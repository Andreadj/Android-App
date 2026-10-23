package com.mobiled.android

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.ui.controller.ControllerActivity
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

@RunWith(AndroidJUnit4::class)
class LegacyGroupCommandTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var controller: ControllerActivity? = null
    private val sockets = mutableListOf<DatagramSocket>()

    @After
    fun finishController() {
        instrumentation.runOnMainSync { controller?.finish() }
        instrumentation.waitForIdleSync()
        sockets.forEach { it.close() }
    }

    @Test
    fun localLegacySelectionAndParameterChangesReachEveryMemberWithoutPixelZero() {
        // Orphan Slaves and invalid/missing IDs must not turn local Legacy into
        // a distributed strip or leave online members without a command.
        val members = listOf(member("localA", 2, "X", -1),
            member("localB", 3, "S", 7), member("localC", 4, "S", 7))
        val receivers = members.map { receiver(it) }
        val activity = launchGroup(members)
        instrumentation.runOnMainSync { activity.selectLegacyEffect(1) }
        receivers.forEach { socket ->
            val command = receive(socket) { it.optInt("GLights") == 1 }
            assertEquals("X", command.getString("GState"))
            assertFalse(command.has("GUniverse"))
        }
        instrumentation.runOnMainSync { activity.onCommandChanged(Speed = 37) }
        receivers.forEach { socket ->
            val command = receive(socket) { it.optInt("Speed", -1) == 37 }
            assertEquals(1, command.getInt("GLights"))
            assertEquals("X", command.getString("GState"))
        }
    }

    @Test
    fun reselectingTheSameLegacyEffectClearsAResidualSlaveRole() {
        val item = member("residualSlave", 2, "X", 4)
        item.hardwareDevice!!.applyDiscoveryFrame(JSONObject(item.hardwareDevice!!.deviceFrame)
            .put("GLights", 1).put("GState", "S").toString())
        val socket = receiver(item)
        val activity = launchGroup(listOf(item))
        instrumentation.runOnMainSync { activity.selectLegacyEffect(1) }
        val command = receive(socket) { it.optInt("GLights") == 1 }
        assertEquals("X", command.getString("GState"))
        assertFalse(command.has("GUniverse"))
    }

    @Test
    fun synchronizedLegacyConfiguresDuplicatePrefixMembersAndEditsOnlyTheMaster() {
        val members = listOf(member("syncA", 2, "M", 0), member("syncB", 3, "S", 1),
            member("syncC", 4, "X", 1), member("syncD", 5, "S", 3))
        val receivers = members.map { receiver(it) }
        val activity = launchGroup(members)
        instrumentation.runOnMainSync { activity.selectLegacyEffect(1) }
        receivers.take(3).forEachIndexed { index, socket ->
            val command = receive(socket) { it.optInt("GLights") == 1 }
            assertEquals(if (index == 0) "M" else "S", command.getString("GState"))
            assertEquals(32020, command.getInt("GUniverse"))
        }
        assertNoMatchingCommand(receivers[3]) { it.optInt("GLights") == 1 }
        members.take(3).forEachIndexed { index, item ->
            item.hardwareDevice!!.applyDiscoveryFrame(JSONObject(item.hardwareDevice!!.deviceFrame)
                .put("GLights", 1).put("GState", if (index == 0) "M" else "S")
                .put("GUniverse", 32020).toString())
        }
        publishDiscovery(members)
        instrumentation.runOnMainSync { activity.onCommandChanged(Speed = 37) }
        assertEquals("M", receive(receivers[0]) { it.optInt("Speed", -1) == 37 }.getString("GState"))
        receivers.drop(1).forEach { socket ->
            assertNoMatchingCommand(socket) { it.optInt("Speed", -1) == 37 }
        }
    }

    @Test
    fun firstParameterChangeConfiguresThePrefixBeforeLaterEditsUseOnlyTheMaster() {
        val members = listOf(member("bootstrapA", 2, "M", 0), member("bootstrapB", 3, "S", 1))
        members.forEach { item ->
            item.hardwareDevice!!.applyDiscoveryFrame(JSONObject(item.hardwareDevice!!.deviceFrame)
                .put("GLights", 1).toString())
        }
        val receivers = members.map { receiver(it) }
        val activity = launchGroup(members)
        instrumentation.runOnMainSync { activity.onCommandChanged(Speed = 37) }
        receivers.forEachIndexed { index, socket ->
            val command = receive(socket) { it.optInt("Speed", -1) == 37 }
            assertEquals(if (index == 0) "M" else "S", command.getString("GState"))
            assertEquals(32020, command.getInt("GUniverse"))
        }
        members.forEach { item ->
            item.hardwareDevice!!.applyDiscoveryFrame(JSONObject(item.hardwareDevice!!.deviceFrame)
                .put("GLights", 1).put("Speed", 37)
                .put("GState", item.GState).put("GUniverse", 32020).toString())
        }
        publishDiscovery(members)
        instrumentation.runOnMainSync { activity.onCommandChanged(Speed = 52) }
        assertEquals("M", receive(receivers[0]) { it.optInt("Speed", -1) == 52 }.getString("GState"))
        assertNoMatchingCommand(receivers[1]) { it.optInt("Speed", -1) == 52 }
    }

    private fun receiver(item: HardwareGroupItem): DatagramSocket = DatagramSocket(null).also {
        it.reuseAddress = true
        it.bind(InetSocketAddress(item.hardwareDevice!!.ip, 8889))
        sockets.add(it)
    }

    private fun launchGroup(members: List<HardwareGroupItem>): ControllerActivity {
        publishDiscovery(members)
        val group = HardwareGroup().apply {
            groupTitle = "Legacy routing test"
            GUniverse = 32020
            colorLedStatusSame = true
            groupItems = members
        }
        val intent = Intent(instrumentation.targetContext, ControllerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("group", group)
            .putExtra("groupSelection", ArrayList(members))
        return (instrumentation.startActivitySync(intent) as ControllerActivity).also {
            controller = it
            instrumentation.waitForIdleSync()
        }
    }

    private fun publishDiscovery(members: List<HardwareGroupItem>) {
        val client = UdpClient.getClient(instrumentation.targetContext)
        client.openUdpClient()
        client.startListen()
        DatagramSocket().use { publisher ->
            members.forEach { item ->
                val bytes = item.hardwareDevice!!.deviceFrame.toByteArray(Charsets.UTF_8)
                publisher.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("127.0.0.1"), 8232))
            }
        }
        fun allFramesArrived() = members.all { item ->
            val device = item.hardwareDevice!!
            client.readLatestDiscoveryFrame(device.ApName.orEmpty()) == device.deviceFrame
        }
        val deadline = SystemClock.elapsedRealtime() + 2_000
        while (!allFramesArrived() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
        assertTrue("The fixture must arrive through real UDP Discovery", allFramesArrived())
    }

    private fun receive(socket: DatagramSocket, matches: (JSONObject) -> Boolean): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + 2_000
        while (SystemClock.elapsedRealtime() < deadline) {
            socket.soTimeout = (deadline - SystemClock.elapsedRealtime()).toInt().coerceAtLeast(1)
            val packet = DatagramPacket(ByteArray(4096), 4096)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                break
            }
            val command = JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
            if (matches(command)) return command
        }
        throw AssertionError("No matching Legacy command was received on UDP 8889")
    }

    private fun assertNoMatchingCommand(socket: DatagramSocket, matches: (JSONObject) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 200
        while (SystemClock.elapsedRealtime() < deadline) {
            socket.soTimeout = (deadline - SystemClock.elapsedRealtime()).toInt().coerceAtLeast(1)
            val packet = DatagramPacket(ByteArray(4096), 4096)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                return
            }
            val command = JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
            assertFalse("This member must not receive this Legacy command", matches(command))
        }
    }

    private fun member(suffix: String, address: Int, role: String, id: Int): HardwareGroupItem {
        val device = HardwareDevice().apply {
            ApName = "MobileD_legacy_test_$suffix"
            devName = suffix
            ip = "127.0.0.$address"
            port = 8889
            applyDiscoveryFrame(JSONObject().put("APName", ApName).put("DevName", devName)
                .put("IP", ip).put("Port", 8889).put("Charge", 90)
                .put("Command", 1).put("GLights", 0).put("Speed", 80).put("Brightness", 100)
                .put("red", 255).put("green", 0).put("blue", 0).put("white", 0)
                .put("GState", "X").put("GPort", "10042").toString())
        }
        return HardwareGroupItem().apply {
            hardwareDevice = device
            PixelID = id
            GState = role
            Gport = "10042"
        }
    }
}
