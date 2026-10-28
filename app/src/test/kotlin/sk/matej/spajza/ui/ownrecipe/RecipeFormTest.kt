package sk.matej.spajza.ui.ownrecipe

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.own.RecipeDraft
import sk.matej.spajza.domain.own.RecipeLineMatcher
import sk.matej.spajza.domain.own.RecipeSource
import sk.matej.spajza.domain.unit.MeasureUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeFormTest {
    private val catalog = MatcherFixture.catalog()
    private val matcher = RecipeLineMatcher(catalog)

    private val form = RecipeForm(
        id = "r1",
        title = "  Pasta for two ",
        servings = 2,
        minutesText = "25",
        ingredientsText = "200 g pasta\n\n• 2 tomato\na pinch of love",
        stepsText = "Boil the pasta for 10 minutes.\n\n  Serve at once.  ",
    )

    @Test
    fun `typed lines become matched ingredients and steps with timers`() {
        val recipe = form.toRecipe(matcher)

        assertEquals("Pasta for two", recipe.title)
        assertEquals(25, recipe.totalTimeMin)
        assertEquals(listOf("200 g pasta", "2 tomato", "a pinch of love"), recipe.ingredients.map { it.text })
        assertEquals(listOf("pasta", "tomato", null), recipe.ingredients.map { it.ingredientId })
        assertEquals(MeasureUnit.G, recipe.ingredients[0].unit)
        assertEquals(listOf("Boil the pasta for 10 minutes.", "Serve at once."), recipe.steps.map { it.text })
        assertEquals(listOf(600, null), recipe.steps.map { it.timerSeconds })
    }

    @Test
    fun `picks by hand win over the matcher and survive editing again`() {
        val picked = form.copy(picks = mapOf("a pinch of love" to "basil", "2 tomato" to null))
        val recipe = picked.toRecipe(matcher)
        assertEquals(listOf("pasta", null, "basil"), recipe.ingredients.map { it.ingredientId })

        val again = RecipeForm.of(recipe, matcher)
        assertEquals(mapOf("a pinch of love" to "basil", "2 tomato" to null), again.picks)
        assertEquals(recipe, again.toRecipe(matcher))
    }

    @Test
    fun `a draft fills the form and empty fields fall back to defaults`() {
        val draft = RecipeDraft(
            title = "Guláš",
            servings = 40,
            ingredientLines = listOf("1 kg mäsa", "3 cibule"),
            steps = listOf("Opeč.", "Duste."),
            source = RecipeSource.URL,
            sourceUrl = "https://example.com",
        )
        val filled = RecipeForm.of(draft, "new")

        assertEquals(RecipeForm.MAX_SERVINGS, filled.servings)
        assertEquals("1 kg mäsa\n3 cibule", filled.ingredientsText)
        assertEquals("Opeč.\nDuste.", filled.stepsText)
        assertEquals(OwnRecipe.DEFAULT_MEAL_TYPES, filled.mealTypes)
        assertEquals(RecipeSource.URL, filled.toRecipe(matcher).source)
        assertEquals(OwnRecipe.DEFAULT_MINUTES, filled.copy(minutesText = "").toRecipe(matcher).totalTimeMin)
        assertEquals(OwnRecipe.DEFAULT_MEAL_TYPES, filled.copy(mealTypes = emptySet()).toRecipe(matcher).mealTypes)
        assertEquals(setOf(MealType.DESSERT), RecipeForm.of(draft.copy(mealTypes = setOf(MealType.DESSERT)), "x").mealTypes)
    }

    @Test
    fun `the preview tells which lines count`() {
        val preview = RecipePreview.lines(form, matcher, catalog)

        assertEquals(listOf(true, true, false), preview.map { it.counted })
        assertEquals("pasta", preview[0].option?.ingredientId)
        assertNull(preview[2].option)
        assertTrue(form.canSave)
        assertFalse(form.copy(title = " ").canSave)
    }
}
