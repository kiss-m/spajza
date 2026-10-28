package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryChange
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.repository.PantryRepository
import java.time.LocalDate

/** Pantries by household id; [initial] goes into [householdId]. */
class FakePantryRepository(initial: List<PantryItem> = emptyList(), householdId: String = "home") : PantryRepository {
    val households = MutableStateFlow(mapOf(householdId to initial.associateBy { it.id }))

    fun itemsOf(householdId: String = "home"): Map<String, PantryItem> = households.value[householdId].orEmpty()

    override fun observe(householdId: String): Flow<List<PantryItem>> = households.map { it[householdId].orEmpty().values.toList() }

    override suspend fun add(householdId: String, ingredient: IngredientRef, quantity: Double?, expiresAt: LocalDate?, addedBy: String) =
        edit(householdId) { items ->
            val existing = items[ingredient.docId]
            val merged = existing?.copy(
                quantity = if (existing.quantity != null && quantity != null) existing.quantity + quantity else existing.quantity ?: quantity,
                expiresAt = listOfNotNull(existing.expiresAt, expiresAt).minOrNull(),
            ) ?: PantryItem(ingredient, quantity, expiresAt, addedBy)
            items + (merged.id to merged)
        }

    override suspend fun remove(householdId: String, itemId: String) = edit(householdId) { it - itemId }

    override suspend fun setQuantity(householdId: String, itemId: String, current: Double?, new: Double?) = edit(householdId) { items ->
        val item = items[itemId] ?: return@edit items
        val quantity = new?.let { (item.quantity ?: 0.0) + (it - (current ?: 0.0)) }
        items + (itemId to item.copy(quantity = quantity))
    }

    override suspend fun setExpiry(householdId: String, itemId: String, expiresAt: LocalDate?) = edit(householdId) { items ->
        val item = items[itemId] ?: return@edit items
        items + (itemId to item.copy(expiresAt = expiresAt))
    }

    override suspend fun apply(householdId: String, changes: List<PantryChange>) = edit(householdId) { start ->
        changes.fold(start) { map, change ->
            when (change) {
                is PantryChange.Decrease -> map.adjust(change.itemId, -change.amount)
                is PantryChange.Increase -> map.adjust(change.itemId, change.amount)
                is PantryChange.Remove -> map - change.item.id
                is PantryChange.Restore -> map + (change.item.id to change.item)
            }
        }
    }

    private fun edit(householdId: String, change: (Map<String, PantryItem>) -> Map<String, PantryItem>) {
        val all = households.value
        households.value = all + (householdId to change(all[householdId].orEmpty()))
    }

    private fun Map<String, PantryItem>.adjust(id: String, delta: Double): Map<String, PantryItem> {
        val item = this[id] ?: return this
        return this + (id to item.copy(quantity = (item.quantity ?: 0.0) + delta))
    }
}
