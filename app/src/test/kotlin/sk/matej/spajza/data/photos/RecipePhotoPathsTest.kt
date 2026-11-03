package sk.matej.spajza.data.photos

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RecipePhotoPathsTest {
    @Test
    fun `own recipe ids become folder names`() {
        assertEquals("own_Ab12", RecipePhotoPaths.keyOf("own:Ab12"))
        assertEquals("spaghetti-aglio-olio", RecipePhotoPaths.keyOf("spaghetti-aglio-olio"))
    }

    @Test
    fun `photos are listed newest first and other files are ignored`() {
        val names = listOf("1700000000000.jpg", "1800000000000.jpg.tmp", "notes.txt", "1800000000000.jpg", "-5.jpg", "999.jpg")
        assertEquals(listOf("1800000000000.jpg", "1700000000000.jpg", "999.jpg"), RecipePhotoPaths.newestFirst(names))
    }

    @Test
    fun `a new photo never takes an older name`() {
        assertEquals(500L, RecipePhotoPaths.nextTimestamp(500, emptyList()))
        assertEquals(500L, RecipePhotoPaths.nextTimestamp(500, listOf("400.jpg")))
        assertEquals(501L, RecipePhotoPaths.nextTimestamp(500, listOf("500.jpg", "x.tmp")))
        assertEquals(701L, RecipePhotoPaths.nextTimestamp(500, listOf("700.jpg")))
    }

    @Test
    fun `big photos shrink to the long side limit and small ones keep their size`() {
        assertEquals(1600 to 1200, RecipePhotoPaths.fittedSize(4000, 3000))
        assertEquals(900 to 1600, RecipePhotoPaths.fittedSize(2250, 4000))
        assertEquals(800 to 600, RecipePhotoPaths.fittedSize(800, 600))
        assertEquals(1600 to 1, RecipePhotoPaths.fittedSize(10000, 2))
    }
}
