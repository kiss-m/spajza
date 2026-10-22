package sk.matej.spajza.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IngredientSearchTest {
    private val search = IngredientSearch(SearchFixture.ingredients)
    private fun top(query: String) = search.search(query).firstOrNull()?.ingredient?.id

    @Test
    fun `search ignores diacritics and case`() {
        assertEquals("spinach", top("SPENAT"))
        assertEquals("salt", top("sol"))
    }

    @Test
    fun `czech and english synonyms match`() {
        assertEquals("chicken-breast", top("kureci prsa"))
        assertEquals("potato", top("brambory"))
        assertEquals("milk", top("milk"))
    }

    @Test
    fun `prefix search ranks whole-name prefixes first`() {
        val results = search.search("cib").map { it.ingredient.id }
        assertEquals(listOf("onion", "red-onion"), results)
    }

    @Test
    fun `every query word must prefix-match a word`() {
        assertEquals("chicken-breast", top("kur pr"))
        assertEquals("chicken-thigh", top("kur steh"))
    }

    @Test
    fun `inflected forms that are not listed still match by stem`() {
        assertEquals("carrot", top("mrkvami"))
        assertEquals("potato", top("zemiakoch"))
    }

    @Test
    fun `each ingredient appears once`() {
        val ids = search.search("kur").map { it.ingredient.id }
        assertEquals(ids.distinct(), ids)
        assertTrue(ids.containsAll(listOf("chicken-breast", "chicken-thigh")))
    }

    @Test
    fun `blank or unknown queries find nothing`() {
        assertTrue(search.search("  ").isEmpty())
        assertNull(top("xyzzy"))
    }
}
