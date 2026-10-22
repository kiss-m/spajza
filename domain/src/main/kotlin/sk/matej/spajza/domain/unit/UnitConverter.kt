package sk.matej.spajza.domain.unit

import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.PantryUnit

class MissingConversionException(
    val ingredientId: String,
    val unit: MeasureUnit,
) : IllegalArgumentException(
    "Cannot convert $unit of '$ingredientId' to grams: " +
        if (unit == MeasureUnit.PCS) "gramsPerPiece is missing" else "densityGPerMl is missing",
)

object UnitConverter {
    const val TBSP_ML = 15.0
    const val TSP_ML = 5.0
    const val CUP_ML = 250.0
    const val PINCH_G = 0.5

    /** @throws MissingConversionException when the ingredient lacks the density or piece weight needed. */
    fun toGrams(amount: Double, unit: MeasureUnit, ingredient: Ingredient): Double = when (unit) {
        MeasureUnit.G -> amount
        MeasureUnit.KG -> amount * 1000
        MeasureUnit.ML -> amount * density(ingredient, unit)
        MeasureUnit.L -> amount * 1000 * density(ingredient, unit)
        MeasureUnit.TBSP -> amount * TBSP_ML * density(ingredient, unit)
        MeasureUnit.TSP -> amount * TSP_ML * density(ingredient, unit)
        MeasureUnit.CUP -> amount * CUP_ML * density(ingredient, unit)
        MeasureUnit.PCS -> amount * pieceWeight(ingredient)
        MeasureUnit.PINCH -> amount * PINCH_G
        MeasureUnit.TO_TASTE -> 0.0
    }

    /** Converts grams into the ingredient's [Ingredient.pantryUnit]. */
    fun gramsToPantryUnit(grams: Double, ingredient: Ingredient): Double = when (ingredient.pantryUnit) {
        PantryUnit.G -> grams
        PantryUnit.ML -> grams / density(ingredient, MeasureUnit.ML)
        PantryUnit.PCS -> grams / pieceWeight(ingredient)
    }

    /** Converts a quantity in the ingredient's [Ingredient.pantryUnit] into grams. */
    fun pantryUnitToGrams(quantity: Double, ingredient: Ingredient): Double = when (ingredient.pantryUnit) {
        PantryUnit.G -> quantity
        PantryUnit.ML -> quantity * density(ingredient, MeasureUnit.ML)
        PantryUnit.PCS -> quantity * pieceWeight(ingredient)
    }

    private fun density(ingredient: Ingredient, unit: MeasureUnit): Double =
        ingredient.densityGPerMl ?: throw MissingConversionException(ingredient.id, unit)

    private fun pieceWeight(ingredient: Ingredient): Double =
        ingredient.gramsPerPiece ?: throw MissingConversionException(ingredient.id, MeasureUnit.PCS)
}
