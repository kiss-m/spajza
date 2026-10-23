package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.profile.BodyProfile
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.UserProfile

/**
 * The signed-in user's own document. Writes apply to the local cache at once and sync later, so
 * callers never wait for the server.
 */
interface UserRepository {
    /** Emits null while the user document does not exist yet (before onboarding creates it). */
    fun observe(uid: String): Flow<UserProfile?>

    /** Creates the user document with default preferences, linked to [householdId]. */
    suspend fun create(uid: String, displayName: String?, householdId: String)

    suspend fun updateBody(uid: String, body: BodyProfile)

    suspend fun updatePreferences(uid: String, preferences: Preferences)

    suspend fun setOnboardingDone(uid: String)
}
