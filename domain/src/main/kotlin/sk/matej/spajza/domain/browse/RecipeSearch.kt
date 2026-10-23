package sk.matej.spajza.domain.browse

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.text.TextNormalizer

/**
 * Search across the whole catalog, including recipes the household cannot make yet. Every query
 * word must start a word of the title (either language) or of an ingredient's name or synonyms;
 * recipes with more title hits come first, otherwise catalog order is kept.
 */
class RecipeSearch(catalog: Catalog) {

    private class Entry(val recipe: Recipe, val titleWords: Set<String>, val ingredientWords: Set<String>)

    private val entries: List<Entry> = catalog.recipes.map { recipe ->
        val ingredientTexts = recipe.ingredients.flatMap { line ->
            catalog.ingredientsById[line.ingredientId]?.let { listOf(it.name.sk, it.name.en) + it.synonyms }.orEmpty()
        }
        Entry(recipe, words(recipe.title.sk, recipe.title.en), words(*ingredientTexts.toTypedArray()))
    }

    fun search(query: String): List<Recipe> {
        val queryWords = TextNormalizer.normalize(query).split(' ').filter { it.isNotEmpty() }
        if (queryWords.isEmpty()) return emptyList()
        return entries
            .mapNotNull { entry ->
                val titleHits = queryWords.count { word -> entry.titleWords.any { it.startsWith(word) } }
                val allFound = queryWords.all { word ->
                    entry.titleWords.any { it.startsWith(word) } || entry.ingredientWords.any { it.startsWith(word) }
                }
                if (allFound) entry.recipe to titleHits else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun words(vararg texts: String): Set<String> =
        texts.flatMap { TextNormalizer.normalize(it).split(' ') }.filter { it.isNotEmpty() }.toSet()
}
