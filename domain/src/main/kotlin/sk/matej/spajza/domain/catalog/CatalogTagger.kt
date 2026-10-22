package sk.matej.spajza.domain.catalog

import sk.matej.spajza.domain.model.ComputedTag
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.nutrition.NutritionCalculator

/** Derives [ComputedTag]s for catalog recipes at import; these tags are never authored. */
class CatalogTagger(
    private val ingredientById: (String) -> Ingredient?,
    private val thresholds: TagThresholds = TagThresholds(),
) {
    private val calculator = NutritionCalculator(ingredientById)

    fun computeTags(recipe: Recipe): Set<ComputedTag> {
        // Diet tags look at every line, optional ones included, so a garnish cannot sneak in.
        val flags = recipe.ingredients.flatMap { ingredient(it.ingredientId).dietFlags }.toSet()
        val perServing = calculator.perServing(recipe)
        return buildSet {
            if (DietFlag.ANIMAL_PRODUCT !in flags) add(ComputedTag.VEGAN)
            if (DietFlag.MEAT !in flags && DietFlag.FISH !in flags) add(ComputedTag.VEGETARIAN)
            if (perServing.protein >= thresholds.highProteinMinGrams &&
                proteinEnergyShare(perServing.protein, perServing.kcal) >= thresholds.highProteinMinEnergyShare
            ) {
                add(ComputedTag.HIGH_PROTEIN)
            }
            if (perServing.kcal >= thresholds.bulkMinKcal && perServing.protein >= thresholds.bulkMinProtein) {
                add(ComputedTag.BULK)
            }
            if (perServing.kcal <= thresholds.lightMaxKcal && perServing.sugar <= thresholds.lightMaxSugar) {
                add(ComputedTag.LIGHT)
            }
            if (isCheap(recipe)) add(ComputedTag.CHEAP)
            if (recipe.totalTimeMin <= thresholds.quickMaxMinutes) add(ComputedTag.QUICK)
        }
    }

    private fun isCheap(recipe: Recipe): Boolean {
        val priced = recipe.ingredients.filter { !it.optional && it.role != IngredientRole.SEASONING }
        if (priced.isEmpty()) return false
        val tiers = priced.map { it.role.coverageWeight to ingredient(it.ingredientId).priceTier }
        if (tiers.any { (_, tier) -> tier == PriceTier.EXPENSIVE }) return false
        val average = tiers.sumOf { (weight, tier) -> weight * tier.score } / tiers.sumOf { (weight, _) -> weight }.toDouble()
        return average <= thresholds.cheapMaxAveragePrice
    }

    private fun proteinEnergyShare(protein: Double, kcal: Double): Double =
        if (kcal > 0) protein * KCAL_PER_GRAM_PROTEIN / kcal else 0.0

    private fun ingredient(id: String): Ingredient =
        requireNotNull(ingredientById(id)) { "Unknown ingredient '$id'" }

    private val PriceTier.score: Int
        get() = ordinal + 1

    private companion object {
        const val KCAL_PER_GRAM_PROTEIN = 4.0
    }
}
