package com.mobiled.android.base.common

import com.mobiled.android.base.comman.UdpClient
import java.lang.ref.WeakReference
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class DeviceStateWatcher : Thread() {
    private val TIMEOUT = 500
    private val MIN_TIME_PERIOD = 5000L
    private val CHECK_INTERVAL = 500L

    var specificDeviceState: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()
    var specificDeviceStateTime: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    var specificDeviceListener: ConcurrentHashMap<String, ArrayList<WeakReference<UdpClient.StateListener>>> = ConcurrentHashMap()

    override fun run() {
        System.err.println("Execution Started!")

        try {
            while (!isInterrupted) {
                val deviceIterator = specificDeviceState.keys.iterator()

                while (deviceIterator.hasNext()) {
                    val ip = deviceIterator.next()
                    checkAndUpdateDeviceState(ip)
                }

                // Sleep between checks
                try {
                    sleep(CHECK_INTERVAL)
                } catch (e: InterruptedException) {
                    interrupt()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndUpdateDeviceState(ip: String) {
        val previousState = specificDeviceState[ip] ?: false
        val lastCheckedTime = specificDeviceStateTime[ip] ?: 0L
        val doNeedCheck = ((System.currentTimeMillis() - lastCheckedTime) > MIN_TIME_PERIOD)
        System.err.println("Checking: $ip Status Needs Check : $doNeedCheck")
        try {
            // Skip if the device is already marked active and within the timeout period
            if (previousState && !doNeedCheck) {
                return
            }

            val isReachable = pingDevice(ip)
            specificDeviceState[ip] = isReachable
            specificDeviceStateTime[ip] = System.currentTimeMillis()

            // Notify listeners if the state has changed
            if (isReachable != previousState) {
                postConnectionChanged(ip, isReachable)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            specificDeviceState[ip] = false
            specificDeviceStateTime[ip] = System.currentTimeMillis()
            if(previousState) postConnectionChanged(ip, false)
        }
    }

    private fun pingDevice(ip: String): Boolean {
        System.err.println("Pinging: $ip")
        return InetAddress.getByName(ip).isReachable(TIMEOUT)
    }

    @Synchronized
    private fun postConnectionChanged(ip: String, isConnected: Boolean) {
        System.err.println("Post Connection Changed: $ip >>> $isConnected")
        specificDeviceListener[ip]?.forEach { weakRef ->
            weakRef.get()?.onClientConnectionStatusChange(isConnected)
        }
    }

    fun registerDeviceListener(ip: String, stateListener: UdpClient.StateListener) {
        specificDeviceStateTime[ip] = System.currentTimeMillis()

        // Initialize the listener list if not present
        specificDeviceListener.getOrPut(ip) { arrayListOf() }.add(WeakReference(stateListener))
        specificDeviceState.putIfAbsent(ip, true)
    }
}
