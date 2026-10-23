package sk.matej.spajza.ui.common

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.search.IngredientSearch

/** A catalog ingredient as pickers and search suggestions show it. */
data class IngredientOption(
    val ingredientId: String,
    val name: LocalizedText,
    val category: IngredientCategory,
    val pantryUnit: PantryUnit,
)

fun Ingredient.toOption() = IngredientOption(id, name, category, pantryUnit)

/** Ingredient search for pickers; ids in `exclude` are left out. */
class IngredientOptions(catalog: Catalog) {
    private val search = IngredientSearch(catalog.ingredients)

    fun search(query: String, exclude: Set<String> = emptySet(), limit: Int = 8): List<IngredientOption> =
        search.search(query, limit + exclude.size)
            .map { it.ingredient }
            .filter { it.id !in exclude }
            .take(limit)
            .map { it.toOption() }
}

/** Plain text for an editable amount: "500", "1.5", empty for none. */
fun quantityInput(quantity: Double?): String = when {
    quantity == null -> ""
    quantity % 1.0 == 0.0 -> quantity.toLong().toString()
    else -> quantity.toString()
}
