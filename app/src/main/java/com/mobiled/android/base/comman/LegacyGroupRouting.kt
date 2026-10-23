package com.mobiled.android.base.comman

/** Legacy local playback does not require the pixel strip used by synchronized effects. */
object LegacyGroupRouting {
    data class Member(val identity: String, val pixelId: Int, val role: String)

    enum class Failure(val message: String) {
        MULTIPLE_MASTERS("Legacy requires exactly one configured Master. Review the group configuration."),
        MASTER_NOT_AT_ZERO("The configured Legacy Master must have PixelID 0."),
        MASTER_NOT_SELECTED("The configured Legacy Master was not online when this group was selected. Select the group again when the Master is online."),
        MASTER_UNREACHABLE("The configured Legacy Master is currently unreachable."),
        NO_REACHABLE_TARGETS("No MobileD in the target is currently reachable.")
    }

    data class Plan(
        val masterIdentity: String?,
        val participants: List<String> = emptyList(),
        val failure: Failure? = null
    ) {
        val isSynchronized: Boolean get() = masterIdentity != null
    }

    fun plan(configured: List<Member>, selected: List<Member>, reachable: Set<String>): Plan {
        val masters = configured.filter { it.role.equals("M", ignoreCase = true) }
        if (masters.isEmpty()) {
            val participants = selected.filter { it.identity in reachable }.map { it.identity }.distinct()
            return Plan(null, participants,
                if (participants.isEmpty()) Failure.NO_REACHABLE_TARGETS else null)
        }
        val master = masters.first()
        if (masters.size != 1) return Plan(master.identity, failure = Failure.MULTIPLE_MASTERS)
        if (master.pixelId != 0) return Plan(master.identity, failure = Failure.MASTER_NOT_AT_ZERO)
        val selectedMaster = selected.firstOrNull { it.identity == master.identity }
            ?: return Plan(master.identity, failure = Failure.MASTER_NOT_SELECTED)
        if (selectedMaster.pixelId != 0) return Plan(master.identity, failure = Failure.MASTER_NOT_AT_ZERO)
        if (master.identity !in reachable) return Plan(master.identity, failure = Failure.MASTER_UNREACHABLE)

        // Freeze the prefix before applying live reachability, so a temporary
        // offline member never moves or truncates the remaining selected slots.
        val participants = DeviceRoutingPolicy.consecutiveMembers(selected) { it.pixelId }
            .filter { it.identity in reachable }.map { it.identity }.distinct()
        return Plan(master.identity, participants)
    }
}
