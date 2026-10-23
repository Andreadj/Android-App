package com.mobiled.android

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.mobiled.android.adapters.RcvChildListAdapter
import com.mobiled.android.base.comman.KeyStorage
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.base.model.LightCommand
import com.mobiled.android.ui.controller.ControllerActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ControllerLaunchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as Application
    private val activities = mutableListOf<ControllerActivity>()
    private val activityTracker = object : ActivityCallbacks() {
        override fun onActivityCreated(activity: Activity, state: Bundle?) {
            if (activity is ControllerActivity) activities.add(activity)
        }
    }

    @Before
    fun trackControllers() {
        instrumentation.runOnMainSync {
            application.registerActivityLifecycleCallbacks(activityTracker)
        }
    }

    @After
    fun finishControllers() {
        instrumentation.runOnMainSync {
            activities.toList().forEach { if (!it.isDestroyed) it.finish() }
            application.unregisterActivityLifecycleCallbacks(activityTracker)
            activities.clear()
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun opensColorControllerForSingleApLight() {
        assertColorControllerReady(launchController(apLight()))
    }

    @Test
    fun opensLightWithRememberedCommandContainingMatrixColors() {
        val device = apLight().apply {
            prevLightCommand = LightCommand().apply {
                fromJson(JSONObject(deviceFrame))
                Colors = JSONArray("""[{"r":255,"g":0,"b":0,"w":0},{"r":0,"g":128,"b":255,"w":64}]""")
            }
        }

        // Home passes the whole device as a Serializable Intent extra. Exercise
        // that boundary first so a non-serializable cached field fails here,
        // before Android attempts to start the Activity in another transaction.
        val restored = activityRoundTrip(device)
        assertEquals("Discovery identity must survive Activity transport", device.ApName, restored.ApName)
        assertEquals("Discovery state must survive Activity transport", device.deviceFrame, restored.deviceFrame)
        assertNull("The local command cache must stay out of Activity transport", restored.prevLightCommand)
        assertColorControllerReady(launchController(restored))
    }

    @Test
    fun opensMatrixLightAtFullProtocolBrightness() {
        assertMatrixControllerReady(launchController(matrixLight(101, 255)), 101, 255)
    }

    @Test
    fun opensLastSupportedMatrixLightAtMiddleBrightness() {
        assertMatrixControllerReady(launchController(matrixLight(112, 128)), 112, 128)
    }

    @Test
    fun opensUnknownMatrixEffectAtZeroBrightness() {
        assertMatrixControllerReady(launchController(matrixLight(199, 0)), 199, 0)
    }

    @Test
    fun opensMatrixGroupWithRememberedCommandsContainingColors() {
        val member = matrixLight(101, 255).apply {
            prevLightCommand = LightCommand().apply { fromJson(JSONObject(deviceFrame)) }
        }
        val group = HardwareGroup().apply {
            rowId = 1L
            groupTitle = "AP launch test group"
            colorLedStatusSame = true
            groupItems = arrayListOf(HardwareGroupItem().apply {
                hardwareDevice = member
                PixelID = 0
            })
        }
        val restored = activityRoundTrip(group)
        val restoredMember = restored.groupItems!!.single().hardwareDevice!!
        assertEquals(member.ApName, restoredMember.ApName)
        assertEquals(member.deviceFrame, restoredMember.deviceFrame)
        assertNull("Group navigation must also omit the local command cache", restoredMember.prevLightCommand)
        assertMatrixControllerReady(launchController(restored), 101, 255)
    }

    @Test
    fun clampsRestoredColorPresetBrightnessBeforeDrawing() {
        val storage = KeyStorage.getKeyStorage(instrumentation.targetContext)
        val previous = storage.getStringValue("viewColor1")
        storage.setStringValue("viewColor1", "255,255,0,0,255,255")
        try {
            val activity = launchController(apLight())
            assertColorControllerReady(activity)
            instrumentation.runOnMainSync {
                val preset = activity.window.decorView.findViewWithTag<View>("viewColor1")
                assertTrue("The saved color must be selectable", preset.performClick())
            }
            awaitControllerDraw(activity)
            instrumentation.runOnMainSync {
                assertFalse(activity.isFinishing || activity.isDestroyed)
                assertEquals("100%", activity.findViewById<TextView>(R.id.view_C_B_percent).text.toString())
                assertEquals("100%", activity.findViewById<TextView>(R.id.view_W_B_percent).text.toString())
            }
        } finally {
            storage.setStringValue("viewColor1", previous)
        }
    }

    @Test
    fun homeRowRebindingAndRecyclingKeepsOtherSubscriptionsForTheSameLight() {
        val device = apLight()
        val activity = launchController(device)
        assertColorControllerReady(activity)
        val client = UdpClient.getClient(instrumentation.targetContext)
        fun listeners() = synchronized(client) {
            client.specificDeviceListener[device.ApName.orEmpty()]?.size ?: 0
        }
        val controllerListeners = listeners()
        val group = HardwareGroup().apply {
            colorLedStatusSame = true
            groupItems = arrayListOf(HardwareGroupItem().apply { hardwareDevice = device })
        }
        instrumentation.runOnMainSync {
            val adapter = RcvChildListAdapter(listOf(device, group))
            val host = FrameLayout(activity)
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            content.addView(host)
            val deviceRow = adapter.createViewHolder(host, 0)
            val groupRow = adapter.createViewHolder(host, 0)
            try {
                adapter.bindViewHolder(deviceRow, 0)
                host.addView(deviceRow.itemView)
                adapter.bindViewHolder(groupRow, 1)
                host.addView(groupRow.itemView)
                assertEquals(controllerListeners + 2, listeners())
                adapter.bindViewHolder(deviceRow, 0)
                assertEquals("Rebinding must replace only this row's listener", controllerListeners + 2, listeners())
                adapter.onViewRecycled(deviceRow)
                assertEquals("The group and controller must remain subscribed", controllerListeners + 1, listeners())
                adapter.bindViewHolder(deviceRow, 0)
                assertEquals(controllerListeners + 2, listeners())
                host.removeView(deviceRow.itemView)
                assertEquals("A detached row must release its listener", controllerListeners + 1, listeners())
                host.addView(deviceRow.itemView)
                assertEquals("A reattached row must resume listening once", controllerListeners + 2, listeners())
            } finally {
                adapter.onDetachedFromRecyclerView(RecyclerView(activity))
                content.removeView(host)
            }
            assertEquals("Removing the Home adapter must preserve the controller", controllerListeners, listeners())
        }
    }

    @Test
    fun recreatesColorControllerWithItsSelectedLight() {
        val original = launchController(apLight())
        assertColorControllerReady(original)
        val client = UdpClient.getClient(instrumentation.targetContext)
        val listenerCount = synchronized(client) {
            client.specificDeviceListener[original.device!!.ApName.orEmpty()]?.size ?: 0
        }
        val replacement = AtomicReference<ControllerActivity>()
        val resumed = CountDownLatch(1)
        val callback = object : ActivityCallbacks() {
            override fun onActivityResumed(activity: Activity) {
                if (activity is ControllerActivity && activity !== original) {
                    replacement.set(activity)
                    resumed.countDown()
                }
            }
        }
        instrumentation.runOnMainSync {
            application.registerActivityLifecycleCallbacks(callback)
            original.recreate()
        }
        try {
            assertTrue("The recreated controller must resume", resumed.await(10, TimeUnit.SECONDS))
            val recreated = replacement.get()
            assertNotSame(original, recreated)
            assertColorControllerReady(recreated)
            instrumentation.runOnMainSync {
                assertNotNull("The Color fragment must have been restored",
                    recreated.supportFragmentManager.findFragmentByTag("f0"))
                for (position in 0..3) {
                    val attached = recreated.supportFragmentManager.findFragmentByTag("f$position")
                    if (attached != null) {
                        assertSame("Controller callbacks must target the restored page $position",
                            attached, recreated.viewPagerAdapter.getFragment(position))
                    }
                }
                assertEquals("Recreation must replace its listeners rather than retain the old controller",
                    listenerCount, synchronized(client) {
                        client.specificDeviceListener[recreated.device!!.ApName.orEmpty()]?.size ?: 0
                    })
            }
        } finally {
            instrumentation.runOnMainSync {
                application.unregisterActivityLifecycleCallbacks(callback)
            }
        }
    }

    private fun launchController(device: HardwareDevice): ControllerActivity {
        val intent = Intent(instrumentation.targetContext, ControllerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("device", device)
        return launchController(intent)
    }

    private fun launchController(group: HardwareGroup): ControllerActivity {
        val intent = Intent(instrumentation.targetContext, ControllerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("group", group)
            .putExtra("groupSelection", ArrayList(group.groupItems.orEmpty()))
        return launchController(intent)
    }

    private fun launchController(intent: Intent): ControllerActivity {
        val activity = instrumentation.startActivitySync(intent) as ControllerActivity
        instrumentation.waitForIdleSync()
        return activity
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Serializable> activityRoundTrip(value: T): T {
        val serialized = ByteArrayOutputStream().use { bytes ->
            ObjectOutputStream(bytes).use { it.writeObject(value) }
            bytes.toByteArray()
        }
        assertTrue("The selected light or group must be serializable", serialized.isNotEmpty())
        return ObjectInputStream(ByteArrayInputStream(serialized)).use { it.readObject() as T }
    }

    private fun assertMatrixControllerReady(activity: ControllerActivity, effect: Int, brightness: Int) {
        awaitControllerDraw(activity)
        instrumentation.runOnMainSync {
            assertFalse("The Matrix controller must remain open", activity.isFinishing || activity.isDestroyed)
            assertEquals(effect, activity.getFrame().getInt("GLights"))
            assertEquals("Matrix brightness must retain its 0..255 protocol range", brightness,
                activity.getFrame().getInt("Brightness"))
            assertEquals("The effects page must be selected", 1,
                activity.findViewById<ViewPager2>(R.id.viewPager).currentItem)
            assertEquals("The Color preview must display Matrix brightness as a percentage",
                "${Math.round(brightness * 100f / 255f)}%",
                activity.findViewById<TextView>(R.id.view_C_B_percent).text.toString())
        }
    }

    private fun matrixLight(effect: Int, brightness: Int): HardwareDevice = apLight().apply {
        applyDiscoveryFrame(JSONObject(deviceFrame)
            .put("GLights", effect).put("Brightness", brightness)
            .put("ColorCount", 1).put("Random", 0)
            .put("Custom1", 0).put("Custom2", 0).put("Custom3", 0)
            .put("OnOff1", 1).put("OnOff2", 0)
            .put("Colors", JSONArray("""[{"r":255,"g":0,"b":0,"w":0}]"""))
            .toString())
    }

    private fun awaitControllerDraw(activity: ControllerActivity) {
        val drawn = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val decor = activity.window.decorView
            decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    decor.viewTreeObserver.removeOnPreDrawListener(this)
                    drawn.countDown()
                    return true
                }
            })
            decor.invalidate()
        }
        assertTrue("The controller and its fragments must draw", drawn.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun assertColorControllerReady(activity: ControllerActivity) {
        awaitControllerDraw(activity)
        instrumentation.runOnMainSync {
            assertFalse("The controller must remain open", activity.isFinishing || activity.isDestroyed)
            assertEquals("MobileD_ap_launch_test", activity.device?.ApName)
            assertTrue("The color picker must be visible", activity.findViewById<View>(R.id.colorPickerView).isShown)
            assertEquals("100%", activity.findViewById<TextView>(R.id.view_C_B_percent).text.toString())
            assertEquals("0%", activity.findViewById<TextView>(R.id.view_W_B_percent).text.toString())
        }
    }

    private fun apLight(): HardwareDevice = HardwareDevice().apply {
        rowId = 1L
        ApName = "MobileD_ap_launch_test"
        devName = "AP launch test light"
        ip = "192.168.4.1"
        port = 8889L
        dMXAddress = 1L
        nProt = "SACN"
        val frame = JSONObject()
            .put("APName", ApName).put("DevName", devName).put("IP", ip).put("Port", port)
            .put("DMXAddress", dMXAddress).put("NProt", nProt)
            .put("SubA", 0).put("Univ1", 0).put("Univ2", 1).put("Charge", 90)
            .put("Command", 1).put("GLights", 0).put("Speed", 90).put("Brightness", 100)
            .put("red", 255).put("green", 0).put("blue", 0).put("white", 0)
            .put("GState", "X").put("GPort", "8889").put("GUniverse", 32000)
            .put("PixelID", 0).put("PixelCount", 1)
        applyDiscoveryFrame(frame.toString())
    }

    private open class ActivityCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
