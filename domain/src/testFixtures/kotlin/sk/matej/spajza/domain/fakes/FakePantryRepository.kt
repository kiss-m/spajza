package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryChange
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.repository.PantryRepository
import java.time.LocalDate

class FakePantryRepository(initial: List<PantryItem> = emptyList()) : PantryRepository {
    val items = MutableStateFlow(initial.associateBy { it.id })

    override fun observe(householdId: String): Flow<List<PantryItem>> = items.map { it.values.toList() }

    override suspend fun add(householdId: String, ingredient: IngredientRef, quantity: Double?, expiresAt: LocalDate?, addedBy: String) {
        val existing = items.value[ingredient.docId]
        val merged = existing?.copy(
            quantity = if (existing.quantity != null && quantity != null) existing.quantity + quantity else existing.quantity ?: quantity,
            expiresAt = listOfNotNull(existing.expiresAt, expiresAt).minOrNull(),
        ) ?: PantryItem(ingredient, quantity, expiresAt, addedBy)
        items.value = items.value + (merged.id to merged)
    }

    override suspend fun remove(householdId: String, itemId: String) {
        items.value = items.value - itemId
    }

    override suspend fun setQuantity(householdId: String, itemId: String, current: Double?, new: Double?) {
        val item = items.value[itemId] ?: return
        val quantity = new?.let { (item.quantity ?: 0.0) + (it - (current ?: 0.0)) }
        items.value = items.value + (itemId to item.copy(quantity = quantity))
    }

    override suspend fun setExpiry(householdId: String, itemId: String, expiresAt: LocalDate?) {
        val item = items.value[itemId] ?: return
        items.value = items.value + (itemId to item.copy(expiresAt = expiresAt))
    }

    override suspend fun apply(householdId: String, changes: List<PantryChange>) {
        var map = items.value
        changes.forEach { change ->
            map = when (change) {
                is PantryChange.Decrease -> map.adjust(change.itemId, -change.amount)
                is PantryChange.Increase -> map.adjust(change.itemId, change.amount)
                is PantryChange.Remove -> map - change.item.id
                is PantryChange.Restore -> map + (change.item.id to change.item)
            }
        }
        items.value = map
    }

    private fun Map<String, PantryItem>.adjust(id: String, delta: Double): Map<String, PantryItem> {
        val item = this[id] ?: return this
        return this + (id to item.copy(quantity = (item.quantity ?: 0.0) + delta))
    }
}
