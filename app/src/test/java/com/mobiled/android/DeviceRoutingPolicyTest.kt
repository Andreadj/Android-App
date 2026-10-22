package com.mobiled.android

import com.mobiled.android.base.comman.DeviceRoutingPolicy
import org.junit.Assert.*
import org.junit.Test

class DeviceRoutingPolicyTest {
    @Test fun discoveryAcceptsOnlyUsableUnicastIpv4() {
        listOf("192.168.1.42", "10.0.0.2", "172.16.0.1").forEach {
            assertTrue(it, DeviceRoutingPolicy.isUsableIpv4(it))
        }
        listOf("", "0.0.0.0", "255.255.255.255", "239.255.125.0", "192.168.1.999", "host", "1.2.3", "1.2.3.-1").forEach {
            assertFalse(it, DeviceRoutingPolicy.isUsableIpv4(it))
        }
    }

    @Test fun stripKeepsDuplicateRecipientsAndStopsAtFirstMissingSlot() {
        val members = listOf(4, 2, 0, 1, 2, 7, -1, 1024)
        assertEquals(listOf(0, 1, 2, 2), DeviceRoutingPolicy.consecutiveMembers(members) { it })
        assertTrue(DeviceRoutingPolicy.consecutiveMembers(listOf(1, 2, 3)) { it }.isEmpty())
    }

    @Test fun stripIncludesPixel1023WithoutTruncationAt255() {
        val members = (0..1023).toList() + listOf(256, 1023)
        val strip = DeviceRoutingPolicy.consecutiveMembers(members) { it }
        assertEquals(1026, strip.size)
        assertEquals(2, strip.count { it == 256 })
        assertEquals(2, strip.count { it == 1023 })
    }
}
