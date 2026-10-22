package sk.matej.spajza.domain.household

import sk.matej.spajza.domain.model.Equipment
import java.time.Instant

data class Household(
    val id: String,
    val name: String,
    val memberUids: List<String>,
    /** Catalog ingredient ids that always count as available. */
    val staples: Set<String>,
    val equipment: Set<Equipment> = DEFAULT_EQUIPMENT,
    val inviteCode: String? = null,
    val inviteExpiresAt: Instant? = null,
) {
    companion object {
        val DEFAULT_EQUIPMENT = setOf(Equipment.STOVE, Equipment.OVEN)
    }
}
