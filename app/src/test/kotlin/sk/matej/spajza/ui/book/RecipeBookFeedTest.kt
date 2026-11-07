package sk.matej.spajza.ui.book

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.book.BookChapter
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Course
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.line
import sk.matej.spajza.domain.testing.nutrition
import sk.matej.spajza.domain.testing.recipe
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeBookFeedTest {
    private fun named(id: String, sk: String, en: String, course: Course, vararg lines: Pair<String, Double>, servings: Int = 1): Recipe =
        recipe(id = id, ingredients = lines.map { (ingredient, grams) -> line(ingredient, grams) }, servings = servings, course = course)
            .copy(title = LocalizedText(sk, en))

    private val catalog = Catalog(
        ingredients = listOf(
            ingredient("chicken-thigh", nutrition(kcal = 120.0), category = IngredientCategory.MEAT, dietFlags = setOf(DietFlag.MEAT)),
            ingredient("potato", nutrition(kcal = 80.0), category = IngredientCategory.VEGETABLES),
            ingredient("chocolate", nutrition(kcal = 550.0), category = IngredientCategory.BAKING),
        ),
        recipes = listOf(
            named("cicer", "Čínske kura", "Chinese chicken", Course.MAIN, "chicken-thigh" to 400.0, servings = 2),
            named("cuketa", "Cuketové kura", "Courgette chicken", Course.MAIN, "chicken-thigh" to 300.0),
            named("halusky", "Halušky", "Dumplings", Course.MAIN, "potato" to 500.0),
            named("chlieb", "Chlebová polievka", "Bread soup", Course.SOUP, "potato" to 200.0),
            named("brownies", "Brownies", "Brownies", Course.DESSERT, "chocolate" to 100.0),
            named("own:x1", "Babkine zemiaky", "Babkine zemiaky", Course.SIDE, "potato" to 300.0),
        ),
        substitutions = emptyList(),
    )
    private val feed = RecipeBookFeed(catalog)

    @Test
    fun `favorites and own recipes open the book, then the chapters in cookbook order`() {
        val state = feed.state(mapOf("brownies" to Rating("brownies", favorite = true), "halusky" to Rating("halusky")), "", Language.SK)

        assertFalse(state.isLoading)
        assertFalse(state.searching)
        assertEquals(
            listOf(BookChapter.FAVORITES, BookChapter.OWN, BookChapter.SOUP, BookChapter.POULTRY, BookChapter.MEATLESS, BookChapter.SIDE, BookChapter.DESSERT),
            state.sections.map { it.chapter },
        )
        assertEquals(listOf("brownies"), state.sections[0].recipes.map { it.recipeId })
        assertTrue(state.sections.single { it.chapter == BookChapter.DESSERT }.recipes.single().favorite, "a favorite also stays in its chapter")
        assertEquals(listOf("own:x1"), state.sections[1].recipes.map { it.recipeId })
    }

    @Test
    fun `titles follow the alphabet of the app language`() {
        fun poultry(language: Language) =
            feed.state(emptyMap(), "", language).sections.single { it.chapter == BookChapter.POULTRY }.recipes.map { it.recipeId }

        assertEquals(listOf("cuketa", "cicer"), poultry(Language.SK), "in Slovak č comes after c")
        assertEquals(listOf("cicer", "cuketa"), poultry(Language.EN), "Chinese before Courgette")
    }

    @Test
    fun `rows show energy per serving`() {
        val poultry = feed.state(emptyMap(), "", Language.SK).sections.single { it.chapter == BookChapter.POULTRY }.recipes
        assertEquals(240, poultry.single { it.recipeId == "cicer" }.kcal, "400 g × 120 kcal / 100 g for two")
        assertEquals(360, poultry.single { it.recipeId == "cuketa" }.kcal)
    }

    @Test
    fun `a chapter chip narrows the book to that chapter alone`() {
        val whole = feed.state(emptyMap(), "", Language.SK)
        assertEquals(null, whole.selected)
        assertEquals(6, whole.total)
        assertEquals(whole.sections.map { it.chapter }, whole.chapters.map { it.chapter })
        assertEquals(2, whole.chapters.single { it.chapter == BookChapter.POULTRY }.count)

        val poultry = feed.state(emptyMap(), "", Language.SK, BookChapter.POULTRY)
        assertEquals(BookChapter.POULTRY, poultry.selected)
        assertEquals(listOf(BookChapter.POULTRY), poultry.sections.map { it.chapter }, "no other chapter follows")
        assertEquals(whole.chapters, poultry.chapters, "every chip stays to switch chapters")
    }

    @Test
    fun `a search that leaves the chosen chapter empty shows the whole result`() {
        val state = feed.state(emptyMap(), "polievka", Language.SK, BookChapter.POULTRY)
        assertEquals(null, state.selected)
        assertEquals(listOf(BookChapter.SOUP), state.sections.map { it.chapter })
        assertEquals(BookChapter.POULTRY, feed.state(emptyMap(), "kura", Language.SK, BookChapter.POULTRY).selected)
    }

    @Test
    fun `a search keeps only matching recipes in their chapters`() {
        val state = feed.state(emptyMap(), "kura", Language.SK)

        assertTrue(state.searching)
        assertEquals(listOf(BookChapter.POULTRY), state.sections.map { it.chapter })
        assertEquals(setOf("cicer", "cuketa"), state.sections.single().recipes.map { it.recipeId }.toSet())
        assertTrue(feed.state(emptyMap(), "xyzzy", Language.SK).sections.isEmpty(), "nothing matched")
    }
}
