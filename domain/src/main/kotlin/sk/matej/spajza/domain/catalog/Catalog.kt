package sk.matej.spajza.domain.catalog

import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.model.Substitution

/** The read-only recipe and ingredient catalog bundled with the app. */
data class Catalog(
    val ingredients: List<Ingredient>,
    val recipes: List<Recipe>,
    val substitutions: List<Substitution>,
) {
    val ingredientsById: Map<String, Ingredient> by lazy { ingredients.associateBy { it.id } }
    val recipesById: Map<String, Recipe> by lazy { recipes.associateBy { it.id } }

    /** Substitutes keyed by the ingredient they replace. */
    val substitutesFor: Map<String, List<Substitution>> by lazy { substitutions.groupBy { it.ingredientId } }

    fun ingredient(id: String): Ingredient = requireNotNull(ingredientsById[id]) { "Unknown ingredient '$id'" }

    companion object {
        val EMPTY = Catalog(emptyList(), emptyList(), emptyList())
    }
}
