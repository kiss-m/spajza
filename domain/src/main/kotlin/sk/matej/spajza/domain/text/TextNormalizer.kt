package sk.matej.spajza.domain.text

import java.text.Normalizer

/** Folds text for diacritics- and case-insensitive matching ("Kuracích pŕs" -> "kuracich prs"). */
object TextNormalizer {
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
}
