package sk.matej.spajza.data.seed

import kotlinx.serialization.Serializable
import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.model.RecipeTag
import sk.matej.spajza.domain.unit.MeasureUnit

// JSON shapes of the files in app/src/main/assets/seed/. Field names are part of the seed format.

@Serializable
data class LocalizedDto(val sk: String, val en: String)

@Serializable
data class NutritionDto(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val salt: Double,
)

@Serializable
data class IngredientDto(
    val id: String,
    val name: LocalizedDto,
    val synonyms: List<String> = emptyList(),
    val category: IngredientCategory,
    /** Per 100 g. Null only while the USDA lookup is pending; the import rejects it. */
    val nutrition: NutritionDto? = null,
    /** "USDA FDC <id>" or "estimate". */
    val source: String? = null,
    val pantryUnit: PantryUnit,
    val gramsPerPiece: Double? = null,
    val density: Double? = null,
    val allergens: Set<Allergen> = emptySet(),
    val flags: Set<DietFlag> = emptySet(),
    val staple: Boolean = false,
    val shelfLifeDays: Int? = null,
    val price: PriceTier,
    val spicy: Boolean = false,
)

@Serializable
data class RecipeDto(
    val id: String,
    val title: LocalizedDto,
    val description: LocalizedDto,
    val cuisine: Cuisine,
    val mealTypes: Set<MealType>,
    val totalTimeMin: Int,
    val activeTimeMin: Int,
    val difficulty: Int,
    val servings: Int,
    val equipment: Set<Equipment> = emptySet(),
    val tags: Set<RecipeTag> = emptySet(),
    val pairsWith: List<String> = emptyList(),
    val ingredients: List<RecipeLineDto>,
    val steps: List<StepDto>,
)

@Serializable
data class RecipeLineDto(
    val id: String,
    /** May be omitted for [MeasureUnit.TO_TASTE]. */
    val amount: Double = 0.0,
    val unit: MeasureUnit,
    val role: IngredientRole,
    val optional: Boolean = false,
    val note: LocalizedDto? = null,
)

@Serializable
data class StepDto(val sk: String, val en: String, val timerSeconds: Int? = null)

@Serializable
data class SubstitutionDto(
    val from: String,
    val to: String,
    val ratio: Double = 1.0,
    val note: LocalizedDto? = null,
)
