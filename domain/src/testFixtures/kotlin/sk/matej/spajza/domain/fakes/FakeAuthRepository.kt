package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import sk.matej.spajza.domain.repository.AuthRepository
import sk.matej.spajza.domain.repository.AuthUser

class FakeAuthRepository(initial: AuthUser? = null) : AuthRepository {
    val user = MutableStateFlow(initial)
    var nextSignInResult: Result<AuthUser> = Result.success(AuthUser(uid = "uid-1", displayName = "Alex", email = "alex@example.com"))

    override val currentUser: Flow<AuthUser?> = user

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> =
        nextSignInResult.onSuccess { user.value = it }

    override suspend fun signOut() {
        user.value = null
    }
}
