package sk.matej.spajza.domain.repository

/** In-app account and data deletion (required for Google Play). Needs a connection. */
interface AccountRepository {
    /**
     * Signs in again with a fresh Google [idToken] (Firebase only deletes recently signed-in
     * accounts), then deletes the user's document with ratings and cook history, deletes the
     * household when the user is its last member (otherwise leaves it) and finally the account.
     */
    suspend fun deleteAccount(uid: String, idToken: String): Result<Unit>
}
