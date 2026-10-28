package sk.matej.spajza.domain.own

import sk.matej.spajza.domain.model.MealType

/** A recipe as imported or typed, before its ingredient lines are matched to the catalog. */
data class RecipeDraft(
    val title: String = "",
    val description: String = "",
    val servings: Int? = null,
    val totalTimeMin: Int? = null,
    val ingredientLines: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val mealTypes: Set<MealType> = emptySet(),
    val source: RecipeSource = RecipeSource.MANUAL,
    val sourceUrl: String? = null,
)
