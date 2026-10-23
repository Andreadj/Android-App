package com.mobiled.android

import com.mobiled.android.base.comman.DiscoveryCommandRoutes
import org.junit.Assert.*
import org.junit.Test

class DiscoveryCommandRoutesTest {
    @Test fun retryResolvesTheNewIpOfTheSameDevice() {
        var time = 1000L
        val routes = DiscoveryCommandRoutes({ time })
        routes.accept("MobileD_A", "192.168.1.10", "first")
        val selectedIdentity = routes.identityForAddress("192.168.1.10")!!
        time += 50
        routes.accept("MobileD_A", "192.168.1.42", "new")
        assertEquals("192.168.1.42", routes.currentAddress(selectedIdentity))
        assertEquals("MobileD_A", routes.identityForAddress("192.168.1.10"))
        assertEquals("new", routes.lastFrame(selectedIdentity))
    }

    @Test fun offlineDisablesDeliveryAndReconnectRestoresTheExistingIdentity() {
        var time = 0L
        val routes = DiscoveryCommandRoutes({ time })
        routes.accept("MobileD_A", "10.0.0.3", "configured")
        time = 5001
        assertNull(routes.currentAddress("MobileD_A"))
        assertEquals("configured", routes.lastFrame("MobileD_A"))
        routes.accept("MobileD_A", "10.0.0.9", "returned")
        assertEquals("10.0.0.9", routes.currentAddress("MobileD_A"))
    }

    @Test fun invalidDiscoveryCannotReplaceAValidRouteOrProlongItsLifetime() {
        var time = 0L
        val routes = DiscoveryCommandRoutes({ time })
        assertTrue(routes.accept("MobileD_A", "10.0.0.3", "valid"))
        time = 3000
        assertFalse(routes.accept("MobileD_A", "239.255.125.0", "invalid"))
        assertFalse(routes.accept(" ", "10.0.0.4", "invalid"))
        assertEquals("10.0.0.3", routes.currentAddress("MobileD_A"))
        time = 5001
        assertNull(routes.currentAddress("MobileD_A"))
        assertEquals("valid", routes.lastFrame("MobileD_A"))
    }
}
