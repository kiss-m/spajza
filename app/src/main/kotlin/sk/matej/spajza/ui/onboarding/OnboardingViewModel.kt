package sk.matej.spajza.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.profile.DailyTargets
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.TargetCalculator
import sk.matej.spajza.domain.repository.HouseholdRepository
import sk.matej.spajza.domain.repository.UserRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.usecase.JoinHouseholdUseCase
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.IngredientOptions
import sk.matej.spajza.ui.household.JoinFlow
import sk.matej.spajza.ui.profile.BodyDraft
import sk.matej.spajza.ui.profile.dislikedOptions
import sk.matej.spajza.ui.profile.likedOptions
import sk.matej.spajza.ui.profile.stapleOptions
import javax.inject.Inject

enum class OnboardingStep { HOUSEHOLD, BODY, TASTE, EQUIPMENT, STAPLES }

data class OnboardingUiState(
    val isLoading: Boolean = true,
    val displayName: String? = null,
    val step: OnboardingStep = OnboardingStep.HOUSEHOLD,
    /** Someone who joined a household skips its kitchen setup, which the others have done. */
    val steps: List<OnboardingStep> = OnboardingStep.entries,
    val householdName: String = "",
    val joinedHousehold: Boolean = false,
    val body: BodyDraft = BodyDraft(),
    val targets: DailyTargets? = null,
    val preferences: Preferences = Preferences(),
    val cuisines: List<Cuisine> = emptyList(),
    val liked: List<IngredientOption> = emptyList(),
    val disliked: List<IngredientOption> = emptyList(),
    val equipment: Set<Equipment> = emptySet(),
    val staples: List<IngredientOption> = emptyList(),
)

/**
 * Onboarding after the household exists. It starts with joining a housemate's household by code.
 * Body and taste are drafts saved by "Ďalej"; equipment and staples change the shared household at
 * once. Every step can be skipped.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val home: HomeData,
    private val users: UserRepository,
    private val households: HouseholdRepository,
    joinHousehold: JoinHouseholdUseCase,
) : ViewModel() {
    private data class Drafts(
        val step: OnboardingStep = OnboardingStep.HOUSEHOLD,
        val body: BodyDraft? = null,
        val preferences: Preferences? = null,
        val joined: Boolean = false,
    ) {
        val steps: List<OnboardingStep> get() = if (joined) JOINED_STEPS else OnboardingStep.entries
    }

    private val drafts = MutableStateFlow(Drafts())

    /** Joining a housemate's household with their code. */
    val join = JoinFlow(viewModelScope, joinHousehold) { home.session.value }
    private val targetCalculator = TargetCalculator()
    private var options: Pair<Catalog, IngredientOptions>? = null

    val state: StateFlow<OnboardingUiState> = combine(home.catalog, home.session, drafts) { catalog, session, current ->
        if (catalog == null || session == null) return@combine OnboardingUiState()
        val body = current.body ?: BodyDraft.of(session.profile.body)
        val preferences = current.preferences ?: session.profile.preferences
        OnboardingUiState(
            isLoading = false,
            displayName = session.user.displayName?.substringBefore(' '),
            step = current.step,
            steps = current.steps,
            householdName = session.household.name,
            joinedHousehold = current.joined,
            body = body,
            targets = targetCalculator.targets(body.toProfile()),
            preferences = preferences,
            cuisines = catalog.recipes.map { it.cuisine }.distinct().sorted(),
            liked = preferences.likedOptions(catalog),
            disliked = preferences.dislikedOptions(catalog),
            equipment = session.household.equipment,
            staples = stapleOptions(catalog, session.household.staples),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    init {
        // After joining, the kitchen setup belongs to the others; continue with the personal steps.
        viewModelScope.launch {
            join.state.map { it.joined != null }.distinctUntilChanged().filter { joined -> joined }.collect {
                drafts.update { current -> current.copy(step = OnboardingStep.BODY, joined = true) }
            }
        }
    }

    fun searchIngredients(query: String, exclude: Set<String>): List<IngredientOption> {
        val catalog = home.catalog.value ?: return emptyList()
        val cached = options?.takeIf { it.first === catalog }?.second ?: IngredientOptions(catalog).also { options = catalog to it }
        return cached.search(query, exclude)
    }

    fun onBodyChange(body: BodyDraft) = drafts.update { it.copy(body = body) }

    fun onPreferencesChange(preferences: Preferences) = drafts.update { it.copy(preferences = preferences) }

    fun onToggleEquipment(equipment: Equipment) {
        val session = home.session.value ?: return
        viewModelScope.launch {
            households.setEquipment(session.householdId, equipment, available = equipment !in session.household.equipment)
        }
    }

    fun onAddStaple(option: IngredientOption) = setStaple(option.ingredientId, true)

    fun onRemoveStaple(ingredientId: String) = setStaple(ingredientId, false)

    private fun setStaple(ingredientId: String, isStaple: Boolean) {
        val session = home.session.value ?: return
        viewModelScope.launch { households.setStaple(session.householdId, ingredientId, isStaple) }
    }

    /** Saves the current step and moves on; after the last step onboarding is done. */
    fun onNext() {
        val session = home.session.value ?: return
        val current = drafts.value
        viewModelScope.launch {
            when (current.step) {
                OnboardingStep.BODY -> current.body?.let { users.updateBody(session.uid, it.toProfile()) }
                OnboardingStep.TASTE -> current.preferences?.let { users.updatePreferences(session.uid, it) }
                OnboardingStep.HOUSEHOLD, OnboardingStep.EQUIPMENT, OnboardingStep.STAPLES -> Unit
            }
            advance()
        }
    }

    /** Moves on without saving this step's draft. */
    fun onSkip() {
        viewModelScope.launch { advance() }
    }

    fun onBack() = drafts.update { it.copy(step = it.steps[(it.steps.indexOf(it.step) - 1).coerceAtLeast(0)]) }

    private suspend fun advance() {
        val current = drafts.value
        val index = current.steps.indexOf(current.step)
        if (index == current.steps.lastIndex) {
            val session = home.session.value ?: return
            users.setOnboardingDone(session.uid)
        } else {
            drafts.update { it.copy(step = it.steps[index + 1]) }
        }
    }

    private companion object {
        val JOINED_STEPS = listOf(OnboardingStep.HOUSEHOLD, OnboardingStep.BODY, OnboardingStep.TASTE)
    }
}
