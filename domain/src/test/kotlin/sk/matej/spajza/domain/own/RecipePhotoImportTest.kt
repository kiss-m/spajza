package sk.matej.spajza.domain.own

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.own.PhotoLineKind.IGNORE
import sk.matej.spajza.domain.own.PhotoLineKind.INGREDIENT
import sk.matej.spajza.domain.own.PhotoLineKind.STEP
import sk.matej.spajza.domain.own.PhotoLineKind.TITLE
import sk.matej.spajza.domain.receipt.OcrLine
import sk.matej.spajza.domain.search.SearchFixture
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipePhotoImportTest {
    private val matcher = RecipeLineMatcher(Catalog(SearchFixture.ingredients, emptyList(), emptyList()))

    /** Lines one under another; [height] stands for the font size, [block] for the paragraph. */
    private fun page(vararg lines: Triple<String, Int, Int>): List<OcrLine> {
        var top = 0
        return lines.map { (text, height, block) ->
            OcrLine(text, left = 40, top = top, right = 40 + text.length * height / 2, bottom = top + height, block = block).also { top += height + 8 }
        }
    }

    @Test
    fun `a cookbook page with headings`() {
        val ocr = page(
            Triple("Zemiaková kaša so špenátom", 60, 0),
            Triple("4 porcie · 30 minút", 24, 1),
            Triple("Suroviny", 30, 2),
            Triple("1 kg zemiakov", 24, 3),
            Triple("250 ml mlieka", 24, 3),
            Triple("hrsť špenátu", 24, 3),
            Triple("soľ podľa chuti", 24, 3),
            Triple("Postup", 30, 4),
            Triple("1. Zemiaky ošúpte, nakrájajte na kocky a varte", 24, 5),
            Triple("v osolenej vode 20 minút, kým nezmäknú.", 24, 5),
            Triple("2. Scedené zemiaky roztlačte s teplým mliekom a", 24, 6),
            Triple("vmiešajte nasekaný špenát.", 24, 6),
            Triple("42", 20, 7),
        )
        val lines = RecipePhotoImport.guess(ocr, matcher)

        assertEquals(
            listOf(TITLE, IGNORE, IGNORE, INGREDIENT, INGREDIENT, INGREDIENT, INGREDIENT, IGNORE, STEP, STEP, STEP, STEP, IGNORE),
            lines.map { it.kind },
        )
        val draft = RecipePhotoImport.draft(lines)
        assertEquals("Zemiaková kaša so špenátom", draft.title)
        assertEquals(4, draft.servings)
        assertEquals(30, draft.totalTimeMin)
        assertEquals(listOf("1 kg zemiakov", "250 ml mlieka", "hrsť špenátu", "soľ podľa chuti"), draft.ingredientLines)
        assertEquals(
            listOf(
                "Zemiaky ošúpte, nakrájajte na kocky a varte v osolenej vode 20 minút, kým nezmäknú.",
                "Scedené zemiaky roztlačte s teplým mliekom a vmiešajte nasekaný špenát.",
            ),
            draft.steps,
        )
        assertEquals(RecipeSource.PHOTO, draft.source)
    }

    @Test
    fun `without headings short amounts are ingredients and paragraphs are steps`() {
        val ocr = page(
            Triple("Palacinky", 48, 0),
            Triple("2 vajcia", 24, 1),
            Triple("500 ml mlieka", 24, 1),
            Triple("250 g hladkej múky", 24, 1),
            Triple("Vajcia rozšľahajte s mliekom, postupne vmiešajte", 24, 2),
            Triple("múku a nechajte cesto 10 minút odpočívať.", 24, 2),
            Triple("Na rozpálenej panvici pečte tenké palacinky do", 24, 3),
            Triple("zlatista z oboch strán.", 24, 3),
        )
        val lines = RecipePhotoImport.guess(ocr, matcher)

        assertEquals(listOf(TITLE, INGREDIENT, INGREDIENT, INGREDIENT, STEP, STEP, STEP, STEP), lines.map { it.kind })
        val draft = RecipePhotoImport.draft(lines)
        assertEquals(2, draft.steps.size, "a new block starts a new step")
        assertEquals("Na rozpálenej panvici pečte tenké palacinky do zlatista z oboch strán.", draft.steps[1])
    }

    @Test
    fun `what the user marks wins and hyphenated words are joined`() {
        val lines = listOf(
            PhotoLine("Babkin guláš", TITLE, block = 0),
            PhotoLine("Mäso nakrájajte na kocky a opeč-", STEP, block = 1),
            PhotoLine("te ho na masti.", STEP, block = 1),
            PhotoLine("Reklama", IGNORE, block = 2),
            PhotoLine("Podlejte vodou a duste do mäkka.", STEP, block = 3),
        )
        val draft = RecipePhotoImport.draft(lines)

        assertEquals(listOf("Mäso nakrájajte na kocky a opečte ho na masti.", "Podlejte vodou a duste do mäkka."), draft.steps)
        assertEquals(emptyList(), draft.ingredientLines)
    }
}
