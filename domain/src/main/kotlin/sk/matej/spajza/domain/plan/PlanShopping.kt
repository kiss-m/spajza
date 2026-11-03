package sk.matej.spajza.domain.plan

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.unit.UnitConverter

/** An ingredient the plan still needs: [amount] in its pantry unit, null for a "to taste" line. */
data class PlanShortfall(val ingredientId: String, val amount: Double?, val recipeIds: Set<String>)

/**
 * One combined shopping list for planned meals (D13): what every entry's required lines need,
 * scaled to its servings, minus what the pantry and the shopping list already hold.
 */
object PlanShopping {
    private const val NEGLIGIBLE_GRAMS = 1e-6

    fun shortfall(
        catalog: Catalog,
        entries: Collection<PlanEntry>,
        pantry: List<PantryItem>,
        staples: Set<String>,
        shopping: List<ShoppingItem>,
    ): List<PlanShortfall> {
        val neededGrams = linkedMapOf<String, Double>()
        val reasons = mutableMapOf<String, MutableSet<String>>()
        entries.sortedWith(compareBy<PlanEntry> { it.date }.thenBy { it.meal }).forEach { entry ->
            val recipe = catalog.recipesById[entry.recipeId] ?: return@forEach
            val factor = entry.servings.toDouble() / recipe.servings
            recipe.ingredients.filterNot { it.optional }.forEach { line ->
                neededGrams.merge(line.ingredientId, line.grams * factor, Double::plus)
                reasons.getOrPut(line.ingredientId) { linkedSetOf() } += recipe.id
            }
        }
        val pantryById = pantry
            .filter { it.quantity == null || it.quantity > 0 }
            .mapNotNull { item -> item.catalogId?.let { it to item } }
            .toMap()
        val listById = shopping.mapNotNull { item -> (item.ingredient as? IngredientRef.Catalog)?.let { it.id to item } }.toMap()

        return neededGrams.mapNotNull { (id, grams) ->
            if (id in staples) return@mapNotNull null
            val ingredient = catalog.ingredientsById[id] ?: return@mapNotNull null
            val stored = pantryById[id]
            val listed = listById[id]
            // Untracked pantry amounts and items already bought count as enough.
            if (stored != null && stored.quantity == null) return@mapNotNull null
            if (listed?.checked == true) return@mapNotNull null
            val recipeIds = reasons.getValue(id)
            if (grams <= 0) {
                // A "to taste" line: buy it only when the household has none at all.
                return@mapNotNull if (stored == null && listed == null) PlanShortfall(id, null, recipeIds) else null
            }
            val have = listOfNotNull(stored?.quantity, listed?.amount).sumOf { UnitConverter.pantryUnitToGrams(it, ingredient) }
            val short = grams - have
            if (short <= NEGLIGIBLE_GRAMS) return@mapNotNull null
            PlanShortfall(id, UnitConverter.roundUpPantryAmount(UnitConverter.gramsToPantryUnit(short, ingredient)), recipeIds)
        }
    }
}
