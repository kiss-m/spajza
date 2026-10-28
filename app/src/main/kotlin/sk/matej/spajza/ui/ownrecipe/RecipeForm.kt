package sk.matej.spajza.ui.ownrecipe

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.own.OwnRecipes
import sk.matej.spajza.domain.own.OwnStep
import sk.matej.spajza.domain.own.RecipeDraft
import sk.matej.spajza.domain.own.RecipeLineMatcher
import sk.matej.spajza.domain.own.RecipeSource
import sk.matej.spajza.domain.own.StepTimers
import sk.matej.spajza.domain.unit.MeasureUnit
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.parseNumber
import sk.matej.spajza.ui.common.toOption

/**
 * The recipe editor's fields as typed: ingredients and steps one per line. Plain Kotlin, so it is
 * tested without Android.
 */
data class RecipeForm(
    val id: String,
    val title: String = "",
    val description: String = "",
    val servings: Int = OwnRecipe.DEFAULT_SERVINGS,
    val minutesText: String = OwnRecipe.DEFAULT_MINUTES.toString(),
    val mealTypes: Set<MealType> = OwnRecipe.DEFAULT_MEAL_TYPES,
    val ingredientsText: String = "",
    val stepsText: String = "",
    /** Ingredients picked by hand, by line text; null marks a line as not in the catalog. */
    val picks: Map<String, String?> = emptyMap(),
    val source: RecipeSource = RecipeSource.MANUAL,
    val sourceUrl: String? = null,
    /** The stored recipe being edited; keeps what the form does not show. */
    val base: OwnRecipe? = null,
) {
    val canSave: Boolean get() = title.isNotBlank()

    fun toRecipe(matcher: RecipeLineMatcher): OwnRecipe = (base ?: OwnRecipe(id = id, title = "")).copy(
        id = id,
        title = title.trim(),
        description = description.trim(),
        servings = servings.coerceIn(MIN_SERVINGS, MAX_SERVINGS),
        totalTimeMin = parseNumber(minutesText)?.toInt()?.takeIf { it > 0 } ?: OwnRecipe.DEFAULT_MINUTES,
        mealTypes = mealTypes.ifEmpty { OwnRecipe.DEFAULT_MEAL_TYPES },
        ingredients = matcher.lines(ingredientsText.lines()).map { line ->
            if (line.text in picks) line.copy(ingredientId = picks[line.text]) else line
        },
        steps = stepsText.lines().map(String::trim).filter { it.isNotEmpty() }.map { OwnStep(it, StepTimers.detect(it)) },
        source = source,
        sourceUrl = sourceUrl,
    )

    companion object {
        const val MIN_SERVINGS = 1
        const val MAX_SERVINGS = 12

        fun of(draft: RecipeDraft, id: String) = RecipeForm(
            id = id,
            title = draft.title,
            description = draft.description,
            servings = (draft.servings ?: OwnRecipe.DEFAULT_SERVINGS).coerceIn(MIN_SERVINGS, MAX_SERVINGS),
            minutesText = (draft.totalTimeMin ?: OwnRecipe.DEFAULT_MINUTES).toString(),
            mealTypes = draft.mealTypes.ifEmpty { OwnRecipe.DEFAULT_MEAL_TYPES },
            ingredientsText = draft.ingredientLines.joinToString("\n"),
            stepsText = draft.steps.joinToString("\n"),
            source = draft.source,
            sourceUrl = draft.sourceUrl,
        )

        fun of(recipe: OwnRecipe, matcher: RecipeLineMatcher): RecipeForm = RecipeForm(
            id = recipe.id,
            title = recipe.title,
            description = recipe.description,
            servings = recipe.servings,
            minutesText = recipe.totalTimeMin.toString(),
            mealTypes = recipe.mealTypes,
            ingredientsText = recipe.ingredients.joinToString("\n") { it.text },
            stepsText = recipe.steps.joinToString("\n") { it.text },
            // Lines matched by hand differ from what the matcher finds on its own.
            picks = recipe.ingredients.filter { matcher.match(it.text).ingredientId != it.ingredientId }.associate { it.text to it.ingredientId },
            source = recipe.source,
            sourceUrl = recipe.sourceUrl,
            base = recipe,
        )
    }
}

/** One ingredient line as the editor shows it: what it counts as and whether nutrition includes it. */
data class LinePreview(
    val text: String,
    val option: IngredientOption?,
    val amount: Double?,
    val unit: MeasureUnit?,
    val counted: Boolean,
)

object RecipePreview {
    fun lines(form: RecipeForm, matcher: RecipeLineMatcher, catalog: Catalog): List<LinePreview> {
        val recipe = form.toRecipe(matcher)
        val uncounted = OwnRecipes.uncounted(recipe, catalog).toSet()
        return recipe.ingredients.map { line ->
            LinePreview(
                text = line.text,
                option = line.ingredientId?.let { catalog.ingredientsById[it] }?.toOption(),
                amount = line.amount,
                unit = line.unit,
                counted = line !in uncounted,
            )
        }
    }
}
