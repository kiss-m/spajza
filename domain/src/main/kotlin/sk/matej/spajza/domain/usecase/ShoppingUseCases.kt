package sk.matej.spajza.domain.usecase

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.matching.LineStatus
import sk.matej.spajza.domain.matching.RecipeMatch
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.repository.ShoppingRepository
import sk.matej.spajza.domain.unit.UnitConverter
import java.time.LocalDate
import kotlin.math.ceil

/**
 * "Pridať chýbajúce do nákupu": puts the missing ingredients of [RecipeMatch] on the shopping list,
 * plus the shortfall of ingredients the pantry has too little of, in the amounts its servings need.
 */
class AddMissingToShoppingUseCase(
    private val catalog: Catalog,
    private val shopping: ShoppingRepository,
) {
    /** Returns how many ingredients were added or topped up. */
    suspend operator fun invoke(householdId: String, match: RecipeMatch, addedBy: String): Int {
        val factor = match.servings.toDouble() / match.recipe.servings
        val gramsById = linkedMapOf<String, Double>()
        match.lines.filterNot { it.line.optional }.forEach { line ->
            val needed = line.line.grams * factor
            val shortfall = when (line.status) {
                LineStatus.MISSING -> needed
                LineStatus.PARTIAL -> needed * (1 - line.availability)
                else -> return@forEach
            }
            gramsById.merge(line.line.ingredientId, shortfall, Double::plus)
        }
        gramsById.forEach { (id, grams) ->
            val ingredient = catalog.ingredient(id)
            // "To taste" lines have no grams; they go on the list without an amount.
            val amount = if (grams > 0) roundUp(UnitConverter.gramsToPantryUnit(grams, ingredient), ingredient.pantryUnit) else null
            shopping.addIngredient(householdId, IngredientRef.Catalog(id), amount, setOf(match.recipe.id), addedBy)
        }
        return gramsById.size
    }

    /** Whole pieces, whole grams and millilitres; a tiny tolerance keeps 2.0000001 at 2. */
    private fun roundUp(amount: Double, unit: PantryUnit): Double = when (unit) {
        PantryUnit.PCS, PantryUnit.G, PantryUnit.ML -> ceil(amount - ROUNDING_TOLERANCE)
    }

    private companion object {
        const val ROUNDING_TOLERANCE = 1e-6
    }
}

/** A checked shopping item with what the user entered when moving it to the pantry. */
data class BoughtItem(
    val item: ShoppingItem,
    /** In the ingredient's pantry unit; null keeps the pantry amount untracked. */
    val quantity: Double?,
    val expiresAt: LocalDate?,
)

/** "Kúpené → do špajze": adds bought ingredients to the pantry and clears them from the list. */
class MoveBoughtToPantryUseCase(
    private val shopping: ShoppingRepository,
    private val pantry: PantryRepository,
) {
    suspend operator fun invoke(householdId: String, bought: List<BoughtItem>, addedBy: String) {
        // Free-text items have no ingredient to store; they just leave the list.
        bought.forEach { entry ->
            entry.item.ingredient?.let { pantry.add(householdId, it, entry.quantity, entry.expiresAt, addedBy) }
        }
        shopping.remove(householdId, bought.map { it.item.id })
    }
}
