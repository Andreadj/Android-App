package com.mobiled.android

import com.mobiled.android.base.comman.DeviceRoutingPolicy
import com.mobiled.android.base.comman.DiscoveryCommandRoutes
import org.junit.Assert.*
import org.junit.Test

class GroupSelectionLifecycleTest {
    private data class Member(val identity: String, var pixelId: Int)

    @Test fun discoveryStaysOnlineAtFiveSecondsAndReturnsOnFirstPacket() {
        var time = 0L
        val routes = DiscoveryCommandRoutes({ time })
        assertTrue(routes.accept("A", "10.0.0.1", "first"))
        time = 5_000L
        assertEquals("10.0.0.1", routes.currentAddress("A"))
        assertTrue(DeviceRoutingPolicy.isDiscoveryRecent(time, 0))
        time = 5_001L
        assertNull(routes.currentAddress("A"))
        assertFalse(DeviceRoutingPolicy.isDiscoveryRecent(time, 0))
        assertTrue(routes.accept("A", "10.0.0.2", "returned"))
        assertEquals("10.0.0.2", routes.currentAddress("A"))
    }

    @Test fun selectionFreezesMembershipIdsAndDuplicatesAcrossDiscoveryChanges() {
        var time = 0L
        val routes = DiscoveryCommandRoutes({ time })
        val members = listOf(Member("A", 0), Member("B", 1), Member("C", 1),
            Member("D", 1), Member("E", 2), Member("F", 4), Member("G", 3))
        listOf("A", "B", "C", "E", "F").forEachIndexed { index, name ->
            routes.accept(name, "10.0.0.${index + 1}", name)
        }
        val selected = DeviceRoutingPolicy.captureOnlineMembers(members,
            { routes.currentAddress(it.identity) != null }, { it.copy() })
        val strip = DeviceRoutingPolicy.consecutiveMembers(selected) { it.pixelId }
        assertEquals(listOf("A", "B", "C", "E", "F"), selected.map { it.identity })
        assertEquals(listOf("A", "B", "C", "E"), strip.map { it.identity })

        // Initial offline duplicate D and gap-filling G must not join. Losing
        // B/C must not truncate position 2. Editing a stored ID must not move it.
        time = 5_001L
        routes.accept("A", "10.0.0.10", "A")
        routes.accept("D", "10.0.0.11", "D")
        routes.accept("E", "10.0.0.12", "E")
        routes.accept("F", "10.0.0.13", "F")
        routes.accept("G", "10.0.0.14", "G")
        members[4].pixelId = 99
        val unchangedStrip = DeviceRoutingPolicy.consecutiveMembers(selected) { it.pixelId }
        assertEquals(listOf(0, 1, 1, 2), unchangedStrip.map { it.pixelId })
        assertEquals(listOf("A", "E"), unchangedStrip
            .filter { routes.currentAddress(it.identity) != null }.map { it.identity })
        assertEquals("10.0.0.10", routes.currentAddress(selected.first().identity))

        routes.accept("B", "10.0.0.20", "B returned")
        assertEquals(listOf("A", "B", "E"), unchangedStrip
            .filter { routes.currentAddress(it.identity) != null }.map { it.identity })
        assertEquals(3, unchangedStrip.map { it.pixelId }.distinct().size)
    }

    @Test fun musicUsesValidPrefixWithoutRequiringTheWholeGroupToBeConsecutive() {
        val group = listOf(7, 2, 0, 1, 2, 8)
        val music = DeviceRoutingPolicy.consecutiveMembers(group) { it }
        assertEquals(listOf(0, 1, 2, 2), music)
        assertEquals(3, music.distinct().size)
        assertTrue(DeviceRoutingPolicy.consecutiveMembers(listOf(2, 3, 3, 4)) { it }.isEmpty())
    }
}
