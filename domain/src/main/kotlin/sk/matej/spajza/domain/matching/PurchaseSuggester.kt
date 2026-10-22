package sk.matej.spajza.domain.matching

import sk.matej.spajza.domain.model.PriceTier

data class PurchaseConfig(
    val maxItems: Int = 3,
    /**
     * A recipe that gets closer without becoming ready earns this share of its score, divided by
     * the number of items it still misses.
     */
    val partialCredit: Double = 0.3,
    /** Gains are divided by these, so a cheap item beats an expensive one that unlocks as much. */
    val priceDivisor: Map<PriceTier, Double> = mapOf(
        PriceTier.CHEAP to 1.0,
        PriceTier.MID to 1.25,
        PriceTier.EXPENSIVE to 1.6,
    ),
    /** Recipes with a lower (or negative) score still count this much. */
    val minRecipeWeight: Double = 1.0,
)

data class SuggestedItem(
    val ingredientId: String,
    val priceTier: PriceTier,
    /** Recipes that become ready once this item is bought on top of the earlier picks. */
    val unlocks: List<String>,
)

data class PurchaseSuggestion(
    /** In pick order. */
    val items: List<SuggestedItem>,
    /** Every recipe that is ready once all [items] are bought. */
    val unlockedRecipeIds: List<String>,
) {
    companion object {
        val EMPTY = PurchaseSuggestion(emptyList(), emptyList())
    }
}

/**
 * Greedy weighted max coverage over the "to buy" recipes: repeatedly picks the missing ingredient
 * with the highest price-adjusted gain and adds it to a virtual pantry. Candidates come from
 * [RecipeMatcher] results, which already passed the hard filters.
 */
class PurchaseSuggester(private val config: PurchaseConfig = PurchaseConfig()) {

    fun suggest(toBuy: List<RecipeMatch>): PurchaseSuggestion {
        val tiers = toBuy.flatMap { it.missing }.associate { it.ingredientId to it.priceTier }
        val bought = mutableSetOf<String>()
        val items = mutableListOf<SuggestedItem>()
        while (items.size < config.maxItems) {
            // Highest gain first; ties go to the cheaper item, then alphabetically for stable output.
            val best = tiers.keys
                .filter { it !in bought }
                .map { candidate -> candidate to gain(candidate, bought, toBuy) / divisor(tiers.getValue(candidate)) }
                .filter { (_, gain) -> gain > 0 }
                .sortedWith(
                    compareByDescending<Pair<String, Double>> { it.second }
                        .thenBy { tiers.getValue(it.first).ordinal }
                        .thenBy { it.first },
                )
                .firstOrNull()
                ?: break
            val readyBefore = readyWith(bought, toBuy)
            bought += best.first
            items += SuggestedItem(best.first, tiers.getValue(best.first), readyWith(bought, toBuy) - readyBefore.toSet())
        }
        return PurchaseSuggestion(items, readyWith(bought, toBuy))
    }

    /** Single-recipe mode: what to buy for [target] and which other recipes those items unlock. */
    fun forRecipe(target: RecipeMatch, toBuy: List<RecipeMatch>): PurchaseSuggestion {
        val bought = target.missing.map { it.ingredientId }.toSet()
        val unlocked = readyWith(bought, toBuy).let { if (target.recipe.id in it) it else listOf(target.recipe.id) + it }
        return PurchaseSuggestion(
            items = target.missing.map { SuggestedItem(it.ingredientId, it.priceTier, unlocked) },
            unlockedRecipeIds = unlocked,
        )
    }

    private fun gain(candidate: String, bought: Set<String>, toBuy: List<RecipeMatch>): Double =
        toBuy.sumOf { match ->
            val remaining = match.missing.map { it.ingredientId }.filter { it !in bought }
            when {
                candidate !in remaining -> 0.0
                remaining.size == 1 -> weight(match)
                else -> config.partialCredit * weight(match) / (remaining.size - 1)
            }
        }

    private fun readyWith(bought: Set<String>, toBuy: List<RecipeMatch>): List<String> =
        toBuy.filter { match -> match.missing.all { it.ingredientId in bought } }.map { it.recipe.id }

    private fun weight(match: RecipeMatch) = maxOf(match.score, config.minRecipeWeight)

    private fun divisor(tier: PriceTier) = config.priceDivisor[tier] ?: 1.0
}
