package com.mobiled.android.base.comman

import java.util.concurrent.ConcurrentHashMap

/** Stable Discovery identity with live IP lookup, including across reconnects. */
class DiscoveryCommandRoutes(
    private val now: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = DeviceRoutingPolicy.DISCOVERY_OFFLINE_AFTER_MS
) {
    private data class Endpoint(val address: String, val frame: String, val lastSeen: Long)
    private val endpoints = ConcurrentHashMap<String, Endpoint>()
    private val addressOwners = ConcurrentHashMap<String, String>()

    fun accept(identity: String, address: String, frame: String): Boolean {
        val key = identity.trim()
        val ip = address.trim()
        if (key.isEmpty() || !DeviceRoutingPolicy.isUsableIpv4(ip)) return false
        endpoints[key] = Endpoint(ip, frame, now())
        addressOwners[ip] = key
        return true
    }

    fun identityForAddress(address: String): String? = addressOwners[address.trim()]

    fun currentAddress(identity: String): String? = endpoints[identity.trim()]?.let {
        if (now() - it.lastSeen in 0..timeoutMillis) it.address else null
    }

    // Retain the last valid frame while offline; it is not proof of reachability.
    fun lastFrame(identity: String): String? = endpoints[identity.trim()]?.frame
}
