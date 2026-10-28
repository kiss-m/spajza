package sk.matej.spajza.domain.own

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.search.SearchFixture
import sk.matej.spajza.domain.unit.MeasureUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeLinesTest {
    private val matcher = RecipeLineMatcher(Catalog(SearchFixture.ingredients, emptyList(), emptyList()))

    @Test
    fun `typed lines are matched with their amounts`() {
        val chicken = matcher.match("200 g kuracích pŕs")
        assertEquals("chicken-breast", chicken.ingredientId)
        assertEquals(200.0, chicken.amount)
        assertEquals(MeasureUnit.G, chicken.unit)
        assertEquals("200 g kuracích pŕs", chicken.text)

        val salt = matcher.match("soľ podľa chuti")
        assertEquals("salt", salt.ingredientId)
        assertEquals(MeasureUnit.TO_TASTE, salt.unit)
    }

    @Test
    fun `bullets from web pages are dropped and optional lines are recognized`() {
        val garlic = matcher.match("▢ 2 strúčiky cesnaku, nasekané")
        assertEquals("2 strúčiky cesnaku, nasekané", garlic.text)
        assertEquals("garlic", garlic.ingredientId)
        assertEquals(2.0, garlic.amount)
        assertFalse(garlic.optional)

        val spinach = matcher.match("• hrsť špenátu (nepovinné)")
        assertEquals("spinach", spinach.ingredientId)
        assertTrue(spinach.optional)
        assertTrue(matcher.match("fresh spinach, optional").optional)
    }

    @Test
    fun `lines the catalog does not know stay as text`() {
        val sauce = matcher.match("1 lyžica rybacej omáčky")
        assertNull(sauce.ingredientId)
        assertEquals("1 lyžica rybacej omáčky", sauce.text)
        assertEquals(1.0, sauce.amount, "the amount is kept for when the user picks the ingredient")
    }

    @Test
    fun `blank lines and section headings are not ingredients`() {
        assertEquals(listOf("2 vajcia", "500 ml mlieka"), matcher.lines(listOf(" ", "Na cesto:", "2 vajcia", "", "500 ml mlieka")).map { it.text })
    }
}
