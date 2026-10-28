package sk.matej.spajza.domain.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.custom.CustomIngredient
import sk.matej.spajza.domain.matching.KitchenState
import sk.matej.spajza.domain.matching.Personalization
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.own.OwnRecipes
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.profile.TargetCalculator
import sk.matej.spajza.domain.rating.CookEvent
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.repository.CatalogRepository
import sk.matej.spajza.domain.repository.CookHistoryRepository
import sk.matej.spajza.domain.repository.CustomIngredientRepository
import sk.matej.spajza.domain.repository.OwnRecipeRepository
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.repository.RatingRepository
import sk.matej.spajza.domain.repository.ShoppingRepository

/**
 * The signed-in household's live data, shared by all screens so that switching tabs shows the
 * latest state at once. Values are null until loaded. Listeners run while a screen collects them,
 * plus [STOP_TIMEOUT_MS] so a configuration change does not restart them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeData(
    sessionManager: SessionManager,
    catalogRepository: CatalogRepository,
    pantryRepository: PantryRepository,
    shoppingRepository: ShoppingRepository,
    ratingRepository: RatingRepository,
    cookHistoryRepository: CookHistoryRepository,
    customIngredientRepository: CustomIngredientRepository,
    ownRecipeRepository: OwnRecipeRepository,
    scope: CoroutineScope,
) {
    val session: StateFlow<Session?> = sessionManager.state
        .map { state ->
            when (state) {
                is SessionState.Ready -> state.session
                is SessionState.Onboarding -> state.session
                SessionState.Loading, SessionState.SignedOut -> null
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val bundled: StateFlow<Catalog?> = catalogRepository.catalog.stateIn(scope, SharingStarted.Eagerly, null)

    private val householdId = session.map { it?.householdId }.distinctUntilChanged()
    private val uid = session.map { it?.uid }.distinctUntilChanged()

    /** The household's own recipes, as stored. */
    val ownRecipes: StateFlow<List<OwnRecipe>?> = householdId.follow(scope) { ownRecipeRepository.observe(it).distinctUntilChanged() }

    /**
     * The bundled catalog with the household's own recipes added (ids `own:<id>`), so every screen
     * treats them like catalog recipes. Null until the bundled catalog is loaded.
     */
    val catalog: StateFlow<Catalog?> = combine(bundled, ownRecipes) { base, own -> base?.let { OwnRecipes.merge(it, own.orEmpty()) } }
        .distinctUntilChanged { old, new -> old === new }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val pantry: StateFlow<List<PantryItem>?> = householdId.follow(scope) { pantryRepository.observe(it) }
    val shopping: StateFlow<List<ShoppingItem>?> = householdId.follow(scope) { shoppingRepository.observe(it) }
    val ratings: StateFlow<Map<String, Rating>?> = uid.follow(scope) { ratingRepository.observe(it) }
    val cookHistory: StateFlow<List<CookEvent>?> = uid.follow(scope) { cookHistoryRepository.observe(it) }

    /** The household's own foods by id. */
    val customIngredients: StateFlow<Map<String, CustomIngredient>?> = householdId.follow(scope) { id ->
        customIngredientRepository.observe(id).map { list -> list.associateBy { it.id } }
    }

    private fun <T : Any> Flow<String?>.follow(scope: CoroutineScope, observe: (String) -> Flow<T>): StateFlow<T?> =
        flatMapLatest { id -> if (id == null) flowOf(null) else observe(id) }
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** What the household has, for [sk.matej.spajza.domain.matching.RecipeMatcher]. */
fun Session.kitchen(pantry: List<PantryItem>) = KitchenState(pantry, household.staples, household.equipment)

/** Everything about the user that shapes the ranking. */
fun Session.personalization(
    ratings: Map<String, Rating>,
    cookHistory: List<CookEvent>,
    targets: TargetCalculator = TargetCalculator(),
) = Personalization(
    preferences = profile.preferences,
    restrictions = profile.restrictions,
    goal = profile.body.goal,
    dailyKcal = targets.targets(profile.body)?.kcal,
    ratings = ratings,
    cookHistory = cookHistory,
)
