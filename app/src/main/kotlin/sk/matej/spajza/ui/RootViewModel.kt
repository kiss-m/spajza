package sk.matej.spajza.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import sk.matej.spajza.domain.session.SessionManager
import sk.matej.spajza.domain.session.SessionState
import sk.matej.spajza.ui.navigation.AppLink
import sk.matej.spajza.ui.navigation.AppLinks
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(sessions: SessionManager, private val links: AppLinks) : ViewModel() {
    val state: StateFlow<SessionState> = sessions.state

    /** Where a notification or another app asked to go, until the main screen has gone there. */
    val pendingLink: StateFlow<AppLink?> = links.pending

    fun onLinkHandled(link: AppLink) = links.consume(link)
}
