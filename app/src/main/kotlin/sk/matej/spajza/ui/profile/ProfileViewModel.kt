package sk.matej.spajza.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.pantry.ExpiryReminder
import sk.matej.spajza.domain.profile.DailyTargets
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.TargetCalculator
import sk.matej.spajza.domain.repository.AccountRepository
import sk.matej.spajza.domain.repository.AuthRepository
import sk.matej.spajza.domain.repository.DeviceSettingsRepository
import sk.matej.spajza.domain.repository.ExpiryReminderScheduler
import sk.matej.spajza.domain.repository.HouseholdRepository
import sk.matej.spajza.domain.repository.UserRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.usecase.CreateInviteUseCase
import sk.matej.spajza.domain.usecase.JoinHouseholdUseCase
import sk.matej.spajza.domain.usecase.LeaveHouseholdUseCase
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.IngredientOptions
import sk.matej.spajza.ui.household.HouseholdUiState
import sk.matej.spajza.ui.household.InviteUi
import sk.matej.spajza.ui.household.JoinFlow
import java.time.Clock
import javax.inject.Inject

enum class DeletionState { IDLE, DELETING, FAILED }

data class ProfileUiState(
    val isLoading: Boolean = true,
    val accountLabel: String = "",
    val body: BodyDraft = BodyDraft(),
    /** The body draft differs from what is saved. */
    val bodyChanged: Boolean = false,
    val targets: DailyTargets? = null,
    val preferences: Preferences = Preferences(),
    val cuisines: List<Cuisine> = emptyList(),
    val liked: List<IngredientOption> = emptyList(),
    val disliked: List<IngredientOption> = emptyList(),
    val equipment: Set<Equipment> = emptySet(),
    val staples: List<IngredientOption> = emptyList(),
    val household: HouseholdUiState = HouseholdUiState(),
    val expiryReminder: ExpiryReminder = ExpiryReminder(),
    val deletion: DeletionState = DeletionState.IDLE,
)

/** Body details save with a button; every other change saves at once. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val home: HomeData,
    private val users: UserRepository,
    private val households: HouseholdRepository,
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val createInvite: CreateInviteUseCase,
    private val leaveHousehold: LeaveHouseholdUseCase,
    private val clock: Clock,
    private val deviceSettings: DeviceSettingsRepository,
    private val reminderScheduler: ExpiryReminderScheduler,
    joinHousehold: JoinHouseholdUseCase,
) : ViewModel() {
    /** Joining another household with a code from a housemate. */
    val join = JoinFlow(viewModelScope, joinHousehold) { home.session.value }

    private val bodyDraft = MutableStateFlow<BodyDraft?>(null)
    private val deletion = MutableStateFlow(DeletionState.IDLE)
    private val targetCalculator = TargetCalculator()
    private var options: Pair<Catalog, IngredientOptions>? = null

    val state: StateFlow<ProfileUiState> = combine(
        home.catalog,
        home.session,
        bodyDraft,
        deletion,
        deviceSettings.expiryReminder,
    ) { catalog, session, draft, deletionState, reminder ->
        if (catalog == null || session == null) return@combine ProfileUiState(deletion = deletionState)
        val saved = session.profile.body
        val body = draft ?: BodyDraft.of(saved)
        val preferences = session.profile.preferences
        val household = session.household
        ProfileUiState(
            isLoading = false,
            accountLabel = session.user.email ?: session.user.displayName ?: session.uid,
            body = body,
            bodyChanged = draft != null && draft.toProfile() != saved,
            targets = targetCalculator.targets(body.toProfile()),
            preferences = preferences,
            cuisines = catalog.recipes.map { it.cuisine }.distinct().sorted(),
            liked = preferences.likedOptions(catalog),
            disliked = preferences.dislikedOptions(catalog),
            equipment = household.equipment,
            staples = stapleOptions(catalog, household.staples),
            household = HouseholdUiState(
                name = household.name,
                members = household.memberUids.map { household.memberNames[it] ?: "?" },
                isShared = household.memberUids.any { it != session.uid },
                invite = household.inviteCode?.let { code ->
                    household.inviteExpiresAt?.takeIf { it.isAfter(clock.instant()) }?.let { InviteUi(code, it) }
                },
            ),
            deletion = deletionState,
            expiryReminder = reminder,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun searchIngredients(query: String, exclude: Set<String>): List<IngredientOption> {
        val catalog = home.catalog.value ?: return emptyList()
        val cached = options?.takeIf { it.first === catalog }?.second ?: IngredientOptions(catalog).also { options = catalog to it }
        return cached.search(query, exclude)
    }

    fun onBodyChange(draft: BodyDraft) {
        bodyDraft.value = draft
    }

    fun onSaveBody() {
        val draft = bodyDraft.value ?: return
        val session = home.session.value ?: return
        viewModelScope.launch {
            users.updateBody(session.uid, draft.toProfile())
            bodyDraft.value = null
        }
    }

    fun onPreferencesChange(preferences: Preferences) {
        val session = home.session.value ?: return
        viewModelScope.launch { users.updatePreferences(session.uid, preferences) }
    }

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

    fun onExpiryReminderChange(reminder: ExpiryReminder) {
        viewModelScope.launch {
            deviceSettings.setExpiryReminder(reminder)
            reminderScheduler.apply(reminder)
        }
    }

    fun onCreateInvite() {
        val session = home.session.value ?: return
        viewModelScope.launch { createInvite(session) }
    }

    /** The user starts a new household named [newHouseholdName]; the shared one keeps its data. */
    fun onLeaveHousehold(newHouseholdName: String) {
        val session = home.session.value ?: return
        val catalog = home.catalog.value ?: return
        viewModelScope.launch { leaveHousehold(session, catalog, newHouseholdName) }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    /** [idToken] comes from signing in with Google again; Firebase deletes only fresh sign-ins. */
    fun onDeleteAccount(idToken: String) {
        val session = home.session.value ?: return
        deletion.value = DeletionState.DELETING
        viewModelScope.launch {
            val result = account.deleteAccount(session.uid, idToken)
            // On success the auth state changes and the app returns to sign-in.
            if (result.isFailure) deletion.value = DeletionState.FAILED
        }
    }

    fun onDeletionErrorShown() {
        deletion.value = DeletionState.IDLE
    }
}
