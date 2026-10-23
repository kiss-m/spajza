package sk.matej.spajza.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.matej.spajza.domain.repository.AuthRepository
import javax.inject.Inject

enum class SignInError { FAILED, NO_ACCOUNT }

data class SignInUiState(val isLoading: Boolean = false, val error: SignInError? = null)

@HiltViewModel
class SignInViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun onStarted() = _state.update { it.copy(isLoading = true, error = null) }

    fun onGoogleResult(result: GoogleSignInResult) {
        when (result) {
            is GoogleSignInResult.Token -> viewModelScope.launch {
                val outcome = auth.signInWithGoogle(result.idToken)
                _state.update { SignInUiState(isLoading = false, error = if (outcome.isSuccess) null else SignInError.FAILED) }
            }
            GoogleSignInResult.Cancelled -> _state.update { SignInUiState() }
            GoogleSignInResult.NoAccount -> _state.update { SignInUiState(error = SignInError.NO_ACCOUNT) }
            is GoogleSignInResult.Failed -> _state.update { SignInUiState(error = SignInError.FAILED) }
        }
    }
}
