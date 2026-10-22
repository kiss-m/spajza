package sk.matej.spajza.domain.search

import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.text.TextNormalizer

data class SearchHit(
    val ingredient: Ingredient,
    /** 0..1; 1 is an exact name or synonym match. */
    val score: Double,
    /** The normalized name or synonym that matched. */
    val matchedTerm: String,
)

/**
 * Ingredient search over names and SK/CZ/EN synonyms, insensitive to case and diacritics.
 * Matches, best first: the whole term, a prefix of the whole term, every query word prefixing a
 * word of the term, and finally a shared word stem, which catches Slovak and Czech case endings
 * that are not listed as synonyms ("mrkvami" → "mrkva").
 */
class IngredientSearch(ingredients: List<Ingredient>) {

    private class Entry(val ingredient: Ingredient, val term: String, val tokens: List<String>, val isName: Boolean)

    private val entries: List<Entry> = ingredients.flatMap { ingredient ->
        val names = listOf(ingredient.name.sk, ingredient.name.en).map(TextNormalizer::normalize).toSet()
        val synonyms = ingredient.synonyms.map(TextNormalizer::normalize).toSet() - names
        names.map { Entry(ingredient, it, it.split(' '), isName = true) } +
            synonyms.map { Entry(ingredient, it, it.split(' '), isName = false) }
    }.filter { it.term.isNotEmpty() }

    fun search(query: String, limit: Int = 20): List<SearchHit> {
        val normalized = TextNormalizer.normalize(query)
        if (normalized.isEmpty()) return emptyList()
        val queryTokens = normalized.split(' ')
        return entries.asSequence()
            .mapNotNull { entry -> score(normalized, queryTokens, entry)?.let { entry to it } }
            .sortedWith(BEST_FIRST)
            .distinctBy { (entry, _) -> entry.ingredient.id }
            .take(limit)
            .map { (entry, score) -> SearchHit(entry.ingredient, score, entry.term) }
            .toList()
    }

    fun bestMatch(text: String): SearchHit? = search(text, limit = 1).firstOrNull()

    private fun score(query: String, queryTokens: List<String>, entry: Entry): Double? {
        if (entry.term == query) return EXACT
        if (entry.term.startsWith(query)) return WHOLE_PREFIX
        val unused = entry.tokens.toMutableList()
        var weakest = TokenMatch.EXACT
        for (token in queryTokens) {
            val (index, match) = unused.withIndex()
                .map { (i, termToken) -> i to tokenMatch(token, termToken) }
                .filter { it.second != null }
                .minByOrNull { it.second!!.ordinal }
                ?: return null
            unused.removeAt(index)
            if (match!!.ordinal > weakest.ordinal) weakest = match
        }
        return weakest.score - EXTRA_TOKEN_PENALTY * unused.size
    }

    private enum class TokenMatch(val score: Double) {
        EXACT(0.85),
        PREFIX(0.8),
        STEM(0.65),
    }

    private fun tokenMatch(query: String, term: String): TokenMatch? = when {
        query == term -> TokenMatch.EXACT
        term.startsWith(query) -> TokenMatch.PREFIX
        commonPrefix(query, term) >= maxOf(MIN_STEM, minOf(query.length, term.length) - STEM_SLACK) -> TokenMatch.STEM
        else -> null
    }

    private fun commonPrefix(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    private companion object {
        /** Higher score, then display names before synonyms, then shorter terms. */
        val BEST_FIRST = compareByDescending<Pair<Entry, Double>> { it.second }
            .thenByDescending { it.first.isName }
            .thenBy { it.first.term.length }
            .thenBy { it.first.ingredient.id }

        const val EXACT = 1.0
        const val WHOLE_PREFIX = 0.9
        const val EXTRA_TOKEN_PENALTY = 0.02
        const val MIN_STEM = 3
        /** Characters a case ending may differ by: "kuracích" ~ "kuracie", "zemiakov" ~ "zemiakoch". */
        const val STEM_SLACK = 2
    }
}
