package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.rating.CookEvent
import sk.matej.spajza.domain.repository.CookHistoryRepository

/** users/{uid}/cookHistory: what was cooked when, for preference learning (not a nutrition diary). */
class FirestoreCookHistoryRepository(private val db: FirebaseFirestore) : CookHistoryRepository {
    private fun collection(uid: String) =
        db.collection(Collections.USERS).document(uid).collection(Collections.COOK_HISTORY)

    override fun observe(uid: String): Flow<List<CookEvent>> = collection(uid)
        .orderBy("cookedAt", Query.Direction.DESCENDING)
        .limit(HISTORY_LIMIT)
        .snapshots()
        .map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                val recipeId = doc.getString("recipeId") ?: return@mapNotNull null
                val cookedAt = (doc.get("cookedAt") as? Timestamp)?.toInstant() ?: return@mapNotNull null
                CookEvent(doc.id, recipeId, cookedAt, (doc.get("servings") as? Number)?.toInt() ?: 1)
            }
        }
        .catch { error ->
            Log.w("SpajzaFirestore", "observing cook history failed", error)
            emit(emptyList())
        }

    override suspend fun add(uid: String, recipeId: String, servings: Int): String {
        val ref = collection(uid).document()
        // The device time, so the event is dated correctly even when it syncs later.
        ref.set(mapOf("recipeId" to recipeId, "cookedAt" to Timestamp.now(), "servings" to servings)).logFailure("logging cook event")
        return ref.id
    }

    override suspend fun remove(uid: String, eventId: String) {
        collection(uid).document(eventId).delete().logFailure("removing cook event")
    }

    private companion object {
        /** Recent history is enough for the taste profile. */
        const val HISTORY_LIMIT = 200L
    }
}
