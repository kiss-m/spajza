package sk.matej.spajza.domain.testing

import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.model.RecipeIngredient
import sk.matej.spajza.domain.model.RecipeTag
import sk.matej.spajza.domain.unit.MeasureUnit

fun line(
    ingredientId: String,
    grams: Double,
    role: IngredientRole = IngredientRole.MAIN,
    optional: Boolean = false,
) = RecipeIngredient(
    ingredientId = ingredientId,
    amount = grams,
    unit = MeasureUnit.G,
    grams = grams,
    role = role,
    optional = optional,
)

fun recipe(
    id: String = "r",
    ingredients: List<RecipeIngredient>,
    servings: Int = 1,
    totalTimeMin: Int = 30,
    cuisine: Cuisine = Cuisine.INTERNATIONAL,
    mealTypes: Set<MealType> = setOf(MealType.DINNER),
    equipment: Set<Equipment> = setOf(Equipment.STOVE),
    tags: Set<RecipeTag> = emptySet(),
) = Recipe(
    id = id,
    title = LocalizedText(sk = id, en = id),
    description = LocalizedText(sk = "", en = ""),
    cuisine = cuisine,
    mealTypes = mealTypes,
    totalTimeMin = totalTimeMin,
    activeTimeMin = totalTimeMin,
    difficulty = 1,
    servings = servings,
    equipment = equipment,
    tags = tags,
    computedTags = emptySet(),
    pairsWith = emptyList(),
    ingredients = ingredients,
    steps = emptyList(),
)
