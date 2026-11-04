package sk.matej.spajza.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import sk.matej.spajza.data.firestore.MealPlanLeftovers
import sk.matej.spajza.domain.session.SessionManager
import sk.matej.spajza.domain.session.SessionState
import sk.matej.spajza.ui.navigation.AppLink
import sk.matej.spajza.ui.navigation.AppLinks
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    sessions: SessionManager,
    private val links: AppLinks,
    mealPlanLeftovers: MealPlanLeftovers,
) : ViewModel() {
    val state: StateFlow<SessionState> = sessions.state

    /** Where a notification or another app asked to go, until the main screen has gone there. */
    val pendingLink: StateFlow<AppLink?> = links.pending

    init {
        // The removed weekly plan may have left documents in the household (D15).
        sessions.state
            .mapNotNull { (it as? SessionState.Ready)?.session?.householdId }
            .distinctUntilChanged()
            .onEach(mealPlanLeftovers::delete)
            .launchIn(viewModelScope)
    }

    fun onLinkHandled(link: AppLink) = links.consume(link)
}
