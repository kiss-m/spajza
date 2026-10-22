package sk.matej.spajza.domain.diet

import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.Recipe

data class DietViolation(val ingredientId: String, val diet: Diet, val optional: Boolean)

/**
 * Hard diet filter. A recipe is allowed when no required line breaks a diet; optional lines that do
 * are reported so the UI can suggest leaving them out.
 */
object DietFilter {
    fun allowsIngredient(ingredient: Ingredient, diet: Diet): Boolean = forbiddenFlags(diet).none { it in ingredient.dietFlags }

    fun violations(recipe: Recipe, ingredientById: (String) -> Ingredient?, diets: Set<Diet>): List<DietViolation> =
        if (diets.isEmpty()) {
            emptyList()
        } else {
            recipe.ingredients.flatMap { line ->
                val ingredient = requireNotNull(ingredientById(line.ingredientId)) { "Unknown ingredient '${line.ingredientId}'" }
                diets.filterNot { allowsIngredient(ingredient, it) }.map { DietViolation(line.ingredientId, it, line.optional) }
            }
        }

    fun allows(recipe: Recipe, ingredientById: (String) -> Ingredient?, diets: Set<Diet>): Boolean =
        violations(recipe, ingredientById, diets).none { !it.optional }

    private fun forbiddenFlags(diet: Diet): Set<DietFlag> = when (diet) {
        Diet.VEGAN -> setOf(DietFlag.ANIMAL_PRODUCT, DietFlag.MEAT, DietFlag.FISH, DietFlag.DAIRY, DietFlag.EGG)
        Diet.VEGETARIAN -> setOf(DietFlag.MEAT, DietFlag.FISH)
        Diet.PESCATARIAN -> setOf(DietFlag.MEAT)
        Diet.GLUTEN_FREE -> setOf(DietFlag.GLUTEN)
        Diet.DAIRY_FREE -> setOf(DietFlag.DAIRY)
        Diet.EGG_FREE -> setOf(DietFlag.EGG)
        Diet.PORK_FREE -> setOf(DietFlag.PORK)
    }
}
