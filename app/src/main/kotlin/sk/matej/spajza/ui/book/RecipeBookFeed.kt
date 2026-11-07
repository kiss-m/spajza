package sk.matej.spajza.ui.book

import sk.matej.spajza.domain.book.BookChapter
import sk.matej.spajza.domain.book.RecipeBook
import sk.matej.spajza.domain.browse.RecipeSearch
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.nutrition.NutritionCalculator
import sk.matej.spajza.domain.rating.Rating
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

/** One recipe in the book. */
data class BookRecipeModel(
    val recipeId: String,
    val title: LocalizedText,
    val cuisine: Cuisine,
    val totalTimeMin: Int,
    val kcal: Int,
    val favorite: Boolean,
)

data class BookSectionModel(val chapter: BookChapter, val recipes: List<BookRecipeModel>)

/** A filter chip: a chapter that has recipes for the current search, and how many. */
data class BookChapterChip(val chapter: BookChapter, val count: Int)

data class BookUiState(
    val isLoading: Boolean = true,
    /** Chapters with recipes for the current search, in book order. */
    val chapters: List<BookChapterChip> = emptyList(),
    /** Recipes for the current search across the whole book. */
    val total: Int = 0,
    /** The chapter the book is narrowed to, or null for the whole book. */
    val selected: BookChapter? = null,
    /** What the list shows: the selected chapter only, or every chapter. */
    val sections: List<BookSectionModel> = emptyList(),
    /** A search is typed; empty [sections] then mean that nothing matched. */
    val searching: Boolean = false,
)

/**
 * The recipe book (D15) from the catalog, own recipes included, and the user's favorites. Pure, so
 * it is unit-tested.
 */
class RecipeBookFeed(private val catalog: Catalog) {
    private val book = RecipeBook(catalog.ingredientsById::get)
    private val search = RecipeSearch(catalog)
    private val kcal: Map<String, Int> = NutritionCalculator(catalog.ingredientsById::get).let { calculator ->
        catalog.recipes.associate { it.id to calculator.perServing(it).kcal.roundToInt() }
    }

    /**
     * The book for [query]. A [chapter] narrows it to that chapter alone; when the search leaves
     * that chapter empty, the whole result shows instead and no chapter is selected.
     */
    fun state(ratings: Map<String, Rating>, query: String, language: Language, chapter: BookChapter? = null): BookUiState {
        val favorites = ratings.filterValues { it.favorite }.keys
        val searching = query.isNotBlank()
        val shown = if (searching) search.search(query) else catalog.recipes
        val all = book.sections(shown, favorites, order(language))
        val selected = all.firstOrNull { it.chapter == chapter }
        return BookUiState(
            isLoading = false,
            chapters = all.map { BookChapterChip(it.chapter, it.recipes.size) },
            total = shown.size,
            selected = selected?.chapter,
            sections = (selected?.let(::listOf) ?: all).map { section ->
                BookSectionModel(section.chapter, section.recipes.map { model(it, favorites) })
            },
            searching = searching,
        )
    }

    private fun model(recipe: Recipe, favorites: Set<String>) = BookRecipeModel(
        recipeId = recipe.id,
        title = recipe.title,
        cuisine = recipe.cuisine,
        totalTimeMin = recipe.totalTimeMin,
        kcal = kcal[recipe.id] ?: 0,
        favorite = recipe.id in favorites,
    )

    companion object {
        /** Titles in the alphabetical order of [language]: Slovak puts "č" after "c" and "ch" after "h". */
        fun order(language: Language): Comparator<Recipe> {
            val collator = Collator.getInstance(if (language == Language.EN) Locale.ENGLISH else Locale.forLanguageTag("sk"))
            collator.strength = Collator.SECONDARY
            return compareBy(collator) { it.title[language] }
        }
    }
}
