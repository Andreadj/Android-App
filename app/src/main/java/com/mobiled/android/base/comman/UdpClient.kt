package com.mobiled.android.base.comman

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.os.AsyncTask
import com.mobiled.android.LogSystem
import com.mobiled.android.base.AppConfiguration
import com.mobiled.android.base.common.DeviceStateWatcher
import kotlinx.coroutines.Runnable
import org.json.JSONObject
import java.io.IOException
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.random.Random


class UdpClient(private var context: Context) {
    private val TAG = UdpClient::class.java.simpleName

    private var wifiManager: WifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager


    private var ipAddress: InetAddress? = null

    private var sendExecutor = Executors.newSingleThreadExecutor()
    private val commandTrailingExecutor = ScheduledThreadPoolExecutor(1)
    private val commandTrailing = ConcurrentHashMap<String, CommandTrailingState>()

    // Discovery is the authoritative device-state stream. Keep a small
    // per-APName last-seen cache so online/offline follows the same passive
    // Discovery semantics as the PC App instead of probing the device with
    // ICMP/ping.
    private val discoveryLastSeen = ConcurrentHashMap<String, Long>()
    private val discoveryOnline = ConcurrentHashMap<String, Boolean>()
    private val discoveryRoutes = DiscoveryCommandRoutes()
    private val discoveryStateListeners =
        ConcurrentHashMap<String, ArrayList<DiscoveryStateListener>>()
    private val discoveryWatchExecutor = ScheduledThreadPoolExecutor(1)

    private data class CommandTrailingState(
        val generation: Long,
        var first: ScheduledFuture<*>? = null,
        var second: ScheduledFuture<*>? = null,
        var third: ScheduledFuture<*>? = null
    )
    private var watcherThread: DeviceStateWatcher? = null
    //private var receiveExecutor = Executors.newSingleThreadExecutor()

    //{"APName" : "MobileD_c07be9ae114c","DevName" : "MobileD","IP": "192.168.0.143","Port": 8889,"DMXAddress":1,"NProt": "SACN","SubA": 0,"Univ1":0,"Univ2":1,"Charge":99,"Command": 0,"GLights": 0,"Speed":90,"Brightness": 100,"red" : 100,"green":0,"blue" : 0,"white":0}}

    companion object {

        @JvmStatic
        val TIMEOUT: Long = 3000L

        @JvmStatic
        val SYNC_TIMEOUT: Long = 3000L

        @JvmStatic
        var instance: UdpClient? = null
        fun getClient(context: Context): UdpClient {
            if (instance == null) {
                instance = UdpClient(context)
            }
            return instance!!
        }
    }

    private var udpSocket: DatagramSocket? = null
    fun openUdpClient(): Boolean {
        if (udpSocket != null) {
            logMessage(TAG, "openUdpClient() Socket is Already Created")
            return true
        }
        logMessage(TAG, "openUdpClient() called")
        // Listen on all local interfaces so Discovery broadcast packets
        // addressed to the Wi-Fi subnet can be received.
        ipAddress = InetAddress.getByName("0.0.0.0")
        openUdpClient(ipAddress!!, AppConfiguration.UDP_LOCAL_PORT, bind = true)

        return false
    }

    fun openUdpClient(ipAddress: InetAddress, port: Int, bind: Boolean = false) {
        logMessage(
            TAG,
            "openUdpClient() called with: ipAddress = $ipAddress, port = $port, bind = $bind"
        )
        closeSocket()
        try {
            udpSocket = if (bind) {
                DatagramSocket(null)
            } else {
                DatagramSocket(port, ipAddress)
            }
            udpSocket?.broadcast = true
            udpSocket?.reuseAddress = true
            if (bind) udpSocket?.bind(InetSocketAddress(port))
            logMessage(
                "TAG",
                "UDP Open : " + ipAddress?.toString() + " Port : ${udpSocket?.localPort}"
            )
            startSimulation()
        } catch (e: BindException) {
            if (!bind) openUdpClient(ipAddress, port, true)
            else e.printStackTrace()
        } catch (e: SocketException) {
            e.printStackTrace()
        }
    }

    var simulationDevicePayloads = hashMapOf<String, JSONObject>()
    private fun startSimulation() {
        if (!AppConfiguration.UDP_SIMULATE_DEVICES) return
        var apNameList = ArrayList<String>()
        var ipAddressList = ArrayList<String>()
        var devNameList = ArrayList<String>()
        var devNamesPre = arrayOf(
            "TECHSYNC-SIM-",
            "QUANTUMGLOW-SIM-",
            "VORTEXFIRE-SIM-",
            "ULTRASPARK-SIM-",
            "RAPIDBLAZE-SIM-"
        )
        for (i in 0 until 100) {
            apNameList.add("MobileD_ac000${String.format("%03d", i)}")
            ipAddressList.add("127.0.0.${String.format("%03d", i)}")
            devNameList.add("${devNamesPre.random()}${String.format("%03d", i)}")

        }
        var apNames = apNameList.toArray(emptyArray<String>())
        var ipAddresses = ipAddressList.toArray(emptyArray<String>())
        var devNames = devNameList.toArray(emptyArray<String>())

        var asyncTask = @SuppressLint("StaticFieldLeak")
        object : AsyncTask<Void, Void?, Void>() {

            override fun doInBackground(vararg params: Void?): Void? {

                var randomColor = Random(1)
                var battery = Random(1)

                apNames.forEachIndexed { index, apName ->
                    var jsonObject =
                        JSONObject("{\"APName\" : \"MobileD_c07be9ae114c\",\"DevName\" : \"MobileD\",\"IP\": \"192.168.0.143\",\"Port\": 8889,\"DMXAddress\":1,\"NProt\": \"SACN\",\"SubA\": 0,\"Univ1\":0,\"Univ2\":1,\"Charge\":99,\"Command\": 0,\"GLights\": 0,\"Speed\":90,\"Brightness\": 100,\"red\" : 100,\"green\":0,\"blue\" : 0,\"white\":0}}")
                    jsonObject.put("APName", apName)
                    jsonObject.put("Charge", battery.nextInt(100))
                    jsonObject.put("red", randomColor.nextInt(255))
                    jsonObject.put("green", randomColor.nextInt(255))
                    jsonObject.put("blue", randomColor.nextInt(255))
                    jsonObject.put("IP", ipAddresses[index])
                    jsonObject.put("DevName", devNames[index])
                    simulationDevicePayloads.put(ipAddresses[index], jsonObject)
                    Thread.sleep(5);

                    //{"Command":0,"GLights":0,"Speed":90,"Brightness":81,"red":0,"green":207,"blue":136,"white":184,"GState":"X","GPort":"8889"}
                }
                while (udpSocket != null) {

                    simulationDevicePayloads.forEach {
                        it.value.put("Charge", battery.nextInt(100))
                        var byteArray = it.value.toString().toByteArray()
                        var datagramPacket = DatagramPacket(byteArray, 0, byteArray.size)
                        Thread.sleep(100);
                        handlePacket(datagramPacket)
                    }
                }

                return null
            }

            override fun onPostExecute(result: Void?) {

            }
        }
        asyncTask.execute()
    }


    fun isWifiConnected(): Boolean {
        val connManager =
            context.getSystemService(Application.CONNECTIVITY_SERVICE) as ConnectivityManager
        var net = connManager.activeNetworkInfo
        return net?.type == ConnectivityManager.TYPE_WIFI
    }

    fun isGpsProviderEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    var specificDeviceListener: HashMap<String, ArrayList<Listener>> = hashMapOf()
    fun startListen(deviceName: String, listener: Listener) {
        val key = deviceName.trim()
        if (key.isEmpty()) return

        // Register the listener before starting the receiver so the first
        // Discovery datagram cannot be lost in the startup window.
        synchronized(this) {
            specificDeviceListener.getOrPut(key) { arrayListOf() }.add(listener)
            if (!listeningStarted) {
                if (!startListen()) {
                    if (udpSocket == null) {
                        openUdpClient()
                        startListen()
                    }
                }
            }
        }
    }

    @Synchronized
    fun stopListen(deviceName: String, listener: Listener) {
        val key = deviceName.trim()
        specificDeviceListener[key]?.let { listeners ->
            listeners.removeAll { it === listener }
            if (listeners.isEmpty()) specificDeviceListener.remove(key)
        }
    }

    /**
     * Returns the current passive Discovery reachability for a MobileD.
     * Group member identification uses this state so Command=2 is sent only
     * when the PC App equivalent would identify an online device with a valid IP.
     */
    fun isDiscoveryOnline(deviceName: String): Boolean {
        val key = deviceName.trim()
        if (key.isEmpty()) return false
        val lastSeen = discoveryLastSeen[key] ?: return false
        return discoveryOnline[key] == true &&
            DeviceRoutingPolicy.isDiscoveryRecent(System.currentTimeMillis(), lastSeen)
    }

    fun currentDiscoveryIp(deviceName: String): String? =
        deviceName.trim().takeIf { isDiscoveryOnline(it) }?.let { discoveryRoutes.currentAddress(it) }

    fun readLatestDiscoveryFrame(deviceName: String): String? = discoveryRoutes.lastFrame(deviceName)

    fun listenDiscoveryState(deviceName: String, listener: DiscoveryStateListener) {
        synchronized(this) {
            discoveryStateListeners.getOrPut(deviceName.trim()) { arrayListOf() }.add(listener)
            val apName = deviceName.trim()
            val lastSeen = discoveryLastSeen[apName]
            if (lastSeen != null && DeviceRoutingPolicy.isDiscoveryRecent(System.currentTimeMillis(), lastSeen)) {
                listener.onDiscoveryStateChanged(true)
            }
        }
    }

    @Synchronized
    fun removeDiscoveryStateListener(deviceName: String, listener: DiscoveryStateListener) {
        val key = deviceName.trim()
        discoveryStateListeners[key]?.let { listeners ->
            listeners.removeAll { it === listener }
            if (listeners.isEmpty()) discoveryStateListeners.remove(key)
        }
    }

    private var listeningStarted = false
    private var lastExTime = -1L

    init {
        discoveryWatchExecutor.scheduleAtFixedRate({
            val now = System.currentTimeMillis()
            discoveryLastSeen.forEach { (apName, lastSeen) ->
                if (now - lastSeen > DeviceRoutingPolicy.DISCOVERY_OFFLINE_AFTER_MS &&
                    discoveryOnline[apName] == true
                ) {
                    discoveryOnline[apName] = false
                    synchronized(this) {
                        discoveryStateListeners[apName]?.toList()?.forEach {
                            try {
                                it.onDiscoveryStateChanged(false)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }
    fun startListen(): Boolean {
        synchronized(this)
        {
            if (listeningStarted) {
                return true
            }
        }
        if (udpSocket != null) {
            listeningStarted = true
            // UDP payloads may be larger than the old 1 KiB cap, especially when a
            // Discovery frame includes a complete Matrix configuration.
            val buffer = ByteArray(65_507)
            val packet = DatagramPacket(buffer, buffer.size)
            logMessage("TAG", "UDP@startListen Invoked")
            Thread(Runnable {
                logMessage("TAG", "UDP@startListen Thread")
                while (udpSocket != null) {
                    if (lastExTime == -1L) {
                        lastExTime = System.currentTimeMillis();
                    }
                    //logMessage("TAG", "UDP@startListen Thread Loop #1")
                    try {
                        Thread.sleep(5)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    logMessage(
                        TAG,
                        "Packet Len : " + packet.length + " Data Size " + packet.data.size + " OffSet : " + packet.getOffset() + " Interval : " + (System.currentTimeMillis() - lastExTime)
                    )
                    lastExTime = System.currentTimeMillis()

                    var pExTime = System.currentTimeMillis()
                    val received = receivePacket(packet)
                    logMessage(
                        TAG,
                        "Packet Receive Time :" + (System.currentTimeMillis() - pExTime)
                    )
                    if (received) handlePacket(packet)
                }
                logMessage("TAG", "UDP@startListen Thread Loop End")
                listeningStarted = false
                udpSocket?.close()
            }).start()
            return true
        } else {
            return false
        }
    }

    private fun handlePacket(receivePacket: DatagramPacket) {
        try {
            val length = receivePacket.length
            if (length <= 0) return
            val bytes = Arrays.copyOfRange(
                receivePacket.data,
                receivePacket.offset,
                receivePacket.offset + length
            )
            //var byteLen = BytesUtil.findSize(bytes)
            if (bytes.isNotEmpty()) {
//                val _tmpBytes = BytesUtil.removeBytes(bytes)
                val _data = String(bytes, Charsets.UTF_8)
                try {
                    var json = JSONObject(_data)
                    if (json.has("APName")) {
                        val apName = json.getString("APName").trim()
                        val currentIp = json.optString("IP", "").trim()
                        if (discoveryRoutes.accept(apName, currentIp, json.toString())) {
                            discoveryLastSeen[apName] = System.currentTimeMillis()
                            val wasOnline = discoveryOnline[apName] == true
                            discoveryOnline[apName] = true
                            if (!wasOnline) {
                                val stateListeners = synchronized(this) {
                                    discoveryStateListeners[apName]?.toList().orEmpty()
                                }
                                stateListeners.forEach {
                                    try {
                                        it.onDiscoveryStateChanged(true)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                            val packetListeners = synchronized(this) {
                                specificDeviceListener[apName]?.toList().orEmpty()
                            }
                            packetListeners.forEach {
                                it.onUdpMessage(bytes)
                            }
                        }
                    }
                } catch (ignore: Exception) {
                }
                logMessage(
                    "UdpClientReceive",
                    "Hardware >> App UDP@ Received String $_data"
                )
                udpListener?.onUdpMessage(bytes)
                //handler?.obtainMessage(0, _data)?.sendToTarget()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun receivePacket(packet: DatagramPacket): Boolean {
        try {
            // DatagramPacket keeps the previous received length. Reset it
            // before every receive or a shorter packet would silently cap the
            // maximum length of the next Discovery packet.
            packet.length = packet.data.size
            udpSocket?.receive(packet)
            logMessage("TAG", "Packet Size : ${packet.data.size}")
            return true
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    fun getConnectedWifiAddress(): String? {
        val wifiMgr = context.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        val wifiInfo = wifiMgr!!.connectionInfo
        val ip = wifiInfo.ipAddress
        val addressAsString = java.lang.String.format(
            Locale.US, "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
        return addressAsString
    }

    fun getConnectedWifiName(): String? {
        val wifiMgr = context.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        val wifiInfo = wifiMgr!!.connectionInfo
        return wifiInfo.ssid
    }

    private fun getBroadcastIpAddress(): InetAddress {
        try {
            val wifiMan = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInf = wifiMan.connectionInfo
            val ipAddress = wifiInf.ipAddress
            val ip = String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )

            val dhcp: DhcpInfo =
                wifiManager.dhcpInfo ?: return InetAddress.getByName("255.255.255.255")
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            val quads = ByteArray(4)
            for (k in 0..3) {
                quads[k] = ((broadcast shr k * 8) and 0xFF).toByte()
            }
            LogSystem.e("TAG", "QUADS : ${InetAddress.getByAddress(quads).toString()}")
            return InetAddress.getByAddress(quads)
        } catch (e: Exception) {
            logMessage("Error", "IP Error ${e.message}")
        }
        return InetAddress.getByName("0.0.0.0")
    }

    private fun logMessage(tag: String, message: String) {
        if (AppConfiguration.SOCKET_LOG) LogSocketManager.getSocketManager().writeLog(message)
        else LogSystem.e(tag, "[$message]")
    }

    fun closeSocket() {
        logMessage(TAG, "closeSocket() called")
        udpSocket?.close()
        udpSocket = null
    }

    @Synchronized
    fun closeWatcher() {
        watcherThread?.interrupt()
        watcherThread = null
    }

    fun with(udpListener: Listener?) {
        this.udpListener = udpListener
    }

    fun isConnected(): Boolean {
        println("IP : ${getConnectedWifiAddress()}")
        return udpSocket?.isConnected == true
    }

    fun writeString(
        value: String,
        ipAddress: String,
        port: Int = 8232,
        writeCallback: WriteCallback = object : WriteCallback {
            override fun onOperationDone(result: Boolean) {
                // Handle operation done
            }
        }
    ) {

        writeBytes(value.toByteArray(), ipAddress, port, writeCallback)
    }

    /**
     * App Command transport.
     *
     * MobileD commands always use UDP 8889 as the network destination.
     * GPort remains a field inside the JSON payload and is never used as the
     * UDP destination port. The transmission policy mirrors the PC App: one
     * immediate datagram, then (only after 50 ms without a newer command for
     * the same device) three final retransmissions spaced by 15 ms.
     */
    fun writeCommandString(
        value: String,
        ipAddress: String,
        writeCallback: WriteCallback = object : WriteCallback {
            override fun onOperationDone(result: Boolean) {}
        }
    ) = writeDeviceCommand(value, ipAddress, writeCallback, reliable = true)

    fun writeRealtimeCommandString(value: String, ipAddress: String) =
        writeDeviceCommand(value, ipAddress, object : WriteCallback {
            override fun onOperationDone(result: Boolean) {}
        }, reliable = false)

    @Synchronized
    private fun writeDeviceCommand(
        value: String,
        ipAddress: String,
        writeCallback: WriteCallback,
        reliable: Boolean
    ) {
        val payloadIdentity = runCatching { JSONObject(value).optString("APName", "").trim() }.getOrDefault("")
        val key = payloadIdentity.takeIf { it.isNotEmpty() }
            ?: discoveryRoutes.identityForAddress(ipAddress)
        if (key == null || currentDiscoveryIp(key) == null) {
            writeCallback.onOperationDone(false)
            return
        }

        val previous = commandTrailing[key]
        previous?.first?.cancel(false)
        previous?.second?.cancel(false)
        previous?.third?.cancel(false)
        val state = CommandTrailingState((previous?.generation ?: 0L) + 1L)
        commandTrailing[key] = state
        val bytes = value.toByteArray(Charsets.UTF_8)

        fun transmit(callback: WriteCallback = object : WriteCallback {
            override fun onOperationDone(result: Boolean) {}
        }) {
            writeBytes(bytes, ipAddress, 8889, callback,
                resolveAddress = { currentDiscoveryIp(key) },
                isCurrent = { commandTrailing[key] === state })
        }

        transmit(writeCallback)
        if (!reliable) return
        state.first = commandTrailingExecutor.schedule({ transmit() }, 50, TimeUnit.MILLISECONDS)
        state.second = commandTrailingExecutor.schedule({ transmit() }, 65, TimeUnit.MILLISECONDS)
        state.third = commandTrailingExecutor.schedule({ transmit() }, 80, TimeUnit.MILLISECONDS)
        // Keep the latest generation: queued sends must still reject superseded commands.
    }

    private fun writeBytes(
        bytes: ByteArray,
        bcAddress: String,
        port: Int = 8889,
        writeCallback: WriteCallback = object : WriteCallback {
            override fun onOperationDone(result: Boolean) {}
        },
        resolveAddress: (() -> String?)? = null,
        isCurrent: (() -> Boolean)? = null
    ) {
        val callableFeature = CompletableFuture.supplyAsync({
            try {
                if (isCurrent?.invoke() == false) return@supplyAsync false
                val destination = if (resolveAddress == null) bcAddress else
                    resolveAddress.invoke() ?: return@supplyAsync false
                if (AppConfiguration.UDP_SIMULATE_DEVICES) {
                    val jsonObject = simulationDevicePayloads[destination]
                    val commandJson = JSONObject(String(bytes, Charsets.UTF_8))
                    commandJson.keys().forEach {
                        if (jsonObject?.has(it) == true) jsonObject.put(it, commandJson.get(it))
                    }
                    jsonObject != null
                } else {
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        // Check again immediately before the physical send, after socket setup.
                        if (isCurrent?.invoke() == false) return@supplyAsync false
                        val currentDestination = if (resolveAddress == null) destination else
                            resolveAddress.invoke() ?: return@supplyAsync false
                        val packet = DatagramPacket(bytes, bytes.size,
                            InetAddress.getByName(currentDestination), port)
                        socket.send(packet)
                    }
                    true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }, sendExecutor)
        callableFeature.thenAccept { writeCallback.onOperationDone(it) }
    }

    private fun isDeviceActive(deviceIp: InetAddress): Boolean {
        try {
            return deviceIp.isReachable(TIMEOUT.toInt())
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }


    @Synchronized
    fun listenConnectionState(ip: String, stateListener: StateListener) {
        if (watcherThread == null) {
            watcherThread = DeviceStateWatcher()
            watcherThread?.start()
        }
        watcherThread?.registerDeviceListener(ip, stateListener)
    }


    var udpListener: Listener? = null

    open interface Listener {
        fun onUdpMessage(bytes: ByteArray)
    }

    open interface StateListener {
        fun onClientConnectionStatusChange(result: Boolean = true)
    }

    open interface DiscoveryStateListener {
        fun onDiscoveryStateChanged(online: Boolean)
    }

    open interface WriteCallback {
        fun onOperationDone(result: Boolean)
        fun apName(): String {
            return "NONE"
        }
    }
}
