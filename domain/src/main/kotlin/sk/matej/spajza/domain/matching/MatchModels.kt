package sk.matej.spajza.domain.matching

import sk.matej.spajza.domain.diet.DietRestrictions
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.model.RecipeIngredient
import sk.matej.spajza.domain.nutrition.Nutrition
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.profile.Goal
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.rating.CookEvent
import sk.matej.spajza.domain.rating.Rating

/** What the household has: pantry, staples that always count as available, and equipment. */
data class KitchenState(
    val pantry: List<PantryItem>,
    val staples: Set<String>,
    val equipment: Set<Equipment>,
)

/** Everything about the user that shapes the ranking. */
data class Personalization(
    val preferences: Preferences = Preferences(),
    val restrictions: DietRestrictions = DietRestrictions(),
    val goal: Goal = Goal.MAINTAIN,
    /** The daily energy target; null until the profile is filled in. */
    val dailyKcal: Int? = null,
    val ratings: Map<String, Rating> = emptyMap(),
    val cookHistory: List<CookEvent> = emptyList(),
)

enum class LineStatus {
    /** A household staple; always available. */
    STAPLE,
    HAVE,

    /** In the pantry, but less than the recipe needs. */
    PARTIAL,

    /** Not in the pantry, but a substitute is. */
    SUBSTITUTE,
    MISSING,
}

data class LineMatch(
    val line: RecipeIngredient,
    val status: LineStatus,
    /** 0..1 contribution to coverage. */
    val availability: Double,
    val substituteId: String? = null,
    val expiresSoon: Boolean = false,
)

data class MissingIngredient(val ingredientId: String, val priceTier: PriceTier)

/** Weighted points per component, for tuning and debugging. */
data class ScoreBreakdown(
    val coverage: Double,
    val taste: Double,
    val macros: Double,
    val time: Double,
    val expiry: Double,
    val adjustments: Double,
) {
    val total: Double get() = coverage + taste + macros + time + expiry + adjustments
}

data class RecipeMatch(
    val recipe: Recipe,
    val score: Double,
    /** 0..1 weighted share of required ingredients that are available. */
    val coverage: Double,
    /** Every line of the recipe, optional ones included. */
    val lines: List<LineMatch>,
    /** Required ingredients that are neither in the pantry nor covered by a staple or substitute. */
    val missing: List<MissingIngredient>,
    val perServing: Nutrition,
    val expiringItems: Int,
    val breakdown: ScoreBreakdown,
)

data class MatchResult(
    /** Nothing missing, best first. */
    val ready: List<RecipeMatch>,
    /** 1..maxMissingForToBuy missing, best first. */
    val toBuy: List<RecipeMatch>,
) {
    companion object {
        val EMPTY = MatchResult(emptyList(), emptyList())
    }
}
