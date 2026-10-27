package sk.matej.spajza.ui.cook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.matej.spajza.domain.browse.FeedFilter
import sk.matej.spajza.domain.browse.RecipeType
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.rating.CookEvent
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.repository.ShoppingRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.session.Session
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface CookScreenEvent {
    data object AddedToShopping : CookScreenEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CookViewModel @Inject constructor(
    private val home: HomeData,
    private val shopping: ShoppingRepository,
    private val clock: Clock,
) : ViewModel() {

    private class Inputs(
        val catalog: Catalog?,
        val session: Session?,
        val pantry: List<PantryItem>?,
        val ratings: Map<String, Rating>?,
        val history: List<CookEvent>?,
    )

    private val _query = MutableStateFlow("")

    /** Read by the search field directly, so typing never waits for the matching below. */
    val query: StateFlow<String> = _query.asStateFlow()
    private val filter = MutableStateFlow(FeedFilter())
    private var feed: Pair<Catalog, CookFeed>? = null

    private val events = Channel<CookScreenEvent>(Channel.BUFFERED)
    val screenEvents: Flow<CookScreenEvent> = events.receiveAsFlow()

    private val inputs = combine(home.catalog, home.session, home.pantry, home.ratings, home.cookHistory) { catalog, session, pantry, ratings, history ->
        Inputs(catalog, session, pantry, ratings, history)
    }

    val state: StateFlow<CookUiState> = combine(inputs, _query, filter) { inputs, query, filter -> Triple(inputs, query, filter) }
        .mapLatest { (inputs, query, filter) ->
            val catalog = inputs.catalog
            val session = inputs.session
            val pantry = inputs.pantry
            if (catalog == null || session == null || pantry == null) {
                CookUiState(filter = filter)
            } else {
                feedFor(catalog).state(
                    session = session,
                    pantry = pantry,
                    ratings = inputs.ratings.orEmpty(),
                    cookHistory = inputs.history.orEmpty(),
                    query = query,
                    filter = filter,
                    today = LocalDate.now(clock),
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CookUiState())

    private fun feedFor(catalog: Catalog): CookFeed {
        val cached = feed
        if (cached != null && cached.first === catalog) return cached.second
        return CookFeed(catalog).also { feed = catalog to it }
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onToggleCuisine(cuisine: Cuisine) = filter.update {
        it.copy(cuisines = if (cuisine in it.cuisines) it.cuisines - cuisine else it.cuisines + cuisine)
    }

    fun onToggleType(type: RecipeType) = filter.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }

    fun onMealType(mealType: MealType?) = filter.update { it.copy(mealType = mealType) }

    /** Clears the chips and keeps the meal type tab. */
    fun onClearFilters() = filter.update { FeedFilter(mealType = it.mealType) }

    fun onAddPurchase() {
        val purchase = state.value.purchase ?: return
        val session = home.session.value ?: return
        viewModelScope.launch {
            purchase.items.forEach { item ->
                shopping.addIngredient(
                    householdId = session.householdId,
                    ingredient = IngredientRef.Catalog(item.ingredientId),
                    amount = null,
                    reasonRecipeIds = purchase.unlockedIds.toSet(),
                    addedBy = session.uid,
                )
            }
            events.send(CookScreenEvent.AddedToShopping)
        }
    }
}
