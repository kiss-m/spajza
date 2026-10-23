package sk.matej.spajza.domain.session

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.profile.UserProfile
import sk.matej.spajza.domain.repository.AuthRepository
import sk.matej.spajza.domain.repository.AuthUser
import sk.matej.spajza.domain.repository.CatalogRepository
import sk.matej.spajza.domain.repository.HouseholdRepository
import sk.matej.spajza.domain.repository.UserRepository
import sk.matej.spajza.domain.usecase.CreateHouseholdUseCase

/** Who is signed in, with their profile and household. */
data class Session(val user: AuthUser, val profile: UserProfile, val household: Household) {
    val uid: String get() = user.uid
    val householdId: String get() = household.id
}

sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    /** Signed in with a household; onboarding is not finished yet. */
    data class Onboarding(val session: Session) : SessionState

    data class Ready(val session: Session) : SessionState
}

/**
 * The app-wide session. After the first sign-in, when the user document is confirmed missing, it
 * creates the household and the user document (once per user and process).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManager(
    auth: AuthRepository,
    private val users: UserRepository,
    private val households: HouseholdRepository,
    private val catalog: CatalogRepository,
    private val createHousehold: CreateHouseholdUseCase,
    private val defaultHouseholdName: () -> String,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val creationLock = Mutex()
    private val createdFor = mutableSetOf<String>()

    val state: StateFlow<SessionState> = auth.currentUser
        .flatMapLatest { user -> if (user == null) flowOf(SessionState.SignedOut) else sessionOf(user) }
        .flowOn(dispatcher)
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    private fun sessionOf(user: AuthUser): Flow<SessionState> = users.observe(user.uid).flatMapLatest { profile ->
        val householdId = profile?.householdId
        if (profile == null || householdId == null) {
            flow {
                emit(SessionState.Loading)
                ensureHousehold(user)
            }
        } else {
            households.observe(householdId).map { household ->
                when {
                    household == null -> SessionState.Loading
                    profile.onboardingDone -> SessionState.Ready(Session(user, profile, household))
                    else -> SessionState.Onboarding(Session(user, profile, household))
                }
            }
        }
    }

    private suspend fun ensureHousehold(user: AuthUser) = creationLock.withLock {
        if (createdFor.add(user.uid)) {
            createHousehold(user.uid, user.displayName, defaultHouseholdName(), catalog.catalog.first())
        }
    }
}
