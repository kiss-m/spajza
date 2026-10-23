package sk.matej.spajza.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import sk.matej.spajza.domain.session.SessionManager
import sk.matej.spajza.domain.session.SessionState
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(sessions: SessionManager) : ViewModel() {
    val state: StateFlow<SessionState> = sessions.state
}
