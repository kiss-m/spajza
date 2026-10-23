package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.profile.BodyProfile
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.UserProfile
import sk.matej.spajza.domain.repository.UserRepository

class FirestoreUserRepository(private val db: FirebaseFirestore) : UserRepository {
    private fun doc(uid: String) = db.collection(Collections.USERS).document(uid)

    override fun observe(uid: String): Flow<UserProfile?> = doc(uid).snapshots(MetadataChanges.INCLUDE)
        // "Missing" straight from the cache may only mean a new phone that has not synced yet;
        // the session would then create a second household, so wait for the server's answer.
        .filter { it.exists() || !it.metadata.isFromCache }
        .map { snapshot -> if (snapshot.exists()) FirestoreMappers.userFrom(uid, snapshot.data.orEmpty()) else null }
        .distinctUntilChanged()
        .catch { Log.w("SpajzaFirestore", "observing user failed", it) }

    override suspend fun create(uid: String, displayName: String?, householdId: String) {
        // Merge, so an existing document keeps its preferences; missing fields read as defaults.
        val data = mapOf(
            "displayName" to displayName,
            "householdId" to householdId,
            "onboardingDone" to false,
            FIELD_CREATED_AT to serverTime(),
            FIELD_UPDATED_AT to serverTime(),
        )
        doc(uid).set(data, SetOptions.merge()).logFailure("creating user")
    }

    override suspend fun updateBody(uid: String, body: BodyProfile) {
        doc(uid).update(mapOf("profile" to FirestoreMappers.bodyTo(body), FIELD_UPDATED_AT to serverTime()))
            .logFailure("updating profile")
    }

    override suspend fun updatePreferences(uid: String, preferences: Preferences) {
        doc(uid).update(mapOf("preferences" to FirestoreMappers.preferencesTo(preferences), FIELD_UPDATED_AT to serverTime()))
            .logFailure("updating preferences")
    }

    override suspend fun setOnboardingDone(uid: String) {
        doc(uid).update(mapOf("onboardingDone" to true, FIELD_UPDATED_AT to serverTime())).logFailure("finishing onboarding")
    }
}
