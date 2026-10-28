package sk.matej.spajza.domain.custom

import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.NutritionSource
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.nutrition.Nutrition
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.unit.MeasureUnit
import sk.matej.spajza.domain.unit.MissingConversionException
import sk.matej.spajza.domain.unit.UnitConverter

enum class CustomSource { OPEN_FOOD_FACTS, MANUAL }

/**
 * A household's own food, from a barcode (Open Food Facts) or typed in; stored at
 * `households/{hid}/customIngredients/{id}`. Text is in one language, as the user sees it.
 */
data class CustomIngredient(
    val id: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val category: IngredientCategory = IngredientCategory.OTHER,
    /** Per 100 g; energy is always recomputed from the macros by [Energy]. */
    val nutrition: Nutrition = Nutrition.ZERO,
    /** False when the label values are missing; the food then counts as zero in recipe macros. */
    val nutritionKnown: Boolean = false,
    val pantryUnit: PantryUnit = PantryUnit.G,
    val gramsPerPiece: Double? = null,
    val densityGPerMl: Double? = null,
    /** One package in [pantryUnit], offered as the amount when the food goes to the pantry. */
    val packageQuantity: Double? = null,
    val allergens: Set<Allergen> = emptySet(),
    val dietFlags: Set<DietFlag> = emptySet(),
    /**
     * The catalog ingredient this product is a kind of ("Rajo polotučné mlieko" → `milk`). Linked
     * products go to the pantry as that ingredient, so recipes find them.
     */
    val catalogId: String? = null,
    val source: CustomSource = CustomSource.MANUAL,
) {
    /** The name with the brand, unless the name already says it: "Horalky · Sedita". */
    val displayName: String
        get() = if (brand.isNullOrBlank() || name.contains(brand, ignoreCase = true)) name else "$name · $brand"

    /** Where an amount of this food is kept in the pantry. */
    val pantryRef: IngredientRef get() = catalogId?.let { IngredientRef.Catalog(it) } ?: IngredientRef.Custom(id)

    /** The food as an [Ingredient], for nutrition and unit conversion; its id is prefixed with `custom:`. */
    fun toIngredient(): Ingredient = Ingredient(
        id = "$INGREDIENT_ID_PREFIX$id",
        name = LocalizedText.same(name),
        synonyms = emptyList(),
        category = category,
        nutrition = nutrition,
        nutritionSource = NutritionSource.Estimate,
        pantryUnit = pantryUnit,
        gramsPerPiece = gramsPerPiece,
        densityGPerMl = densityGPerMl ?: if (pantryUnit == PantryUnit.ML) LIQUID_DENSITY else null,
        allergens = allergens,
        dietFlags = dietFlags,
    )

    /**
     * [amount] in this food's [pantryUnit] expressed in [target]'s pantry unit, or null when the
     * densities or piece weights needed are unknown.
     */
    fun amountAs(target: Ingredient, amount: Double): Double? {
        val unit = when (pantryUnit) {
            PantryUnit.G -> MeasureUnit.G
            PantryUnit.ML -> MeasureUnit.ML
            PantryUnit.PCS -> MeasureUnit.PCS
        }
        // Same unit: grams stay grams and a piece (an egg, a roll) stays a piece.
        if (target.pantryUnit == pantryUnit) return amount
        return try {
            UnitConverter.gramsToPantryUnit(UnitConverter.toGrams(amount, unit, toIngredient()), target)
        } catch (e: MissingConversionException) {
            null
        }
    }

    companion object {
        const val INGREDIENT_ID_PREFIX = "custom:"

        /** Drinks without a known density count as water. */
        const val LIQUID_DENSITY = 1.0

        /** Barcode products have a fixed id, so household members scanning one product share it. */
        fun idForBarcode(barcode: String): String = "b$barcode"
    }
}

/** Energy from the macros with the EU label factors (Regulation 1169/2011, Annex XIV). */
object Energy {
    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_CARBS = 4.0
    const val KCAL_PER_G_FAT = 9.0
    const val KCAL_PER_G_FIBER = 2.0

    fun kcal(protein: Double, carbs: Double, fat: Double, fiber: Double): Double =
        KCAL_PER_G_PROTEIN * protein + KCAL_PER_G_CARBS * carbs + KCAL_PER_G_FAT * fat + KCAL_PER_G_FIBER * fiber

    /** [nutrition] with its energy recomputed from the macros, never taken from the label. */
    fun recomputed(nutrition: Nutrition): Nutrition =
        nutrition.copy(kcal = kcal(nutrition.protein, nutrition.carbs, nutrition.fat, nutrition.fiber))
}
