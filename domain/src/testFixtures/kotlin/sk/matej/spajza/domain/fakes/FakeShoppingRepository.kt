package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.repository.ShoppingRepository

/** Shopping lists by household id; [initial] goes into [householdId]. */
class FakeShoppingRepository(initial: List<ShoppingItem> = emptyList(), householdId: String = "home") : ShoppingRepository {
    val households = MutableStateFlow(mapOf(householdId to initial.associateBy { it.id }))
    private var nextId = 1

    fun itemsOf(householdId: String = "home"): Map<String, ShoppingItem> = households.value[householdId].orEmpty()

    override fun observe(householdId: String): Flow<List<ShoppingItem>> = households.map { it[householdId].orEmpty().values.toList() }

    override suspend fun addIngredient(
        householdId: String,
        ingredient: IngredientRef,
        amount: Double?,
        reasonRecipeIds: Set<String>,
        addedBy: String,
    ) = edit(householdId) { items ->
        val existing = items[ingredient.docId]
        val merged = existing?.copy(
            amount = if (amount != null) (existing.amount ?: 0.0) + amount else existing.amount,
            checked = false,
            reasonRecipeIds = existing.reasonRecipeIds + reasonRecipeIds,
        ) ?: ShoppingItem(ingredient.docId, ingredient, amount = amount, reasonRecipeIds = reasonRecipeIds, addedBy = addedBy)
        items + (merged.id to merged)
    }

    override suspend fun addText(householdId: String, text: String, addedBy: String) = edit(householdId) { items ->
        val id = "text-${nextId++}"
        items + (id to ShoppingItem(id, ingredient = null, text = text, addedBy = addedBy))
    }

    override suspend fun setChecked(householdId: String, itemId: String, checked: Boolean) = edit(householdId) { items ->
        val item = items[itemId] ?: return@edit items
        items + (itemId to item.copy(checked = checked))
    }

    override suspend fun remove(householdId: String, itemIds: Collection<String>) = edit(householdId) { it - itemIds.toSet() }

    private fun edit(householdId: String, change: (Map<String, ShoppingItem>) -> Map<String, ShoppingItem>) {
        val all = households.value
        households.value = all + (householdId to change(all[householdId].orEmpty()))
    }
}
