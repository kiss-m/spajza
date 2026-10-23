package sk.matej.spajza.ui.recipe

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.matching.LineStatus
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.matching.MatcherFixture.MONDAY
import sk.matej.spajza.domain.matching.MatcherFixture.creamyChicken
import sk.matej.spajza.domain.matching.MatcherFixture.kitchen
import sk.matej.spajza.domain.matching.MatcherFixture.pantry
import sk.matej.spajza.domain.matching.MatcherFixture.taste
import sk.matej.spajza.domain.matching.MatcherFixture.tomatoPasta
import sk.matej.spajza.domain.matching.RecipeMatcher
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.rating.Verdict
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeDetailBuilderTest {
    private val catalog = MatcherFixture.catalog(tomatoPasta, creamyChicken)
    private val matcher = RecipeMatcher(catalog)

    @Test
    fun `lines scale with servings and report what the pantry has`() {
        val home = kitchen(pantry("pasta" to 50.0, "basil" to null))
        val match = matcher.evaluate(tomatoPasta, home, taste(), MONDAY, servings = 4)

        val state = RecipeDetailBuilder.build(catalog, match, Rating("tomato-pasta", Verdict.LIKE, favorite = true))

        assertEquals(4, state.servings)
        assertEquals(400.0, state.lines.first { it.ingredientId == "pasta" }.amount)
        assertEquals(LineStatus.PARTIAL, state.lines.first { it.ingredientId == "pasta" }.status)
        assertEquals(LineStatus.STAPLE, state.lines.first { it.ingredientId == "oil" }.status)
        // pasta is short, tomato and garlic are missing; optional basil is in the pantry.
        assertEquals(3, state.toBuyCount)
        assertEquals(listOf(LocalizedText("basil", "basil")), state.addOns)
        assertTrue(state.favorite)
        assertEquals(Verdict.LIKE, state.verdict)
    }

    @Test
    fun `missing ingredients list known substitutes`() {
        val home = kitchen(pantry("chicken" to null), staples = emptySet())
        val state = RecipeDetailBuilder.build(catalog, matcher.evaluate(creamyChicken, home, taste(), MONDAY), rating = null)

        val butter = state.lines.first { it.ingredientId == "butter" }
        assertEquals(LineStatus.MISSING, butter.status)
        assertEquals(listOf(LocalizedText("oil", "oil")), butter.alternatives)
    }
}
