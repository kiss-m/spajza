package sk.matej.spajza.domain.matching

import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.nutrition.Nutrition
import sk.matej.spajza.domain.testing.recipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PurchaseSuggesterTest {
    private val suggester = PurchaseSuggester()

    private fun match(id: String, score: Double, vararg missing: Pair<String, PriceTier>) = RecipeMatch(
        recipe = recipe(id = id, ingredients = emptyList()),
        score = score,
        coverage = 0.5,
        lines = emptyList(),
        missing = missing.map { (ingredient, tier) -> MissingIngredient(ingredient, tier) },
        perServing = Nutrition.ZERO,
        expiringItems = 0,
        breakdown = ScoreBreakdown(score, 0.0, 0.0, 0.0, 0.0, 0.0),
    )

    private val cheap = PriceTier.CHEAP
    private val mid = PriceTier.MID
    private val expensive = PriceTier.EXPENSIVE

    @Test
    fun `picks the item that unlocks the most`() {
        val toBuy = listOf(
            match("a", 100.0, "tomato" to mid),
            match("b", 100.0, "tomato" to mid),
            match("c", 100.0, "cream" to mid),
        )
        val suggestion = suggester.suggest(toBuy)
        assertEquals("tomato", suggestion.items.first().ingredientId)
        assertEquals(listOf("a", "b"), suggestion.items.first().unlocks)
    }

    @Test
    fun `combinations unlock recipes that need two items`() {
        val toBuy = listOf(
            match("pesto", 120.0, "basil" to mid, "pine-nuts" to expensive),
            match("caprese", 100.0, "basil" to mid, "mozzarella" to mid),
            match("toast", 60.0, "bread" to cheap),
        )
        val suggestion = suggester.suggest(toBuy)
        // bread unlocks toast right away (60); basil then brings two recipes closer; mozzarella
        // (100 / 1.25) beats expensive pine nuts (120 / 1.6) for the last pick.
        assertEquals(listOf("bread", "basil", "mozzarella"), suggestion.items.map { it.ingredientId })
        assertEquals(setOf("caprese", "toast"), suggestion.unlockedRecipeIds.toSet())
        assertEquals(listOf("caprese"), suggestion.items.last().unlocks)
    }

    @Test
    fun `a cheaper item wins when it unlocks as much`() {
        val toBuy = listOf(match("a", 100.0, "salmon" to expensive), match("b", 100.0, "lentils" to cheap))
        assertEquals("lentils", suggester.suggest(toBuy).items.first().ingredientId)
    }

    @Test
    fun `equal gains go to the cheaper item`() {
        val flat = PurchaseSuggester(PurchaseConfig(priceDivisor = emptyMap()))
        val toBuy = listOf(match("a", 100.0, "salmon" to expensive), match("b", 100.0, "lentils" to cheap))
        assertEquals(listOf("lentils", "salmon"), flat.suggest(toBuy).items.map { it.ingredientId })
    }

    @Test
    fun `price does not beat a much better gain`() {
        val toBuy = listOf(
            match("a", 100.0, "salmon" to expensive),
            match("b", 100.0, "salmon" to expensive),
            match("c", 100.0, "lentils" to cheap),
        )
        assertEquals("salmon", suggester.suggest(toBuy).items.first().ingredientId)
    }

    @Test
    fun `at most three items`() {
        val toBuy = (1..6).map { match("r$it", 100.0, "item$it" to cheap) }
        val suggestion = suggester.suggest(toBuy)
        assertEquals(3, suggestion.items.size)
        assertEquals(3, suggestion.unlockedRecipeIds.size)
    }

    @Test
    fun `nothing to buy gives an empty suggestion`() {
        assertTrue(suggester.suggest(emptyList()).items.isEmpty())
    }

    @Test
    fun `single recipe mode lists its missing items and what else they unlock`() {
        val target = match("carbonara", 100.0, "bacon" to mid, "parmesan" to expensive)
        val toBuy = listOf(target, match("bacon-eggs", 80.0, "bacon" to mid), match("risotto", 90.0, "rice" to cheap, "parmesan" to expensive))
        val suggestion = suggester.forRecipe(target, toBuy)
        assertEquals(listOf("bacon", "parmesan"), suggestion.items.map { it.ingredientId })
        assertEquals(listOf("carbonara", "bacon-eggs"), suggestion.unlockedRecipeIds)
    }
}
