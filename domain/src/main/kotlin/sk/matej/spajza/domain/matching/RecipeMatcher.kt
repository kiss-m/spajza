package sk.matej.spajza.domain.matching

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.diet.AllergenGuard
import sk.matej.spajza.domain.diet.DietFilter
import sk.matej.spajza.domain.model.ComputedTag
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.model.RecipeIngredient
import sk.matej.spajza.domain.model.RecipeTag
import sk.matej.spajza.domain.nutrition.NutritionCalculator
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.profile.Goal
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.SpicyTolerance
import sk.matej.spajza.domain.rating.Verdict
import sk.matej.spajza.domain.unit.UnitConverter
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Splits the catalog into recipes the household can cook now and recipes that need 1–3 more
 * ingredients, ranked for the user. See [MatcherConfig] for how the score is built.
 */
class RecipeMatcher(
    private val catalog: Catalog,
    private val config: MatcherConfig = MatcherConfig(),
) {
    private val calculator = NutritionCalculator(catalog.ingredientsById::get)
    private val features: Map<String, Set<String>> by lazy { catalog.recipes.associate { it.id to featuresOf(it) } }

    fun match(kitchen: KitchenState, personalization: Personalization, today: LocalDate): MatchResult {
        val context = Context(kitchen, personalization, today)
        val matches = catalog.recipes
            .filter { passesHardFilters(it, kitchen, personalization) }
            .map { evaluate(it, context) }
            .filter { it.missing.size <= config.maxMissingForToBuy }
            .sortedWith(compareByDescending<RecipeMatch> { it.score }.thenBy { it.recipe.id })
        return MatchResult(
            ready = matches.filter { it.missing.isEmpty() },
            toBuy = matches.filter { it.missing.isNotEmpty() },
        )
    }

    /**
     * Evaluates a single recipe however many items are missing (recipe detail, search results), for
     * [servings] or, when null, the user's default for this recipe ([defaultServings]).
     */
    fun evaluate(
        recipe: Recipe,
        kitchen: KitchenState,
        personalization: Personalization,
        today: LocalDate,
        servings: Int? = null,
    ): RecipeMatch = evaluate(recipe, Context(kitchen, personalization, today), servings)

    /** Meal-prep recipes are cooked whole when the user meal-preps; everything else for the default servings. */
    fun defaultServings(recipe: Recipe, preferences: Preferences): Int =
        if (preferences.mealPrep && RecipeTag.MEAL_PREP in recipe.tags) recipe.servings else preferences.defaultServings

    fun passesHardFilters(recipe: Recipe, kitchen: KitchenState, personalization: Personalization): Boolean {
        val disliked = personalization.preferences.dislikedIngredients
        val restrictions = personalization.restrictions
        return kitchen.equipment.containsAll(recipe.equipment) &&
            personalization.ratings[recipe.id]?.verdict != Verdict.DISLIKE &&
            recipe.ingredients.none { !it.optional && it.role != IngredientRole.SEASONING && it.ingredientId in disliked } &&
            DietFilter.allows(recipe, catalog.ingredientsById::get, restrictions.diets) &&
            AllergenGuard.isSafe(recipe, catalog.ingredientsById::get, restrictions.allergens)
    }

    private inner class Context(val kitchen: KitchenState, val personalization: Personalization, val today: LocalDate) {
        val pantryById: Map<String, PantryItem> = kitchen.pantry
            .filter { it.quantity == null || it.quantity > 0 }
            .mapNotNull { item -> item.catalogId?.let { it to item } }
            .toMap()

        /** Liked, favorite and cooked recipes with their weight in the taste profile. */
        val tasteSources: Map<String, Double> = buildMap<String, Double> {
            personalization.ratings.values.forEach { rating ->
                if (rating.verdict == Verdict.DISLIKE) return@forEach
                var weight = 0.0
                if (rating.verdict == Verdict.LIKE) weight += config.likeSourceWeight
                if (rating.favorite) weight += config.favoriteSourceWeight
                if (weight > 0) merge(rating.recipeId, weight, Double::plus)
            }
            personalization.cookHistory.forEach { merge(it.recipeId, config.cookEventSourceWeight, Double::plus) }
            personalization.ratings.values.filter { it.verdict == Verdict.DISLIKE }.forEach { remove(it.recipeId) }
        }

        val isWeekend: Boolean = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
    }

    private fun evaluate(recipe: Recipe, ctx: Context, requestedServings: Int? = null): RecipeMatch {
        val servings = requestedServings ?: defaultServings(recipe, ctx.personalization.preferences)
        require(servings > 0) { "servings must be positive, was $servings" }
        val factor = servings.toDouble() / recipe.servings
        val lines = recipe.ingredients.map { evaluateLine(it, factor, ctx) }
        val required = lines.filterNot { it.line.optional }

        val totalWeight = required.sumOf { it.line.role.coverageWeight }
        val coverage = if (totalWeight == 0) 1.0 else required.sumOf { it.line.role.coverageWeight * it.availability } / totalWeight
        val missing = required.filter { it.status == LineStatus.MISSING }
            .map { MissingIngredient(it.line.ingredientId, ingredient(it.line.ingredientId).priceTier) }
        val expiring = required.count { it.expiresSoon }
        val perServing = calculator.perServing(recipe)

        val breakdown = ScoreBreakdown(
            coverage = config.coverageWeight * coverage,
            taste = config.tasteWeight * tasteComponent(recipe, ctx),
            macros = config.macroWeight * macroComponent(perServing.kcal, perServing.proteinPer100Kcal, ctx),
            time = config.timeWeight * timeComponent(recipe, ctx),
            expiry = config.expiryWeight * minOf(1.0, expiring * config.expiryPerItem),
            adjustments = adjustments(recipe, ctx),
        )
        return RecipeMatch(recipe, breakdown.total, coverage, lines, missing, perServing, expiring, breakdown, servings)
    }

    private fun evaluateLine(line: RecipeIngredient, factor: Double, ctx: Context): LineMatch {
        val id = line.ingredientId
        if (id in ctx.kitchen.staples) return LineMatch(line, LineStatus.STAPLE, 1.0)
        val needed = line.grams * factor
        ctx.pantryById[id]?.let { item ->
            val ratio = sufficiency(item, needed, ingredient(id))
            return LineMatch(
                line,
                if (ratio >= 1.0) LineStatus.HAVE else LineStatus.PARTIAL,
                ratio,
                expiresSoon = isExpiringSoon(item, ctx.today),
            )
        }
        val substitute = catalog.substitutesFor[id].orEmpty()
            .mapNotNull { sub ->
                when {
                    sub.substituteId in ctx.kitchen.staples -> Triple(sub.substituteId, 1.0, null)
                    else -> ctx.pantryById[sub.substituteId]?.let { item ->
                        Triple(sub.substituteId, sufficiency(item, needed * sub.ratio, ingredient(sub.substituteId)), item)
                    }
                }
            }
            .maxByOrNull { it.second }
        if (substitute != null) {
            val (substituteId, ratio, item) = substitute
            return LineMatch(
                line,
                LineStatus.SUBSTITUTE,
                config.substituteAvailability * ratio,
                substituteId = substituteId,
                expiresSoon = item != null && isExpiringSoon(item, ctx.today),
            )
        }
        return LineMatch(line, LineStatus.MISSING, 0.0)
    }

    /** Share of the needed grams the pantry item covers; untracked quantities count as enough. */
    private fun sufficiency(item: PantryItem, neededGrams: Double, ingredient: Ingredient): Double {
        val quantity = item.quantity ?: return 1.0
        if (neededGrams <= 0) return 1.0
        return minOf(1.0, UnitConverter.pantryUnitToGrams(quantity, ingredient) / neededGrams)
    }

    private fun isExpiringSoon(item: PantryItem, today: LocalDate): Boolean {
        val expires = item.expiresAt ?: return false
        return !expires.isBefore(today) && !expires.isAfter(today.plusDays(config.expiringWithinDays))
    }

    private fun tasteComponent(recipe: Recipe, ctx: Context): Double {
        val preferences = ctx.personalization.preferences
        val rating = ctx.personalization.ratings[recipe.id]
        val direct = if (rating?.verdict == Verdict.LIKE || rating?.favorite == true) 1.0 else 0.0
        val own = features.getValue(recipe.id)
        val similarity = ctx.tasteSources
            .filterKeys { it != recipe.id && it in features }
            .maxOfOrNull { (sourceId, weight) -> jaccard(own, features.getValue(sourceId)) * minOf(1.0, weight) }
            ?: 0.0
        val likedIngredients = recipe.ingredients.count {
            !it.optional && it.role != IngredientRole.SEASONING && it.ingredientId in preferences.likedIngredients
        }
        val declared = (if (recipe.cuisine in preferences.likedCuisines) config.likedCuisineBonus else 0.0) +
            minOf(config.likedIngredientBonusCap, likedIngredients * config.likedIngredientBonus)
        return (maxOf(direct, config.similarityShare * similarity) + declared).coerceIn(0.0, 1.0)
    }

    private fun macroComponent(kcal: Double, proteinPer100Kcal: Double, ctx: Context): Double {
        val protein = (proteinPer100Kcal / config.proteinPer100KcalReference).coerceIn(0.0, 1.0)
        val reference = (ctx.personalization.dailyKcal ?: config.defaultDailyKcal) * config.mealShareOfDailyTarget
        val ratio = kcal / reference
        val energy = when (ctx.personalization.goal) {
            Goal.LOSE -> 1.0 - (ratio - config.loseFullUpTo) / (config.loseZeroAt - config.loseFullUpTo)
            Goal.GAIN -> (ratio - config.gainZeroAt) / (config.gainFullFrom - config.gainZeroAt)
            Goal.MAINTAIN -> 1.0 - kotlin.math.abs(ratio - 1.0) / config.maintainTolerance
        }.coerceIn(0.0, 1.0)
        return 0.5 * protein + 0.5 * energy
    }

    private fun timeComponent(recipe: Recipe, ctx: Context): Double {
        val preferences = ctx.personalization.preferences
        val limit = (if (ctx.isWeekend) preferences.maxTimeWeekendMin else preferences.maxTimeWeekdayMin).coerceAtLeast(1).toDouble()
        val minutes = recipe.totalTimeMin.toDouble()
        return if (minutes <= limit) 1.0 - 0.5 * minutes / limit else -minOf(1.0, (minutes - limit) / limit)
    }

    private fun adjustments(recipe: Recipe, ctx: Context): Double {
        val preferences = ctx.personalization.preferences
        val required = recipe.ingredients.filterNot { it.optional }
        var points = 0.0

        val overSkill = recipe.difficulty - preferences.cookingSkill.comfortableDifficulty
        if (overSkill > 0) points -= overSkill * config.skillPenaltyPerLevel

        if (required.any { ingredient(it.ingredientId).spicy }) {
            points += when (preferences.spicyTolerance) {
                SpicyTolerance.NONE -> -config.spicyPenaltyNone
                SpicyTolerance.MILD -> -config.spicyPenaltyMild
                SpicyTolerance.HOT -> config.spicyBonusHot
            }
        }

        val hasExpensive = required.any { it.role != IngredientRole.SEASONING && ingredient(it.ingredientId).priceTier == PriceTier.EXPENSIVE }
        points += when (preferences.budget) {
            PriceTier.CHEAP ->
                (if (ComputedTag.CHEAP in recipe.computedTags) config.budgetPoints else 0.0) -
                    (if (hasExpensive) config.budgetPoints else 0.0)
            PriceTier.MID -> if (hasExpensive) -config.budgetPointsMid else 0.0
            PriceTier.EXPENSIVE -> 0.0
        }

        if (preferences.mealPrep && RecipeTag.MEAL_PREP in recipe.tags) points += config.mealPrepBonus
        return points
    }

    private fun featuresOf(recipe: Recipe): Set<String> = buildSet {
        add("cuisine:${recipe.cuisine}")
        recipe.tags.forEach { add("tag:$it") }
        recipe.computedTags.filter { it in SIMILARITY_TAGS }.forEach { add("tag:$it") }
        recipe.ingredients.filter { !it.optional && it.role == IngredientRole.MAIN }.forEach { add("main:${it.ingredientId}") }
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 0.0
        return (a intersect b).size.toDouble() / (a union b).size
    }

    private fun ingredient(id: String): Ingredient = catalog.ingredient(id)

    private companion object {
        val SIMILARITY_TAGS = setOf(
            ComputedTag.VEGAN,
            ComputedTag.VEGETARIAN,
            ComputedTag.HIGH_PROTEIN,
            ComputedTag.BULK,
            ComputedTag.LIGHT,
        )
    }
}
