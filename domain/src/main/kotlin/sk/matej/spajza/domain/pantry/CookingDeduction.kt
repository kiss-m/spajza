package sk.matej.spajza.domain.pantry

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.unit.UnitConverter

/** A change to a pantry document; amounts are in the ingredient's pantry unit. */
sealed interface PantryChange {
    data class Decrease(val itemId: String, val amount: Double) : PantryChange

    data class Increase(val itemId: String, val amount: Double) : PantryChange

    data class Remove(val item: PantryItem) : PantryChange

    data class Restore(val item: PantryItem) : PantryChange
}

/** What "cooked it" does to the pantry, and how to take it back. */
data class Deduction(val changes: List<PantryChange>, val undo: List<PantryChange>) {
    val isEmpty: Boolean get() = changes.isEmpty()
}

/**
 * Subtracts what a cooked recipe used from pantry items that track a quantity. Staples, items
 * without a quantity and optional lines are never touched; a missing ingredient covered by a
 * substitute is taken from the substitute using its ratio. Items that reach zero are removed.
 */
class CookingDeduction(private val catalog: Catalog) {

    fun plan(recipe: Recipe, servings: Int, pantry: List<PantryItem>, staples: Set<String>): Deduction {
        require(servings > 0) { "servings must be positive, was $servings" }
        val factor = servings.toDouble() / recipe.servings
        val byId = pantry.filter { it.catalogId != null }.associateBy { it.catalogId!! }

        // Grams to take from each pantry item, summed in case a substitute is also used elsewhere.
        val gramsByItem = linkedMapOf<String, Double>()
        recipe.ingredients.filter { !it.optional && it.grams > 0 && it.ingredientId !in staples }.forEach { line ->
            val needed = line.grams * factor
            val direct = byId[line.ingredientId]
            val (item, grams) = when {
                direct != null -> direct to needed
                else -> catalog.substitutesFor[line.ingredientId].orEmpty()
                    .firstNotNullOfOrNull { sub ->
                        byId[sub.substituteId]?.takeIf { sub.substituteId !in staples }?.let { it to needed * sub.ratio }
                    }
                    ?: return@forEach
            }
            if (item.quantity != null) gramsByItem.merge(item.catalogId!!, grams, Double::plus)
        }

        val changes = mutableListOf<PantryChange>()
        val undo = mutableListOf<PantryChange>()
        gramsByItem.forEach { (ingredientId, grams) ->
            val item = byId.getValue(ingredientId)
            val quantity = item.quantity ?: return@forEach
            val amount = UnitConverter.gramsToPantryUnit(grams, catalog.ingredient(ingredientId))
            if (quantity - amount <= EPSILON) {
                changes += PantryChange.Remove(item)
                undo += PantryChange.Restore(item)
            } else {
                changes += PantryChange.Decrease(item.id, amount)
                undo += PantryChange.Increase(item.id, amount)
            }
        }
        return Deduction(changes, undo)
    }

    private companion object {
        const val EPSILON = 1e-6
    }
}
