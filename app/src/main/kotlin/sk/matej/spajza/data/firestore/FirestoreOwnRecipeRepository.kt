package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.repository.OwnRecipeRepository

/** households/{hid}/recipes: one document per own recipe, lines and steps as arrays (D12). */
class FirestoreOwnRecipeRepository(private val db: FirebaseFirestore) : OwnRecipeRepository {
    private fun collection(householdId: String) =
        db.collection(Collections.HOUSEHOLDS).document(householdId).collection(Collections.RECIPES)

    override fun observe(householdId: String): Flow<List<OwnRecipe>> = collection(householdId).snapshots()
        .map { snapshot -> snapshot.documents.mapNotNull { FirestoreMappers.ownRecipeFrom(it.id, it.data.orEmpty()) } }
        .retryPermissionDenied()
        .catch { error ->
            Log.w("SpajzaFirestore", "observing own recipes failed", error)
            emit(emptyList())
        }

    override suspend fun save(householdId: String, recipe: OwnRecipe, savedBy: String) {
        val data = FirestoreMappers.ownRecipeTo(recipe) + mapOf("updatedBy" to savedBy, FIELD_UPDATED_AT to serverTime())
        collection(householdId).document(recipe.id).set(data, SetOptions.merge()).logFailure("saving own recipe")
    }

    override suspend fun delete(householdId: String, id: String) {
        collection(householdId).document(id).delete().logFailure("deleting own recipe")
    }
}
