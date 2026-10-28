package sk.matej.spajza.ui.product

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.custom.CustomIngredient
import sk.matej.spajza.domain.custom.Energy
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.nutrition.Nutrition
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.parseNumber
import sk.matej.spajza.ui.common.quantityInput
import sk.matej.spajza.ui.common.toOption
import java.time.LocalDate

/** Where the product on the form came from. */
enum class ProductStatus { KNOWN, FOUND, UNKNOWN, OFFLINE, MANUAL }

/**
 * The editable product: label values per 100 g as typed, the catalog ingredient it counts as, and
 * the amount going into the pantry now. Plain Kotlin, so it is tested without Android.
 */
data class ProductForm(
    /** The stored or drafted food; keeps what the form does not show (allergens, diet flags, source). */
    val base: CustomIngredient,
    val name: String,
    val brand: String,
    val category: IngredientCategory,
    val unit: PantryUnit,
    val packageText: String,
    /** Grams in one piece, for foods counted in pieces. */
    val pieceText: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val fiber: String,
    val sugar: String,
    val salt: String,
    val link: IngredientOption?,
    /** The amount for the pantry, in [unit]. */
    val quantityText: String,
    val expiresAt: LocalDate?,
) {
    val canSave: Boolean get() = name.isNotBlank()

    /** Label values when protein, carbohydrates and fat are all filled in. */
    private val nutrition: Nutrition?
        get() {
            val p = parseNumber(protein) ?: return null
            val c = parseNumber(carbs) ?: return null
            val f = parseNumber(fat) ?: return null
            val fib = parseNumber(fiber) ?: 0.0
            if (p < 0 || c < 0 || f < 0 || fib < 0 || p + c + f > MAX_MACROS_G) return null
            return Energy.recomputed(
                Nutrition(
                    kcal = 0.0,
                    protein = p,
                    carbs = c,
                    fat = f,
                    fiber = fib,
                    sugar = (parseNumber(sugar) ?: 0.0).coerceIn(0.0, c),
                    salt = (parseNumber(salt) ?: 0.0).coerceAtLeast(0.0),
                ),
            )
        }

    /** Energy per 100 g as the app computes it from the macros; null until they are complete. */
    val kcal: Double? get() = nutrition?.kcal

    val quantity: Double? get() = parseNumber(quantityText)?.takeIf { it > 0 }

    fun toIngredient(): CustomIngredient {
        val values = nutrition
        return base.copy(
            name = name.trim(),
            brand = brand.trim().takeIf { it.isNotEmpty() },
            category = category,
            nutrition = values ?: Nutrition.ZERO,
            nutritionKnown = values != null,
            pantryUnit = unit,
            gramsPerPiece = parseNumber(pieceText)?.takeIf { it > 0 },
            densityGPerMl = if (unit == PantryUnit.ML) base.densityGPerMl ?: CustomIngredient.LIQUID_DENSITY else base.densityGPerMl,
            packageQuantity = parseNumber(packageText)?.takeIf { it > 0 },
            catalogId = link?.ingredientId,
        )
    }

    companion object {
        private const val MAX_MACROS_G = 101.0

        fun of(ingredient: CustomIngredient, catalog: Catalog): ProductForm {
            val n = ingredient.nutrition.takeIf { ingredient.nutritionKnown }
            return ProductForm(
                base = ingredient,
                name = ingredient.name,
                brand = ingredient.brand.orEmpty(),
                category = ingredient.category,
                unit = ingredient.pantryUnit,
                packageText = quantityInput(ingredient.packageQuantity),
                pieceText = quantityInput(ingredient.gramsPerPiece),
                protein = n?.protein?.let(::decimalInput).orEmpty(),
                carbs = n?.carbs?.let(::decimalInput).orEmpty(),
                fat = n?.fat?.let(::decimalInput).orEmpty(),
                fiber = n?.fiber?.let(::decimalInput).orEmpty(),
                sugar = n?.sugar?.let(::decimalInput).orEmpty(),
                salt = n?.salt?.let(::decimalInput).orEmpty(),
                link = ingredient.catalogId?.let { catalog.ingredientsById[it] }?.toOption(),
                quantityText = quantityInput(ingredient.packageQuantity),
                expiresAt = null,
            )
        }

        /** "3.4", "12", without float noise like 0.30000000000000004. */
        private fun decimalInput(value: Double): String {
            val rounded = Math.round(value * 100) / 100.0
            return quantityInput(rounded)
        }
    }
}
