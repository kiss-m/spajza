package sk.matej.spajza.domain.matching

/**
 * Every weight and threshold the recipe matcher uses.
 *
 * The score is `Σ weight × component + adjustments`. Components are normalized (coverage, taste,
 * macros and expiry to 0..1, time to -1..1) and the weights decrease in the priority order of
 * docs/SPEC.md: coverage > ratings/taste > macro fit > time > expiring items. Adjustments for skill,
 * spiciness, budget, meal prep and recent cooking are small point values on top.
 */
data class MatcherConfig(
    // 1) Ingredient coverage: Σ(role weight × availability) / Σ(role weight) over required lines.
    val coverageWeight: Double = 100.0,
    /** Availability of a line covered by a substitute from the pantry or staples. */
    val substituteAvailability: Double = 0.8,
    /** Recipes missing more required ingredients than this are hidden. */
    val maxMissingForToBuy: Int = 3,

    // 2) Ratings and taste: learned + declared likes - likeness to dislikes, from -[dislikeSimilarityPenalty]
    //    to 1. Learned combines cooking this recipe, similarity and the profile as 1 - (1 - a)(1 - b)(1 - c).
    //    A liked or favorite recipe always scores 1. Likeness is the Jaccard index of cuisine, meal types,
    //    tags and main ingredients.
    val tasteWeight: Double = 40.0,
    /** A recipe cooked this many times counts as liked; fewer times count proportionally. */
    val cooksForDirectLike: Int = 3,
    /** Weight of a like or favorite as a source of the taste profile and similarity. */
    val likeSourceWeight: Double = 1.0,
    val favoriteSourceWeight: Double = 1.5,
    /** Each "cooked it" event as a source; repeated recent cooking adds up to a full like. */
    val cookEventSourceWeight: Double = 0.5,
    /** A cook event's weight halves every this many days ... */
    val cookHalfLifeDays: Double = 45.0,
    /** ... but never drops below this share. */
    val cookEventFloor: Double = 0.25,
    /** How much of the taste component similarity to one liked or cooked recipe can earn. */
    val similarityShare: Double = 0.6,
    /**
     * The profile adds up all sources: the share of source weight with the recipe's cuisine, with its
     * most common main ingredient and, on average, with its tags, times these points ...
     */
    val profileCuisinePoints: Double = 0.3,
    val profileMainPoints: Double = 0.3,
    val profileTagPoints: Double = 0.15,
    /** ... times confidence W / (W + this), W being the total source weight. */
    val profileConfidenceHalf: Double = 2.0,
    /** Taken off at full likeness (Jaccard 1) to a disliked recipe, falling linearly to 0 at ... */
    val dislikeSimilarityPenalty: Double = 0.3,
    /** ... this likeness, so only close relatives of a disliked recipe drop. */
    val dislikeLikenessFrom: Double = 0.5,
    val likedCuisineBonus: Double = 0.3,
    /** Per liked ingredient among required MAIN/SECONDARY lines ... */
    val likedIngredientBonus: Double = 0.15,
    /** ... capped at this. */
    val likedIngredientBonusCap: Double = 0.3,

    // 3) Macro fit, against a reference of one main meal.
    val macroWeight: Double = 25.0,
    /** Grams of protein per 100 kcal that earn the full protein half of the macro component. */
    val proteinPer100KcalReference: Double = 8.0,
    val mealShareOfDailyTarget: Double = 1.0 / 3,
    /** Used when the profile has no target yet. */
    val defaultDailyKcal: Int = 2000,
    /** LOSE: full energy fit up to this share of the meal reference, zero from [loseZeroAt]. */
    val loseFullUpTo: Double = 0.8,
    val loseZeroAt: Double = 1.5,
    /** GAIN: full energy fit from this share of the meal reference, zero at [gainZeroAt] and below. */
    val gainFullFrom: Double = 1.1,
    val gainZeroAt: Double = 0.5,
    /** MAINTAIN: energy fit falls linearly to zero at this distance from the reference. */
    val maintainTolerance: Double = 0.6,

    // 4) Time: within the weekday/weekend limit 1 - 0.5 × (time / limit); above it down to -1.
    val timeWeight: Double = 15.0,

    // 5) Items that expire within [expiringWithinDays] days, today included.
    val expiryWeight: Double = 10.0,
    val expiringWithinDays: Long = 3,
    val expiryPerItem: Double = 0.5,

    // Small adjustments, in points.
    val skillPenaltyPerLevel: Double = 3.0,
    val spicyPenaltyNone: Double = 8.0,
    val spicyPenaltyMild: Double = 2.0,
    val spicyBonusHot: Double = 2.0,
    /** Bonus for CHEAP recipes and penalty for EXPENSIVE ingredients when the budget is CHEAP. */
    val budgetPoints: Double = 4.0,
    /** Penalty for EXPENSIVE ingredients when the budget is MID. */
    val budgetPointsMid: Double = 2.0,
    val mealPrepBonus: Double = 3.0,
    /** For variety: penalty for a recipe cooked within the last [recentCookDays] days, today included. */
    val recentCookPenalty: Double = 6.0,
    val recentCookDays: Long = 3,
)
