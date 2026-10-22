package sk.matej.spajza.domain.diet

import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.Recipe

data class AllergenConflict(val ingredientId: String, val allergens: Set<Allergen>, val optional: Boolean)

/**
 * Hard allergen filter. A recipe is safe when no required line contains an avoided allergen;
 * optional lines that do are reported so the UI can tell the user to leave them out.
 */
object AllergenGuard {
    fun conflicts(recipe: Recipe, ingredientById: (String) -> Ingredient?, avoided: Set<Allergen>): List<AllergenConflict> =
        if (avoided.isEmpty()) {
            emptyList()
        } else {
            recipe.ingredients.mapNotNull { line ->
                val ingredient = requireNotNull(ingredientById(line.ingredientId)) { "Unknown ingredient '${line.ingredientId}'" }
                val hit = ingredient.allergens intersect avoided
                if (hit.isEmpty()) null else AllergenConflict(line.ingredientId, hit, line.optional)
            }
        }

    fun isSafe(recipe: Recipe, ingredientById: (String) -> Ingredient?, avoided: Set<Allergen>): Boolean =
        conflicts(recipe, ingredientById, avoided).none { !it.optional }
}
