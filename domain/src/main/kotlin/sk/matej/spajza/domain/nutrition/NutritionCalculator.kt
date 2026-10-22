package sk.matej.spajza.domain.nutrition

import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.unit.MeasureUnit

/**
 * Computes nutrition from grams × per-100 g values of the ingredient table. This is the only
 * source of nutrition numbers in the app; imported or external numbers are never shown as-is.
 */
class NutritionCalculator(private val ingredientById: (String) -> Ingredient?) {

    /** Whole-recipe totals. Optional lines are left out unless [includeOptional] is set. */
    fun total(recipe: Recipe, includeOptional: Boolean = false): Nutrition =
        recipe.ingredients
            .filter { includeOptional || !it.optional }
            .fold(Nutrition.ZERO) { sum, line -> sum + ingredient(line.ingredientId).nutrition * (line.grams / 100) }

    fun perServing(recipe: Recipe, includeOptional: Boolean = false): Nutrition =
        total(recipe, includeOptional) / recipe.servings.toDouble()

    /** The recipe with every amount scaled from its own servings to [servings]. */
    fun scale(recipe: Recipe, servings: Int): Recipe {
        require(servings > 0) { "servings must be positive, was $servings" }
        val factor = servings.toDouble() / recipe.servings
        return recipe.copy(
            servings = servings,
            ingredients = recipe.ingredients.map { line ->
                if (line.unit == MeasureUnit.TO_TASTE) {
                    line
                } else {
                    line.copy(amount = line.amount * factor, grams = line.grams * factor)
                }
            },
        )
    }

    private fun ingredient(id: String): Ingredient =
        requireNotNull(ingredientById(id)) { "Unknown ingredient '$id'" }
}
