package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.rating.Verdict
import sk.matej.spajza.domain.repository.RatingRepository

class FirestoreRatingRepository(private val db: FirebaseFirestore) : RatingRepository {
    private fun collection(uid: String) =
        db.collection(Collections.USERS).document(uid).collection(Collections.RATINGS)

    override fun observe(uid: String): Flow<Map<String, Rating>> = collection(uid).snapshots()
        .map { snapshot -> snapshot.documents.associate { it.id to FirestoreMappers.ratingFrom(it.id, it.data.orEmpty()) } }
        .catch { error ->
            Log.w("SpajzaFirestore", "observing ratings failed", error)
            emit(emptyMap())
        }

    override suspend fun setVerdict(uid: String, recipeId: String, verdict: Verdict?) {
        collection(uid).document(recipeId)
            .set(mapOf("verdict" to verdict?.name, FIELD_UPDATED_AT to serverTime()), SetOptions.merge())
            .logFailure("rating recipe")
    }

    override suspend fun setFavorite(uid: String, recipeId: String, favorite: Boolean) {
        collection(uid).document(recipeId)
            .set(mapOf("favorite" to favorite, FIELD_UPDATED_AT to serverTime()), SetOptions.merge())
            .logFailure("marking favorite")
    }
}
