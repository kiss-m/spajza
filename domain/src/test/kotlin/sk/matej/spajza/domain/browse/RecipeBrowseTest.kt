package sk.matej.spajza.domain.browse

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.ComputedTag
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.model.RecipeTag
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.line
import sk.matej.spajza.domain.testing.recipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeBrowseTest {
    private val goulash = recipe("gulas", listOf(line("beef", 500.0), line("onion", 200.0)), cuisine = Cuisine.SLOVAK_CZECH, mealTypes = setOf(MealType.LUNCH))
        .copy(title = LocalizedText("Hovädzí guláš", "Beef goulash"), tags = setOf(RecipeTag.ONE_POT), computedTags = setOf(ComputedTag.HIGH_PROTEIN))
    private val dal = recipe("dal", listOf(line("lentils", 200.0), line("onion", 100.0)), cuisine = Cuisine.INDIAN, mealTypes = setOf(MealType.LUNCH, MealType.DINNER))
        .copy(title = LocalizedText("Šošovicový dál", "Lentil dal"), tags = setOf(RecipeTag.STUDENT), computedTags = setOf(ComputedTag.VEGAN, ComputedTag.VEGETARIAN, ComputedTag.CHEAP))
    private val oats = recipe("ovsena-kasa", listOf(line("oats", 60.0)), cuisine = Cuisine.INTERNATIONAL, mealTypes = setOf(MealType.BREAKFAST))
        .copy(title = LocalizedText("Ovsená kaša", "Porridge"), computedTags = setOf(ComputedTag.VEGETARIAN, ComputedTag.QUICK, ComputedTag.CHEAP))

    private val catalog = Catalog(
        ingredients = listOf(
            ingredient("beef").copy(name = LocalizedText("hovädzie mäso", "beef"), synonyms = listOf("hovädzina")),
            ingredient("onion").copy(name = LocalizedText("cibuľa", "onion"), synonyms = listOf("cibule", "cibuľu")),
            ingredient("lentils").copy(name = LocalizedText("šošovica", "lentils"), synonyms = listOf("čočka")),
            ingredient("oats").copy(name = LocalizedText("ovsené vločky", "rolled oats")),
        ),
        recipes = listOf(goulash, dal, oats),
        substitutions = emptyList(),
    )

    private fun ids(filter: FeedFilter) = catalog.recipes.filter(filter::matches).map { it.id }

    @Test
    fun `empty filter keeps everything`() {
        assertEquals(listOf("gulas", "dal", "ovsena-kasa"), ids(FeedFilter()))
        assertTrue(FeedFilter().isEmpty)
    }

    @Test
    fun `cuisines are alternatives, types must all apply`() {
        assertEquals(listOf("gulas", "dal"), ids(FeedFilter(cuisines = setOf(Cuisine.SLOVAK_CZECH, Cuisine.INDIAN))))
        assertEquals(listOf("dal", "ovsena-kasa"), ids(FeedFilter(types = setOf(RecipeType.VEGETARIAN, RecipeType.CHEAP))))
        assertEquals(listOf("dal"), ids(FeedFilter(types = setOf(RecipeType.VEGETARIAN, RecipeType.STUDENT))))
        assertEquals(emptyList(), ids(FeedFilter(cuisines = setOf(Cuisine.SLOVAK_CZECH), types = setOf(RecipeType.VEGAN))))
    }

    @Test
    fun `the meal type tab is not a chip`() {
        assertTrue(FeedFilter(types = setOf(RecipeType.VEGAN)).hasChips)
        assertTrue(FeedFilter(cuisines = setOf(Cuisine.THAI)).hasChips)
        assertFalse(FeedFilter(mealType = MealType.LUNCH).hasChips)
    }

    @Test
    fun `meal type filter`() {
        assertEquals(listOf("ovsena-kasa"), ids(FeedFilter(mealType = MealType.BREAKFAST)))
        assertEquals(listOf("gulas", "dal"), ids(FeedFilter(mealType = MealType.LUNCH)))
    }

    @Test
    fun `every type chip maps to a tag`() {
        RecipeType.entries.forEach { type -> assertTrue(type.tag != null || type.computedTag != null, "$type has no tag") }
    }

    @Test
    fun `search matches titles in both languages without diacritics`() {
        val search = RecipeSearch(catalog)
        assertEquals(listOf("gulas"), search.search("gulas").map { it.id })
        assertEquals(listOf("gulas"), search.search("goulash").map { it.id })
        assertEquals(listOf("dal"), search.search("SOSOV").map { it.id })
        assertEquals(emptyList(), search.search("   ").map { it.id })
    }

    @Test
    fun `search finds recipes by ingredient names and synonyms, title hits first`() {
        val search = RecipeSearch(catalog)
        // "cibul" is in no title: both onion recipes, in catalog order.
        assertEquals(listOf("gulas", "dal"), search.search("cibuľ").map { it.id })
        assertEquals(listOf("dal"), search.search("čočka").map { it.id })
        // Every word must match somewhere: "dal" in the title plus "onion" as an ingredient.
        assertEquals(listOf("dal"), search.search("dal onion").map { it.id })
        // "o" starts a title word only in "Ovsená kaša"; the others match through "onion".
        assertEquals(listOf("ovsena-kasa", "gulas", "dal"), search.search("o").map { it.id })
    }
}
