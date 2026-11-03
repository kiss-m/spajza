package sk.matej.spajza.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
import kotlinx.coroutines.launch
import sk.matej.spajza.domain.browse.FeedFilter
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.matching.RecipeMatcher
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.rating.CookEvent
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.repository.MealPlanRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.session.Session
import sk.matej.spajza.domain.usecase.AddToPlanUseCase
import sk.matej.spajza.ui.cook.CookFeed
import sk.matej.spajza.ui.cook.CookUiState
import sk.matej.spajza.ui.navigation.PlanPickerDestination
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Choosing a recipe for a day of the plan: what can be cooked now, what needs a little shopping, or a search. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val home: HomeData,
    private val plans: MealPlanRepository,
    private val clock: Clock,
) : ViewModel() {
    /** The day the recipe is planned for. */
    val date: LocalDate = runCatching { LocalDate.parse(savedStateHandle.toRoute<PlanPickerDestination>().date) }.getOrNull() ?: today()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private var feed: Pair<Catalog, CookFeed>? = null

    private val events = Channel<Int>(Channel.BUFFERED)

    /** The number of days a recipe was added for; the screen then closes. */
    val added: Flow<Int> = events.receiveAsFlow()

    private class Inputs(
        val catalog: Catalog?,
        val session: Session?,
        val pantry: List<PantryItem>?,
        val ratings: Map<String, Rating>?,
        val history: List<CookEvent>?,
    )

    private val inputs = combine(home.catalog, home.session, home.pantry, home.ratings, home.cookHistory) { catalog, session, pantry, ratings, history ->
        Inputs(catalog, session, pantry, ratings, history)
    }

    val state: StateFlow<CookUiState> = combine(inputs, _query) { inputs, query -> inputs to query }
        .mapLatest { (inputs, query) ->
            val catalog = inputs.catalog
            val session = inputs.session
            val pantry = inputs.pantry
            if (catalog == null || session == null || pantry == null) {
                CookUiState()
            } else {
                feedFor(catalog).state(
                    session = session,
                    pantry = pantry,
                    ratings = inputs.ratings.orEmpty(),
                    cookHistory = inputs.history.orEmpty(),
                    query = query,
                    filter = FeedFilter(),
                    today = today(),
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

    fun today(): LocalDate = LocalDate.now(clock)

    fun onQueryChange(query: String) {
        _query.value = query
    }

    /** The sheet's starting point for [recipeId]: this day, the recipe's meal and the user's servings. */
    fun draftFor(recipeId: String): AddToPlanDraft? {
        val catalog = home.catalog.value ?: return null
        val session = home.session.value ?: return null
        val recipe = catalog.recipesById[recipeId] ?: return null
        val servings = RecipeMatcher(catalog).defaultServings(recipe, session.profile.preferences)
        return AddToPlanDraft.initial(recipe.mealTypes, servings, today(), preferredDate = date)
    }

    fun onAdd(recipeId: String, draft: AddToPlanDraft) {
        val session = home.session.value ?: return
        viewModelScope.launch {
            val entries = AddToPlanUseCase(plans)(session.householdId, recipeId, draft.dates, draft.meal, draft.servings, addedBy = session.uid)
            events.send(entries.size)
        }
    }
}
