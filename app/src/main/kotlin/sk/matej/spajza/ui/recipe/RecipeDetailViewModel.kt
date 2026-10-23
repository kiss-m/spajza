package sk.matej.spajza.ui.recipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.matching.RecipeMatch
import sk.matej.spajza.domain.matching.RecipeMatcher
import sk.matej.spajza.domain.rating.Verdict
import sk.matej.spajza.domain.repository.RatingRepository
import sk.matej.spajza.domain.repository.ShoppingRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.session.kitchen
import sk.matej.spajza.domain.session.personalization
import sk.matej.spajza.domain.usecase.AddMissingToShoppingUseCase
import sk.matej.spajza.domain.usecase.CookUndo
import sk.matej.spajza.ui.navigation.RecipeDestination
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface DetailEvent {
    data class AddedToShopping(val count: Int) : DetailEvent

    data class Cooked(val pantryChanges: Int) : DetailEvent

    data object CookUndone : DetailEvent
}

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val home: HomeData,
    private val ratings: RatingRepository,
    private val shopping: ShoppingRepository,
    private val cooking: RecipeCooking,
    private val clock: Clock,
) : ViewModel() {
    private val recipeId = savedStateHandle.toRoute<RecipeDestination>().recipeId

    /** Null until the user changes it: then the matcher's default for this recipe applies. */
    private val servings = MutableStateFlow<Int?>(null)
    private var matcher: Pair<Catalog, RecipeMatcher>? = null
    private var lastMatch: RecipeMatch? = null
    private var lastUndo: CookUndo? = null

    private val events = Channel<DetailEvent>(Channel.BUFFERED)
    val screenEvents: Flow<DetailEvent> = events.receiveAsFlow()

    private val household = combine(home.catalog, home.session, home.pantry) { catalog, session, pantry -> Triple(catalog, session, pantry) }

    val state: StateFlow<RecipeDetailUiState> = combine(household, home.ratings, home.cookHistory, servings) { (catalog, session, pantry), ratings, history, chosenServings ->
        if (catalog == null || session == null || pantry == null) return@combine RecipeDetailUiState()
        val recipe = catalog.recipesById[recipeId] ?: return@combine RecipeDetailUiState(isLoading = false, notFound = true)
        val match = matcherFor(catalog).evaluate(
            recipe = recipe,
            kitchen = session.kitchen(pantry),
            personalization = session.personalization(ratings.orEmpty(), history.orEmpty()),
            today = LocalDate.now(clock),
            servings = chosenServings,
        )
        lastMatch = match
        RecipeDetailBuilder.build(catalog, match, ratings?.get(recipeId))
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    private fun matcherFor(catalog: Catalog): RecipeMatcher {
        val cached = matcher
        if (cached != null && cached.first === catalog) return cached.second
        return RecipeMatcher(catalog).also { matcher = catalog to it }
    }

    fun onServingsChange(value: Int) {
        servings.value = value.coerceIn(MIN_SERVINGS, MAX_SERVINGS)
    }

    fun onToggleFavorite() {
        val session = home.session.value ?: return
        val favorite = !state.value.favorite
        viewModelScope.launch { ratings.setFavorite(session.uid, recipeId, favorite) }
    }

    fun onVerdict(verdict: Verdict) {
        val session = home.session.value ?: return
        val next = if (state.value.verdict == verdict) null else verdict
        viewModelScope.launch { ratings.setVerdict(session.uid, recipeId, next) }
    }

    fun onAddMissing() {
        val catalog = home.catalog.value ?: return
        val session = home.session.value ?: return
        val match = lastMatch ?: return
        viewModelScope.launch {
            val count = AddMissingToShoppingUseCase(catalog, shopping)(session.householdId, match, addedBy = session.uid)
            events.send(DetailEvent.AddedToShopping(count))
        }
    }

    fun onCooked() {
        val servings = state.value.servings
        viewModelScope.launch {
            val undo = cooking.cook(recipeId, servings) ?: return@launch
            lastUndo = undo
            events.send(DetailEvent.Cooked(undo.pantryChanges))
        }
    }

    fun onUndoCooked() {
        val undo = lastUndo ?: return
        lastUndo = null
        viewModelScope.launch {
            undo.undo()
            events.send(DetailEvent.CookUndone)
        }
    }

    companion object {
        const val MIN_SERVINGS = 1
        const val MAX_SERVINGS = 12
    }
}
