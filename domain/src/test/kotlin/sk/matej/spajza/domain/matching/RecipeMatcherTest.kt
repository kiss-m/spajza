package sk.matej.spajza.domain.matching

import sk.matej.spajza.domain.diet.Diet
import sk.matej.spajza.domain.diet.DietRestrictions
import sk.matej.spajza.domain.matching.MatcherFixture.MONDAY
import sk.matej.spajza.domain.matching.MatcherFixture.SATURDAY
import sk.matej.spajza.domain.matching.MatcherFixture.catalog
import sk.matej.spajza.domain.matching.MatcherFixture.chickenRice
import sk.matej.spajza.domain.matching.MatcherFixture.creamyChicken
import sk.matej.spajza.domain.matching.MatcherFixture.garlicPasta
import sk.matej.spajza.domain.matching.MatcherFixture.kitchen
import sk.matej.spajza.domain.matching.MatcherFixture.ovenPasta
import sk.matej.spajza.domain.matching.MatcherFixture.pantry
import sk.matej.spajza.domain.matching.MatcherFixture.peanutRice
import sk.matej.spajza.domain.matching.MatcherFixture.salmonRice
import sk.matej.spajza.domain.matching.MatcherFixture.spicyPasta
import sk.matej.spajza.domain.matching.MatcherFixture.taste
import sk.matej.spajza.domain.matching.MatcherFixture.tomatoPasta
import sk.matej.spajza.domain.matching.MatcherFixture.bigFeast
import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.model.RecipeTag
import sk.matej.spajza.domain.profile.Goal
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.SpicyTolerance
import sk.matej.spajza.domain.rating.CookEvent
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.rating.Verdict
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeMatcherTest {
    private fun ids(matches: List<RecipeMatch>) = matches.map { it.recipe.id }

    @Test
    fun `recipe with everything at home is ready, staples count as available`() {
        val matcher = RecipeMatcher(catalog(garlicPasta))
        val result = matcher.match(kitchen(pantry("pasta" to null, "garlic" to null)), taste(), MONDAY)
        assertEquals(listOf("garlic-pasta"), ids(result.ready))
        val match = result.ready.single()
        assertEquals(1.0, match.coverage, 1e-9)
        assertEquals(LineStatus.STAPLE, match.lines.first { it.line.ingredientId == "oil" }.status)
    }

    @Test
    fun `one to three missing items go to the to-buy section, more are hidden`() {
        val matcher = RecipeMatcher(catalog(tomatoPasta, bigFeast))
        val result = matcher.match(kitchen(pantry("pasta" to null)), taste(), MONDAY)
        assertEquals(emptyList(), ids(result.ready))
        assertEquals(listOf("tomato-pasta"), ids(result.toBuy))
        val missing = result.toBuy.single().missing
        assertEquals(listOf("tomato", "garlic"), missing.map { it.ingredientId })
        assertEquals(listOf(PriceTier.MID, PriceTier.CHEAP), missing.map { it.priceTier })
    }

    @Test
    fun `missing optional ingredients do not count`() {
        val result = RecipeMatcher(catalog(tomatoPasta)).match(kitchen(pantry("pasta" to null, "tomato" to null, "garlic" to null)), taste(), MONDAY)
        assertEquals(listOf("tomato-pasta"), ids(result.ready))
        assertEquals(LineStatus.MISSING, result.ready.single().lines.first { it.line.ingredientId == "basil" }.status)
    }

    @Test
    fun `substitute in the pantry counts 0_8 and is not missing`() {
        // creamy-chicken needs butter (SECONDARY 2); oil substitutes for it.
        val home = pantry("chicken" to null, "cream" to null, "onion" to null, "pasta" to null)
        val match = RecipeMatcher(catalog(creamyChicken)).match(kitchen(home), taste(), MONDAY).ready.single()
        val butter = match.lines.first { it.line.ingredientId == "butter" }
        assertEquals(LineStatus.SUBSTITUTE, butter.status)
        assertEquals("oil", butter.substituteId)
        // weights: chicken 3, cream 2, butter 2 × 0.8, onion 2, pasta 3 -> 11.6 / 12
        assertEquals(11.6 / 12, match.coverage, 1e-9)
    }

    @Test
    fun `smaller pantry quantity counts as partial for one default serving`() {
        // Default servings = 1 of a 2-serving recipe: needs 100 g pasta and 1 tomato.
        val enough = RecipeMatcher(catalog(tomatoPasta)).match(kitchen(pantry("pasta" to 150.0, "tomato" to 1.0, "garlic" to null)), taste(), MONDAY)
        assertEquals(1.0, enough.ready.single().coverage, 1e-9)
        val partial = RecipeMatcher(catalog(tomatoPasta)).match(kitchen(pantry("pasta" to 50.0, "tomato" to 1.0, "garlic" to null)), taste(), MONDAY)
        val match = partial.ready.single()
        val pasta = match.lines.first { it.line.ingredientId == "pasta" }
        assertEquals(LineStatus.PARTIAL, pasta.status)
        assertEquals(0.5, pasta.availability, 1e-9)
        assertTrue(match.coverage < 1.0)
    }

    @Test
    fun `meal prep recipes need the full amount when meal prep is on`() {
        val prep = tomatoPasta.copy(id = "prep-pasta", tags = setOf(RecipeTag.MEAL_PREP))
        val home = kitchen(pantry("pasta" to 150.0, "tomato" to 2.0, "garlic" to null))
        val withPrep = RecipeMatcher(catalog(prep)).match(home, taste(), MONDAY).ready.single()
        assertEquals(LineStatus.PARTIAL, withPrep.lines.first { it.line.ingredientId == "pasta" }.status)
        val withoutPrep = RecipeMatcher(catalog(prep)).match(home, taste(Preferences(mealPrep = false)), MONDAY).ready.single()
        assertEquals(LineStatus.HAVE, withoutPrep.lines.first { it.line.ingredientId == "pasta" }.status)
    }

    @Test
    fun `hard filters remove recipes`() {
        val all = catalog(tomatoPasta, ovenPasta, chickenRice, garlicPasta, peanutRice)
        val home = kitchen(
            pantry("pasta" to null, "tomato" to null, "garlic" to null, "chicken" to null, "rice" to null, "onion" to null, "peanuts" to null),
            equipment = setOf(Equipment.STOVE),
        )
        val result = RecipeMatcher(all).match(
            home,
            taste(
                preferences = Preferences(dislikedIngredients = setOf("chicken")),
                ratings = mapOf("garlic-pasta" to Rating("garlic-pasta", verdict = Verdict.DISLIKE)),
                restrictions = DietRestrictions(allergens = setOf(Allergen.PEANUTS)),
            ),
            MONDAY,
        )
        // oven-pasta: no oven; chicken-rice: disliked main ingredient; garlic-pasta: disliked; peanut-rice: allergen.
        assertEquals(listOf("tomato-pasta"), ids(result.ready + result.toBuy))
    }

    @Test
    fun `disliked seasoning does not remove a recipe`() {
        val result = RecipeMatcher(catalog(tomatoPasta)).match(
            kitchen(pantry("pasta" to null, "tomato" to null, "garlic" to null)),
            taste(Preferences(dislikedIngredients = setOf("garlic"))),
            MONDAY,
        )
        assertEquals(listOf("tomato-pasta"), ids(result.ready))
    }

    @Test
    fun `diet restriction removes recipes`() {
        val home = kitchen(pantry("chicken" to null, "rice" to null, "onion" to null, "salmon" to null, "garlic" to null))
        val result = RecipeMatcher(catalog(chickenRice, salmonRice)).match(home, taste(restrictions = DietRestrictions(diets = setOf(Diet.PESCATARIAN))), MONDAY)
        assertEquals(listOf("salmon-rice"), ids(result.ready))
    }

    @Test
    fun `liked recipe ranks above an otherwise equal one`() {
        val twin = garlicPasta.copy(id = "garlic-pasta-2")
        val home = kitchen(pantry("pasta" to null, "garlic" to null))
        val result = RecipeMatcher(catalog(garlicPasta, twin)).match(
            home,
            taste(ratings = mapOf("garlic-pasta-2" to Rating("garlic-pasta-2", verdict = Verdict.LIKE))),
            MONDAY,
        )
        assertEquals(listOf("garlic-pasta-2", "garlic-pasta"), ids(result.ready))
    }

    @Test
    fun `recipes similar to liked or cooked ones rank higher`() {
        // Same coverage; the user cooked an Italian pasta recipe several times.
        val home = kitchen(pantry("pasta" to null, "garlic" to null, "rice" to null, "peanuts" to null))
        val history = (1..3).map { CookEvent("e$it", "tomato-pasta", Instant.EPOCH, 2) }
        val result = RecipeMatcher(catalog(garlicPasta, peanutRice, tomatoPasta)).match(home, taste(history = history), MONDAY)
        val ready = ids(result.ready)
        assertTrue(ready.indexOf("garlic-pasta") < ready.indexOf("peanut-rice"), "ranking was $ready")
    }

    @Test
    fun `goal shifts the macro preference`() {
        val light = garlicPasta.copy(id = "light", ingredients = garlicPasta.ingredients.map { if (it.ingredientId == "pasta") it.copy(grams = 100.0) else it })
        val heavy = garlicPasta.copy(id = "heavy", ingredients = garlicPasta.ingredients.map { if (it.ingredientId == "pasta") it.copy(grams = 400.0) else it })
        val home = kitchen(pantry("pasta" to null, "garlic" to null))
        val matcher = RecipeMatcher(catalog(light, heavy))
        assertEquals(listOf("light", "heavy"), ids(matcher.match(home, taste(goal = Goal.LOSE, dailyKcal = 1800), MONDAY).ready))
        assertEquals(listOf("heavy", "light"), ids(matcher.match(home, taste(goal = Goal.GAIN, dailyKcal = 3200), MONDAY).ready))
    }

    @Test
    fun `quicker recipes rank higher and the weekend limit applies on weekends`() {
        val quick = garlicPasta.copy(id = "quick", totalTimeMin = 15)
        val slow = garlicPasta.copy(id = "slow", totalTimeMin = 50)
        val home = kitchen(pantry("pasta" to null, "garlic" to null))
        val matcher = RecipeMatcher(catalog(slow, quick))
        val monday = matcher.match(home, taste(), MONDAY).ready
        assertEquals(listOf("quick", "slow"), ids(monday))
        val saturday = matcher.match(home, taste(), SATURDAY).ready
        val slowGap = { r: List<RecipeMatch> -> r.first { it.recipe.id == "quick" }.score - r.first { it.recipe.id == "slow" }.score }
        assertTrue(slowGap(saturday) < slowGap(monday), "a 50-minute recipe is within the 60-minute weekend limit")
    }

    @Test
    fun `using items that expire soon earns a bonus`() {
        val twin = garlicPasta.copy(id = "garlic-pasta-2", ingredients = garlicPasta.ingredients.map {
            if (it.ingredientId == "garlic") it.copy(ingredientId = "onion") else it
        })
        val home = kitchen(pantry("pasta" to null, "garlic" to null, "onion" to null, expiring = mapOf("onion" to MONDAY.plusDays(2))))
        val result = RecipeMatcher(catalog(garlicPasta, twin)).match(home, taste(), MONDAY)
        assertEquals(listOf("garlic-pasta-2", "garlic-pasta"), ids(result.ready))
        assertEquals(1, result.ready.first().expiringItems)
    }

    @Test
    fun `items expiring later or already expired earn no bonus`() {
        val home = kitchen(pantry("pasta" to null, "garlic" to null, expiring = mapOf("garlic" to MONDAY.plusDays(4), "pasta" to MONDAY.minusDays(1))))
        assertEquals(0, RecipeMatcher(catalog(garlicPasta)).match(home, taste(), MONDAY).ready.single().expiringItems)
    }

    @Test
    fun `spicy recipes rank lower for people who avoid spice`() {
        val home = kitchen(pantry("pasta" to null, "garlic" to null, "chili" to null))
        val matcher = RecipeMatcher(catalog(spicyPasta, garlicPasta))
        val none = matcher.match(home, taste(Preferences(spicyTolerance = SpicyTolerance.NONE)), MONDAY).ready
        assertEquals("garlic-pasta", none.first().recipe.id)
        val hot = matcher.match(home, taste(Preferences(spicyTolerance = SpicyTolerance.HOT)), MONDAY).ready
        val score = { r: List<RecipeMatch>, id: String -> r.first { it.recipe.id == id }.score }
        assertTrue(score(hot, "spicy-pasta") > score(none, "spicy-pasta"))
    }

    @Test
    fun `per serving nutrition is reported`() {
        val match = RecipeMatcher(catalog(garlicPasta)).match(kitchen(pantry("pasta" to null, "garlic" to null)), taste(), MONDAY).ready.single()
        // (200 × 3.6 + 20 × 1.49 + 40 × 8.84) / 2
        assertEquals((720.0 + 29.8 + 353.6) / 2, match.perServing.kcal, 1e-9)
    }
}
