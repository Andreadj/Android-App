package com.mobiled.android.base.common

import com.mobiled.android.base.comman.UdpClient
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class DeviceStateWatcher : Thread() {
    private val TIMEOUT = 500
    private val MIN_TIME_PERIOD = 5000L
    private val CHECK_INTERVAL = 500L

    private val specificDeviceState: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()
    private val specificDeviceStateTime: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    // State listeners must stay strongly referenced while the watcher is active.
    // WeakReference listeners can be garbage-collected while a device is still being watched,
    // which prevents the live offline transition from reaching the Home/group UI.
    private val specificDeviceListener: ConcurrentHashMap<String, ArrayList<UdpClient.StateListener>> = ConcurrentHashMap()

    override fun run() {
        try {
            while (!isInterrupted) {
                val deviceIterator = specificDeviceState.keys.iterator()
                while (deviceIterator.hasNext()) {
                    checkAndUpdateDeviceState(deviceIterator.next())
                }
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
        val doNeedCheck = System.currentTimeMillis() - lastCheckedTime > MIN_TIME_PERIOD
        try {
            if (previousState && !doNeedCheck) return
            val isReachable = InetAddress.getByName(ip).isReachable(TIMEOUT)
            specificDeviceState[ip] = isReachable
            specificDeviceStateTime[ip] = System.currentTimeMillis()
            if (isReachable != previousState) postConnectionChanged(ip, isReachable)
        } catch (e: Exception) {
            specificDeviceState[ip] = false
            specificDeviceStateTime[ip] = System.currentTimeMillis()
            if (previousState) postConnectionChanged(ip, false)
        }
    }

    @Synchronized
    private fun postConnectionChanged(ip: String, isConnected: Boolean) {
        specificDeviceListener[ip]?.forEach { it.onClientConnectionStatusChange(isConnected) }
    }

    fun registerDeviceListener(ip: String, stateListener: UdpClient.StateListener) {
        specificDeviceStateTime[ip] = System.currentTimeMillis()
        specificDeviceListener.getOrPut(ip) { arrayListOf() }.add(stateListener)
        specificDeviceState.putIfAbsent(ip, true)
    }
}
