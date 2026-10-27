package sk.matej.spajza.ui.cook

import sk.matej.spajza.domain.browse.FeedFilter
import sk.matej.spajza.domain.browse.RecipeSearch
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.matching.PurchaseSuggester
import sk.matej.spajza.domain.matching.RecipeMatch
import sk.matej.spajza.domain.matching.RecipeMatcher
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.rating.CookEvent
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.session.Session
import sk.matej.spajza.domain.session.kitchen
import sk.matej.spajza.domain.session.personalization
import java.time.LocalDate
import kotlin.math.roundToInt

data class MissingItem(val ingredientId: String, val name: LocalizedText, val priceTier: PriceTier)

data class RecipeCardModel(
    val recipeId: String,
    val title: LocalizedText,
    val cuisine: Cuisine,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val totalTimeMin: Int,
    val missing: List<MissingItem>,
    val favorite: Boolean,
)

/** The "Dokúp a odomkni" card: up to three items and the recipes they make ready. */
data class PurchaseModel(val items: List<MissingItem>, val unlockedIds: List<String>, val unlockedTitles: List<LocalizedText>)

data class CookUiState(
    val isLoading: Boolean = true,
    val pantryEmpty: Boolean = false,
    val filter: FeedFilter = FeedFilter(),
    val cuisines: List<Cuisine> = emptyList(),
    val ready: List<RecipeCardModel> = emptyList(),
    val toBuy: List<RecipeCardModel> = emptyList(),
    val purchase: PurchaseModel? = null,
    /** Non-null while searching; replaces the two sections. */
    val searchResults: List<RecipeCardModel>? = null,
)

/** Builds the "Čo uvariť" state from the catalog and the household's data. Pure, so it is unit-tested. */
class CookFeed(private val catalog: Catalog) {
    private val matcher = RecipeMatcher(catalog)
    private val search = RecipeSearch(catalog)
    private val suggester = PurchaseSuggester()
    private val cuisines = catalog.recipes.map { it.cuisine }.distinct().sorted()

    fun state(
        session: Session,
        pantry: List<PantryItem>,
        ratings: Map<String, Rating>,
        cookHistory: List<CookEvent>,
        query: String,
        filter: FeedFilter,
        today: LocalDate,
    ): CookUiState {
        val kitchen = session.kitchen(pantry)
        val personalization = session.personalization(ratings, cookHistory)
        val result = matcher.match(kitchen, personalization, today)
        val ready = result.ready.filter { filter.matches(it.recipe) }
        val toBuy = result.toBuy.filter { filter.matches(it.recipe) }
        val suggestion = suggester.suggest(toBuy)
        val purchase = suggestion.takeIf { it.items.isNotEmpty() }?.let {
            PurchaseModel(
                items = it.items.map { item -> missingItem(item.ingredientId, item.priceTier) },
                unlockedIds = it.unlockedRecipeIds,
                unlockedTitles = it.unlockedRecipeIds.mapNotNull { id -> catalog.recipesById[id]?.title },
            )
        }
        val searchResults = if (query.isBlank()) {
            null
        } else {
            // The chips narrow the search too, like they narrow both sections.
            search.search(query)
                .filter { filter.matches(it) }
                .map { card(matcher.evaluate(it, kitchen, personalization, today), ratings) }
        }
        return CookUiState(
            isLoading = false,
            pantryEmpty = pantry.isEmpty(),
            filter = filter,
            cuisines = cuisines,
            ready = ready.map { card(it, ratings) },
            toBuy = toBuy.map { card(it, ratings) },
            purchase = purchase,
            searchResults = searchResults,
        )
    }

    private fun card(match: RecipeMatch, ratings: Map<String, Rating>) = RecipeCardModel(
        recipeId = match.recipe.id,
        title = match.recipe.title,
        cuisine = match.recipe.cuisine,
        kcal = match.perServing.kcal.roundToInt(),
        protein = match.perServing.protein.roundToInt(),
        carbs = match.perServing.carbs.roundToInt(),
        fat = match.perServing.fat.roundToInt(),
        totalTimeMin = match.recipe.totalTimeMin,
        missing = match.missing.map { missingItem(it.ingredientId, it.priceTier) },
        favorite = ratings[match.recipe.id]?.favorite == true,
    )

    private fun missingItem(ingredientId: String, priceTier: PriceTier) =
        MissingItem(ingredientId, catalog.ingredient(ingredientId).name, priceTier)
}
