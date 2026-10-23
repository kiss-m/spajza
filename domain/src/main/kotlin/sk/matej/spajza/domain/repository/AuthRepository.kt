package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow

data class AuthUser(val uid: String, val displayName: String?, val email: String?)

interface AuthRepository {
    /** The signed-in user, or null; emits on every change. */
    val currentUser: Flow<AuthUser?>

    /** Signs in with a Google ID token obtained on the device. */
    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>

    suspend fun signOut()
}
