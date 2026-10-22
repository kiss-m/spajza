package sk.matej.spajza.domain.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextNormalizerTest {
    @Test
    fun `strips diacritics, lowercases and collapses whitespace`() {
        assertEquals("kuracie prsia", TextNormalizer.normalize("  Kuracie   PRSIA "))
        assertEquals("kuracich prs", TextNormalizer.normalize("kuracích pŕs"))
        assertEquals("spajza", TextNormalizer.normalize("Špajza"))
        assertEquals("kureci prsa", TextNormalizer.normalize("kuřecí prsa"))
        assertEquals("ludova cesnacka", TextNormalizer.normalize("Ľudová cesnačka"))
    }

    @Test
    fun `punctuation becomes a word separator`() {
        assertEquals("pasta al dente", TextNormalizer.normalize("Pasta (al-dente)!"))
    }
}
