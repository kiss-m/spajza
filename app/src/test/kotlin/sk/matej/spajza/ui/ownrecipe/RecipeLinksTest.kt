package sk.matej.spajza.ui.ownrecipe

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipeLinksTest {
    @Test
    fun `addresses in shared text`() {
        assertEquals("https://example.com/gulas", RecipeLinks.urlIn("https://example.com/gulas"))
        assertEquals("https://example.com/gulas?x=1", RecipeLinks.urlIn("Hovädzí guláš | Blog\nhttps://example.com/gulas?x=1"))
        assertEquals("http://example.com/a", RecipeLinks.urlIn("Pozri (http://example.com/a)."))
    }

    @Test
    fun `a pasted address without the scheme`() {
        assertEquals("https://www.example.sk/recept/gulas", RecipeLinks.urlIn(" www.example.sk/recept/gulas "))
        assertEquals("https://example.com", RecipeLinks.urlIn("example.com"))
        assertNull(RecipeLinks.urlIn("guláš od babky"))
        assertNull(RecipeLinks.urlIn("1.5"))
        assertNull(RecipeLinks.urlIn(""))
    }
}
