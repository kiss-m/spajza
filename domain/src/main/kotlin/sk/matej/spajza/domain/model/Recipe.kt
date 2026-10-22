package sk.matej.spajza.domain.model

import sk.matej.spajza.domain.unit.MeasureUnit

data class Recipe(
    val id: String,
    val title: LocalizedText,
    val description: LocalizedText,
    val cuisine: Cuisine,
    val mealTypes: Set<MealType>,
    val totalTimeMin: Int,
    val activeTimeMin: Int,
    /** 1 = easy, 3 = demanding. */
    val difficulty: Int,
    val servings: Int,
    val equipment: Set<Equipment>,
    val tags: Set<RecipeTag>,
    val computedTags: Set<ComputedTag>,
    val pairsWith: List<String>,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
)

data class RecipeIngredient(
    val ingredientId: String,
    val amount: Double,
    val unit: MeasureUnit,
    /** [amount] in [unit] normalized to grams at import. */
    val grams: Double,
    val role: IngredientRole,
    val optional: Boolean = false,
    val note: LocalizedText? = null,
)

data class RecipeStep(
    val index: Int,
    val text: LocalizedText,
    val timerSeconds: Int? = null,
)

/** [substituteId] can replace [ingredientId]; use [ratio] × the original amount. */
data class Substitution(
    val ingredientId: String,
    val substituteId: String,
    val ratio: Double,
    val note: LocalizedText? = null,
)
