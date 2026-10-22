package sk.matej.spajza.domain.model

import sk.matej.spajza.domain.nutrition.Nutrition

data class Ingredient(
    /** Stable slug, e.g. `chicken-breast`. Referenced by recipes and Firestore documents. */
    val id: String,
    val name: LocalizedText,
    /** SK/CZ/EN names including common inflected forms; used by search and line parsing. */
    val synonyms: List<String>,
    val category: IngredientCategory,
    /** Values per 100 g. */
    val nutrition: Nutrition,
    val nutritionSource: NutritionSource,
    val pantryUnit: PantryUnit,
    val gramsPerPiece: Double? = null,
    val densityGPerMl: Double? = null,
    val allergens: Set<Allergen> = emptySet(),
    val dietFlags: Set<DietFlag> = emptySet(),
    val isDefaultStaple: Boolean = false,
    val typicalShelfLifeDays: Int? = null,
    val priceTier: PriceTier = PriceTier.MID,
    val spicy: Boolean = false,
)

sealed interface NutritionSource {
    data class Usda(val fdcId: Int) : NutritionSource

    /** Typical label values for foods without a USDA match; listed in docs/DATA_TODO.md. */
    data object Estimate : NutritionSource

    companion object {
        private val USDA = Regex("""USDA FDC (\d+)""")

        fun parse(raw: String): NutritionSource? = when {
            raw == "estimate" -> Estimate
            else -> USDA.matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull()?.let(::Usda)
        }
    }
}
