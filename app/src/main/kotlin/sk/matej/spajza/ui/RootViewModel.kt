package sk.matej.spajza.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import sk.matej.spajza.domain.repository.AuthRepository
import sk.matej.spajza.domain.repository.AuthUser
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState

    data object SignedOut : RootState

    data class SignedIn(val user: AuthUser) : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(auth: AuthRepository) : ViewModel() {
    val state: StateFlow<RootState> = auth.currentUser
        .map { user -> if (user == null) RootState.SignedOut else RootState.SignedIn(user) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootState.Loading)
}
