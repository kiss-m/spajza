package sk.matej.spajza.domain.testing

import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.NutritionSource
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.nutrition.Nutrition

fun nutrition(
    kcal: Double,
    protein: Double = 0.0,
    carbs: Double = 0.0,
    fat: Double = 0.0,
    fiber: Double = 0.0,
    sugar: Double = 0.0,
    salt: Double = 0.0,
) = Nutrition(kcal, protein, carbs, fat, fiber, sugar, salt)

fun ingredient(
    id: String,
    nutrition: Nutrition = nutrition(kcal = 100.0),
    pantryUnit: PantryUnit = PantryUnit.G,
    gramsPerPiece: Double? = null,
    densityGPerMl: Double? = null,
    dietFlags: Set<DietFlag> = emptySet(),
    priceTier: PriceTier = PriceTier.MID,
    category: IngredientCategory = IngredientCategory.OTHER,
    spicy: Boolean = false,
    isDefaultStaple: Boolean = false,
) = Ingredient(
    id = id,
    name = LocalizedText(sk = id, en = id),
    synonyms = emptyList(),
    category = category,
    nutrition = nutrition,
    nutritionSource = NutritionSource.Estimate,
    pantryUnit = pantryUnit,
    gramsPerPiece = gramsPerPiece,
    densityGPerMl = densityGPerMl,
    dietFlags = dietFlags,
    priceTier = priceTier,
    spicy = spicy,
    isDefaultStaple = isDefaultStaple,
)
