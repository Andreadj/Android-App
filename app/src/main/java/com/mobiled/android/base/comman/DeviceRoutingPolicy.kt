package com.mobiled.android.base.comman

/** Protocol rules shared by discovery routing and virtual strip selection. */
object DeviceRoutingPolicy {
    const val DISCOVERY_OFFLINE_AFTER_MS = 5_000L

    fun isDiscoveryRecent(now: Long, lastSeen: Long): Boolean =
        now - lastSeen in 0..DISCOVERY_OFFLINE_AFTER_MS

    /** Capture membership once; later Discovery only changes delivery routes. */
    fun <T, R> captureOnlineMembers(
        members: List<T>,
        isOnline: (T) -> Boolean,
        capture: (T) -> R
    ): List<R> = members.filter(isOnline).map(capture)

    fun isUsableIpv4(address: String): Boolean {
        val components = address.split('.')
        if (components.size != 4 || components.any { part ->
                part.isEmpty() || part.any { it !in '0'..'9' } ||
                    part.toIntOrNull()?.let { it !in 0..255 } != false
            }) return false
        val first = components.first().toInt()
        return first in 1..223 && address != "255.255.255.255"
    }

    fun <T> consecutiveMembers(members: List<T>, pixelId: (T) -> Int): List<T> {
        val byId = members.filter { pixelId(it) in 0..1023 }.groupBy(pixelId)
        val result = ArrayList<T>()
        for (id in 0..1023) {
            val bucket = byId[id].orEmpty()
            if (bucket.isEmpty()) break
            result.addAll(bucket)
        }
        return result
    }
}
