package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.repository.HouseholdRepository

class FakeHouseholdRepository(initial: List<Household> = emptyList()) : HouseholdRepository {
    val households = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = 1

    override fun observe(householdId: String): Flow<Household?> = households.map { it[householdId] }

    override suspend fun create(
        name: String,
        ownerUid: String,
        ownerName: String?,
        staples: Set<String>,
        equipment: Set<Equipment>,
    ): String {
        val id = "household-${nextId++}"
        val household = Household(
            id = id,
            name = name,
            memberUids = listOf(ownerUid),
            memberNames = ownerName?.let { mapOf(ownerUid to it) }.orEmpty(),
            staples = staples,
            equipment = equipment,
        )
        households.value = households.value + (id to household)
        return id
    }

    override suspend fun setStaple(householdId: String, ingredientId: String, isStaple: Boolean) = update(householdId) {
        it.copy(staples = if (isStaple) it.staples + ingredientId else it.staples - ingredientId)
    }

    override suspend fun setEquipment(householdId: String, equipment: Equipment, available: Boolean) = update(householdId) {
        it.copy(equipment = if (available) it.equipment + equipment else it.equipment - equipment)
    }

    private fun update(id: String, change: (Household) -> Household) {
        val household = households.value[id] ?: return
        households.value = households.value + (id to change(household))
    }
}
