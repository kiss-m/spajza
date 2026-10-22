package sk.matej.spajza.domain.catalog

/**
 * Every threshold used to derive [sk.matej.spajza.domain.model.ComputedTag]s at import.
 * Nutrition values are per serving.
 */
data class TagThresholds(
    /** HIGH_PROTEIN: at least this much protein ... */
    val highProteinMinGrams: Double = 25.0,
    /** ... and at least this share of energy from protein (protein g × 4 / kcal). */
    val highProteinMinEnergyShare: Double = 0.25,
    /** BULK ("objemovka"): at least this much energy ... */
    val bulkMinKcal: Double = 700.0,
    /** ... with at least this much protein. */
    val bulkMinProtein: Double = 30.0,
    /** LIGHT ("diétne"): at most this much energy ... */
    val lightMaxKcal: Double = 450.0,
    /** ... and at most this much sugar. */
    val lightMaxSugar: Double = 15.0,
    /**
     * CHEAP: price tiers of non-optional MAIN and SECONDARY lines (CHEAP = 1, MID = 2,
     * EXPENSIVE = 3), averaged with role weights, must not exceed this. Any EXPENSIVE line among
     * them rules the tag out. Seasonings do not count.
     */
    val cheapMaxAveragePrice: Double = 1.5,
    /** QUICK: total time at most this many minutes. */
    val quickMaxMinutes: Int = 20,
)
