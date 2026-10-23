package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.ShoppingItem

/**
 * The shared shopping list. An ingredient has at most one item: adding it again merges the amount
 * (by increment) and the reasons into that item and unchecks it.
 */
interface ShoppingRepository {
    fun observe(householdId: String): Flow<List<ShoppingItem>>

    /** [amount] is in the ingredient's pantry unit; null leaves the amount as it is. */
    suspend fun addIngredient(
        householdId: String,
        ingredient: IngredientRef,
        amount: Double?,
        reasonRecipeIds: Set<String>,
        addedBy: String,
    )

    suspend fun addText(householdId: String, text: String, addedBy: String)

    suspend fun setChecked(householdId: String, itemId: String, checked: Boolean)

    suspend fun remove(householdId: String, itemIds: Collection<String>)
}
