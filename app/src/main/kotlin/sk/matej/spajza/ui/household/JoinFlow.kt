package sk.matej.spajza.ui.household

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.matej.spajza.domain.household.Invite
import sk.matej.spajza.domain.household.InviteCodes
import sk.matej.spajza.domain.session.Session
import sk.matej.spajza.domain.usecase.JoinHouseholdUseCase
import sk.matej.spajza.domain.usecase.JoinOutcome

enum class JoinProblem { NOT_FOUND, OFFLINE, ALREADY_MEMBER, EXPIRED, FAILED }

data class JoinUiState(
    val code: String = "",
    val busy: Boolean = false,
    /** The household behind the code, waiting for the user to confirm. */
    val pending: Invite? = null,
    val problem: JoinProblem? = null,
    /** Set after joining until the screen has shown it. */
    val joined: JoinedHousehold? = null,
) {
    val canSubmit: Boolean get() = InviteCodes.isComplete(code) && !busy
}

data class JoinedHousehold(val name: String, val movedItems: Int)

/**
 * Typing a code, looking it up, confirming and joining. Shared by onboarding and the profile;
 * plain Kotlin, so it is tested without Android.
 */
class JoinFlow(
    private val scope: CoroutineScope,
    private val joinHousehold: JoinHouseholdUseCase,
    private val session: () -> Session?,
) {
    private val mutableState = MutableStateFlow(JoinUiState())
    val state: StateFlow<JoinUiState> = mutableState.asStateFlow()

    fun onCodeChange(text: String) = mutableState.update { JoinUiState(code = InviteCodes.normalize(text)) }

    fun onSubmit() {
        val code = mutableState.value.code
        val current = session()
        if (!InviteCodes.isComplete(code) || current == null) return
        mutableState.update { it.copy(busy = true, problem = null) }
        scope.launch {
            val result = joinHousehold.find(code)
            val invite = result.getOrNull()
            mutableState.update {
                when {
                    result.isFailure -> it.copy(busy = false, problem = JoinProblem.OFFLINE)
                    invite == null -> it.copy(busy = false, problem = JoinProblem.NOT_FOUND)
                    invite.householdId == current.householdId -> it.copy(busy = false, problem = JoinProblem.ALREADY_MEMBER)
                    else -> it.copy(busy = false, pending = invite)
                }
            }
        }
    }

    fun onConfirm() {
        val invite = mutableState.value.pending
        val current = session()
        if (invite == null || current == null) return
        mutableState.update { it.copy(busy = true, pending = null) }
        scope.launch {
            val outcome = joinHousehold(current, invite)
            mutableState.update {
                when (outcome) {
                    is JoinOutcome.Joined -> JoinUiState(joined = JoinedHousehold(invite.householdName, outcome.movedItems))
                    JoinOutcome.AlreadyMember -> it.copy(busy = false, problem = JoinProblem.ALREADY_MEMBER)
                    JoinOutcome.Expired -> it.copy(busy = false, problem = JoinProblem.EXPIRED)
                    JoinOutcome.Failed -> it.copy(busy = false, problem = JoinProblem.FAILED)
                }
            }
        }
    }

    fun onDismiss() = mutableState.update { it.copy(pending = null) }

    fun onJoinedShown() = mutableState.update { it.copy(joined = null) }
}
