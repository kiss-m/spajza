package sk.matej.spajza.ui.recipe

import android.net.Uri
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
import sk.matej.spajza.data.photos.RecipePhotoStore
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.matching.RecipeMatch
import sk.matej.spajza.domain.matching.RecipeMatcher
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.own.OwnRecipes
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.rating.Verdict
import sk.matej.spajza.domain.repository.MealPlanRepository
import sk.matej.spajza.domain.repository.OwnRecipeRepository
import sk.matej.spajza.domain.repository.RatingRepository
import sk.matej.spajza.domain.repository.ShoppingRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.session.Session
import sk.matej.spajza.domain.session.kitchen
import sk.matej.spajza.domain.session.personalization
import sk.matej.spajza.domain.usecase.AddMissingToShoppingUseCase
import sk.matej.spajza.domain.usecase.AddToPlanUseCase
import sk.matej.spajza.domain.usecase.CookUndo
import sk.matej.spajza.ui.navigation.RecipeDestination
import sk.matej.spajza.ui.plan.AddToPlanDraft
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface DetailEvent {
    data class AddedToShopping(val count: Int) : DetailEvent

    data class Cooked(val pantryChanges: Int) : DetailEvent

    data object CookUndone : DetailEvent

    /** Planned for [days] days. */
    data class AddedToPlan(val days: Int) : DetailEvent

    /** An own recipe was deleted; the screen closes. */
    data object Deleted : DetailEvent

    data object PhotoSaved : DetailEvent

    /** The picked picture could not be read or saved. */
    data object PhotoFailed : DetailEvent
}

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val home: HomeData,
    private val ratings: RatingRepository,
    private val shopping: ShoppingRepository,
    private val ownRecipes: OwnRecipeRepository,
    private val plans: MealPlanRepository,
    private val cooking: RecipeCooking,
    private val photoStore: RecipePhotoStore,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<RecipeDestination>()
    private val recipeId = route.recipeId

    /** A planned meal's servings, or null until the user changes it: then the matcher's default applies. */
    private val servings = MutableStateFlow(route.servings?.coerceIn(MIN_SERVINGS, MAX_SERVINGS))
    private var matcher: Pair<Catalog, RecipeMatcher>? = null
    private var lastMatch: RecipeMatch? = null
    private var lastUndo: CookUndo? = null

    private val events = Channel<DetailEvent>(Channel.BUFFERED)
    val screenEvents: Flow<DetailEvent> = events.receiveAsFlow()

    /** True from deleting an own recipe until the screen closes, so it never shows "not found". */
    private val deleting = MutableStateFlow(false)

    private class Household(val catalog: Catalog?, val session: Session?, val pantry: List<PantryItem>?, val ownRecipes: List<OwnRecipe>?)

    private val household = combine(home.catalog, home.session, home.pantry, home.ownRecipes) { catalog, session, pantry, own ->
        Household(catalog, session, pantry, own)
    }
    private val choices = combine(servings, deleting) { chosen, closing -> chosen to closing }

    val state: StateFlow<RecipeDetailUiState> = combine(household, home.ratings, home.cookHistory, choices) { data, ratings, history, (chosenServings, closing) ->
        val catalog = data.catalog
        val session = data.session
        val pantry = data.pantry
        if (closing || catalog == null || session == null || pantry == null) return@combine RecipeDetailUiState()
        val recipe = catalog.recipesById[recipeId] ?: return@combine RecipeDetailUiState(isLoading = false, notFound = true)
        val match = matcherFor(catalog).evaluate(
            recipe = recipe,
            kitchen = session.kitchen(pantry),
            personalization = session.personalization(ratings.orEmpty(), history.orEmpty()),
            today = LocalDate.now(clock),
            servings = chosenServings,
        )
        lastMatch = match
        val own = OwnRecipes.ownId(recipeId)?.let { id -> data.ownRecipes?.firstOrNull { it.id == id } }
        RecipeDetailBuilder.build(catalog, match, ratings?.get(recipeId), own)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    /** Own photos of this recipe on this phone, newest first (D14). */
    val photos: StateFlow<List<File>> = photoStore.photos(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun today(): LocalDate = LocalDate.now(clock)

    /** The "Pridať do plánu" sheet's starting point: today, the recipe's meal and the shown servings. */
    fun planDraft(): AddToPlanDraft? {
        val current = state.value
        val recipe = current.recipe ?: return null
        return AddToPlanDraft.initial(recipe.mealTypes, current.servings, today())
    }

    fun onAddToPlan(draft: AddToPlanDraft) {
        val session = home.session.value ?: return
        viewModelScope.launch {
            val entries = AddToPlanUseCase(plans)(session.householdId, recipeId, draft.dates, draft.meal, draft.servings, addedBy = session.uid)
            events.send(DetailEvent.AddedToPlan(entries.size))
        }
    }

    /** Deletes an own recipe for the whole household, and its photos on this phone. */
    fun onDelete() {
        val ownId = OwnRecipes.ownId(recipeId) ?: return
        val session = home.session.value ?: return
        deleting.value = true
        viewModelScope.launch {
            ownRecipes.delete(session.householdId, ownId)
            photoStore.deleteAll(recipeId)
            events.send(DetailEvent.Deleted)
        }
    }

    /** Saves the picture at [source] (camera or gallery) as the recipe's newest photo. */
    fun onAddPhoto(source: Uri) {
        viewModelScope.launch {
            val saved = photoStore.add(recipeId, source)
            events.send(if (saved) DetailEvent.PhotoSaved else DetailEvent.PhotoFailed)
        }
    }

    fun onDeletePhoto(photo: File) {
        viewModelScope.launch { photoStore.delete(recipeId, photo) }
    }

    companion object {
        const val MIN_SERVINGS = PlanEntry.MIN_SERVINGS

        /** As many as a plan entry may have, so a planned batch opens with its servings. */
        const val MAX_SERVINGS = PlanEntry.MAX_SERVINGS
    }
}
