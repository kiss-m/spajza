package sk.matej.spajza.domain.nutrition

/** Energy in kcal, everything else in grams. */
data class Nutrition(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val salt: Double,
) {
    operator fun plus(other: Nutrition) = Nutrition(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat,
        fiber = fiber + other.fiber,
        sugar = sugar + other.sugar,
        salt = salt + other.salt,
    )

    operator fun times(factor: Double) = Nutrition(
        kcal = kcal * factor,
        protein = protein * factor,
        carbs = carbs * factor,
        fat = fat * factor,
        fiber = fiber * factor,
        sugar = sugar * factor,
        salt = salt * factor,
    )

    operator fun div(divisor: Double) = times(1.0 / divisor)

    /** Grams of protein per 100 kcal; 0 for energy-free food. */
    val proteinPer100Kcal: Double
        get() = if (kcal > 0) protein / kcal * 100 else 0.0

    companion object {
        val ZERO = Nutrition(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}
