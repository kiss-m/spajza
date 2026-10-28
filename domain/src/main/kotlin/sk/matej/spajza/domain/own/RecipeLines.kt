package sk.matej.spajza.domain.own

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.search.IngredientLineParser
import sk.matej.spajza.domain.search.IngredientSearch
import sk.matej.spajza.domain.text.TextNormalizer

/** Matches typed or imported ingredient lines to the catalog; a weak match stays plain text. */
class RecipeLineMatcher(catalog: Catalog) {
    private val parser = IngredientLineParser(IngredientSearch(catalog.ingredients))

    fun match(text: String): OwnIngredientLine {
        val clean = clean(text)
        val parsed = parser.parse(clean.replace(OPTIONAL_NOTE, " "))
        val ingredient = parsed.ingredient?.takeIf { parsed.confidence >= MIN_CONFIDENCE }
        return OwnIngredientLine(
            text = clean,
            ingredientId = ingredient?.id,
            amount = parsed.amount,
            unit = parsed.unit,
            optional = OPTIONAL_NOTE.containsMatchIn(clean),
        )
    }

    /** How sure the match of [text] is, 0..1; used to guess which photo lines are ingredients. */
    fun confidence(text: String): Double {
        val parsed = parser.parse(clean(text))
        return if (parsed.ingredient == null) 0.0 else parsed.confidence
    }

    /** One line per ingredient; blank lines and headings such as "Na cesto:" are dropped. */
    fun lines(texts: List<String>): List<OwnIngredientLine> = texts.map(::clean).filter { it.isNotEmpty() && !isHeading(it) }.map(::match)

    companion object {
        /** Below this the line is kept as text and the user may pick the ingredient. */
        const val MIN_CONFIDENCE = 0.6

        /** The line without list bullets and extra spaces. */
        fun clean(text: String): String = text.replace(BULLET, "").replace(SPACES, " ").trim()

        /** "Na cesto:", "Omáčka:" — a heading inside an ingredient list. */
        fun isHeading(text: String): Boolean = text.endsWith(":") && text.none(Char::isDigit) && TextNormalizer.normalize(text).split(' ').size <= MAX_HEADING_WORDS

        private const val MAX_HEADING_WORDS = 4
        private val BULLET = Regex("""^[\s•·▪▫◦‣∙▢□☐☑✓✔*–—-]+""")
        private val SPACES = Regex("""\s+""")
        private val OPTIONAL_NOTE = Regex("""(?iU)\(?\b(nepovinn\w*|voliteľn\w*|volitel\w*|optional)\b\)?""")
    }
}
