package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.custom.CustomIngredient
import sk.matej.spajza.domain.repository.CustomIngredientRepository

/** households/{hid}/customIngredients: barcode products use the id `b<barcode>` (D11). */
class FirestoreCustomIngredientRepository(private val db: FirebaseFirestore) : CustomIngredientRepository {
    private fun collection(householdId: String) =
        db.collection(Collections.HOUSEHOLDS).document(householdId).collection(Collections.CUSTOM_INGREDIENTS)

    override fun observe(householdId: String): Flow<List<CustomIngredient>> = collection(householdId).snapshots()
        .map { snapshot -> snapshot.documents.mapNotNull { FirestoreMappers.customIngredientFrom(it.id, it.data.orEmpty()) } }
        .retryPermissionDenied()
        .catch { error ->
            Log.w("SpajzaFirestore", "observing custom ingredients failed", error)
            emit(emptyList())
        }

    override suspend fun save(householdId: String, ingredient: CustomIngredient, savedBy: String) {
        val data = FirestoreMappers.customIngredientTo(ingredient) + mapOf("updatedBy" to savedBy, FIELD_UPDATED_AT to serverTime())
        // Merge, so two members editing different fields of one product both keep their change.
        collection(householdId).document(ingredient.id).set(data, SetOptions.merge()).logFailure("saving custom ingredient")
    }

    override suspend fun delete(householdId: String, id: String) {
        collection(householdId).document(id).delete().logFailure("deleting custom ingredient")
    }
}
