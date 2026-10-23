package com.mobiled.android

import com.mobiled.android.base.comman.DeviceRoutingPolicy
import com.mobiled.android.base.comman.LegacyGroupRouting
import com.mobiled.android.base.comman.LegacyGroupRouting.Member
import org.junit.Assert.*
import org.junit.Test

class LegacyGroupRoutingTest {
    private fun plan(configured: List<Member>, selected: List<Member> = configured,
                     reachable: Set<String> = selected.map { it.identity }.toSet()) =
        LegacyGroupRouting.plan(configured, selected, reachable)

    @Test fun localLegacyIncludesEverySelectedMemberRegardlessOfMissingOrInvalidIds() {
        val members = listOf(Member("A", 2, "X"), Member("B", 7, "X"),
            Member("C", -1, "X"), Member("D", 2048, "X"))
        val result = plan(members)
        assertFalse(result.isSynchronized)
        assertNull(result.failure)
        assertEquals(listOf("A", "B", "C", "D"), result.participants)
    }

    @Test fun orphanSlavesWithoutConfiguredMasterPlayLocallyIncludingDuplicateIds() {
        val members = listOf(Member("A", 0, "S"), Member("B", 0, "S"), Member("C", 5, "S"))
        val result = plan(members)
        assertFalse(result.isSynchronized)
        assertNull(result.failure)
        assertEquals(listOf("A", "B", "C"), result.participants)
    }

    @Test fun localLegacyKeepsTheCapturedMembershipAndUsesOnlyLiveRoutes() {
        val configured = listOf(Member("A", 1, "X"), Member("B", 3, "X"), Member("C", 0, "X"))
        val result = plan(configured, configured.take(2), setOf("A", "C"))
        assertEquals(listOf("A"), result.participants)
        assertNull(result.failure)
    }

    @Test fun synchronizedLegacyKeepsDuplicatesAndStopsAtTheFirstGap() {
        val members = listOf(Member("A", 0, "M"), Member("B", 1, "S"),
            Member("C", 1, "X"), Member("D", 3, "S"), Member("E", -1, "S"))
        val result = plan(members)
        assertTrue(result.isSynchronized)
        assertEquals("A", result.masterIdentity)
        assertEquals(listOf("A", "B", "C"), result.participants)
        assertNull(result.failure)
    }

    @Test fun losingAnIntermediateRouteDoesNotTruncateTheFrozenPrefix() {
        val members = listOf(Member("A", 0, "M"), Member("B", 1, "S"), Member("C", 2, "S"))
        assertEquals(listOf("A", "C"), plan(members, reachable = setOf("A", "C")).participants)
    }

    @Test fun configuredMasterMissingFromSelectionNeverFallsBackToLocal() {
        val members = listOf(Member("A", 0, "M"), Member("B", 1, "S"))
        val result = plan(members, members.drop(1))
        assertTrue(result.isSynchronized)
        assertEquals(LegacyGroupRouting.Failure.MASTER_NOT_SELECTED, result.failure)
        assertTrue(result.participants.isEmpty())
    }

    @Test fun masterGoingOfflineAfterSelectionPreventsUnsynchronizedCommands() {
        val members = listOf(Member("A", 0, "M"), Member("B", 1, "S"))
        val result = plan(members, reachable = setOf("B"))
        assertTrue(result.isSynchronized)
        assertEquals(LegacyGroupRouting.Failure.MASTER_UNREACHABLE, result.failure)
        assertTrue(result.participants.isEmpty())
    }

    @Test fun misplacedOrMultipleMastersReportConfigurationErrors() {
        val misplaced = listOf(Member("A", 2, "M"), Member("B", 0, "S"))
        assertEquals(LegacyGroupRouting.Failure.MASTER_NOT_AT_ZERO, plan(misplaced).failure)
        val multiple = listOf(Member("A", 0, "M"), Member("B", 1, "m"))
        assertEquals(LegacyGroupRouting.Failure.MULTIPLE_MASTERS, plan(multiple).failure)
    }

    @Test fun emptyLocalTargetReportsReachabilityRatherThanAConfigurationFailure() {
        val members = listOf(Member("A", -1, "X"))
        assertEquals(LegacyGroupRouting.Failure.NO_REACHABLE_TARGETS,
            plan(members, reachable = emptySet()).failure)
    }

    @Test fun localLegacyDoesNotChangeTheDistributedStripSelection() {
        val members = listOf(Member("A", 0, "X"), Member("B", 1, "X"), Member("C", 3, "X"))
        assertEquals(listOf("A", "B", "C"), plan(members).participants)
        assertEquals(listOf("A", "B"),
            DeviceRoutingPolicy.consecutiveMembers(members) { it.pixelId }.map { it.identity })
    }
}
