package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.repository.ShoppingRepository

class FakeShoppingRepository(initial: List<ShoppingItem> = emptyList()) : ShoppingRepository {
    val items = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = 1

    override fun observe(householdId: String): Flow<List<ShoppingItem>> = items.map { it.values.toList() }

    override suspend fun addIngredient(
        householdId: String,
        ingredient: IngredientRef,
        amount: Double?,
        reasonRecipeIds: Set<String>,
        addedBy: String,
    ) {
        val existing = items.value[ingredient.docId]
        val merged = existing?.copy(
            amount = if (amount != null) (existing.amount ?: 0.0) + amount else existing.amount,
            checked = false,
            reasonRecipeIds = existing.reasonRecipeIds + reasonRecipeIds,
        ) ?: ShoppingItem(ingredient.docId, ingredient, amount = amount, reasonRecipeIds = reasonRecipeIds, addedBy = addedBy)
        items.value = items.value + (merged.id to merged)
    }

    override suspend fun addText(householdId: String, text: String, addedBy: String) {
        val id = "text-${nextId++}"
        items.value = items.value + (id to ShoppingItem(id, ingredient = null, text = text, addedBy = addedBy))
    }

    override suspend fun setChecked(householdId: String, itemId: String, checked: Boolean) {
        val item = items.value[itemId] ?: return
        items.value = items.value + (itemId to item.copy(checked = checked))
    }

    override suspend fun remove(householdId: String, itemIds: Collection<String>) {
        items.value = items.value - itemIds.toSet()
    }
}
