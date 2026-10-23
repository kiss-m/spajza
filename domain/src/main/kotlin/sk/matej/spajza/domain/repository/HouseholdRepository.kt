package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.model.Equipment

/**
 * Households shared by their members. Staples and equipment change one entry at a time, so two
 * members editing the lists at once both keep their changes.
 */
interface HouseholdRepository {
    /** Emits null when the household does not exist or the user is no longer a member. */
    fun observe(householdId: String): Flow<Household?>

    /** Creates a household with [ownerUid] as the only member and returns its id. */
    suspend fun create(
        name: String,
        ownerUid: String,
        ownerName: String?,
        staples: Set<String>,
        equipment: Set<Equipment>,
    ): String

    suspend fun setStaple(householdId: String, ingredientId: String, isStaple: Boolean)

    suspend fun setEquipment(householdId: String, equipment: Equipment, available: Boolean)
}
