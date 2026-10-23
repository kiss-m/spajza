package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryChange
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.repository.PantryRepository
import java.time.LocalDate

/**
 * households/{hid}/pantry with one document per ingredient (D2): adding an ingredient that is
 * already there merges into its document, and quantities only ever change by increments.
 */
class FirestorePantryRepository(private val db: FirebaseFirestore) : PantryRepository {
    private fun collection(householdId: String) =
        db.collection(Collections.HOUSEHOLDS).document(householdId).collection(Collections.PANTRY)

    override fun observe(householdId: String): Flow<List<PantryItem>> = collection(householdId).snapshots()
        .map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                FirestoreMappers.pantryItemFrom(doc.data.orEmpty(), (doc.get(FIELD_UPDATED_AT) as? Timestamp)?.toInstant())
            }
        }
        .catch { error ->
            Log.w("SpajzaFirestore", "observing pantry failed", error)
            emit(emptyList())
        }

    override suspend fun add(householdId: String, ingredient: IngredientRef, quantity: Double?, expiresAt: LocalDate?, addedBy: String) {
        val ref = collection(householdId).document(ingredient.docId)
        val cached = ref.cachedData()
        val data = FirestoreMappers.ingredientRefTo(ingredient).toMutableMap()
        data[FIELD_UPDATED_AT] = serverTime()
        if (quantity != null) data["quantity"] = FieldValue.increment(quantity)
        // Best effort with the local copy; Firestore has no "minimum" transform.
        FirestoreMappers.earlierExpiry(cached, expiresAt)?.let { data["expiresAt"] = it.toString() }
        if (cached == null) data["addedBy"] = addedBy
        ref.set(data, SetOptions.merge()).logFailure("adding to pantry")
    }

    override suspend fun remove(householdId: String, itemId: String) {
        collection(householdId).document(itemId).delete().logFailure("removing from pantry")
    }

    override suspend fun setQuantity(householdId: String, itemId: String, current: Double?, new: Double?) {
        val value = if (new == null) FieldValue.delete() else FieldValue.increment(new - (current ?: 0.0))
        collection(householdId).document(itemId).update("quantity", value, FIELD_UPDATED_AT, serverTime())
            .logFailure("setting pantry quantity")
    }

    override suspend fun setExpiry(householdId: String, itemId: String, expiresAt: LocalDate?) {
        val value: Any = expiresAt?.toString() ?: FieldValue.delete()
        collection(householdId).document(itemId).update("expiresAt", value, FIELD_UPDATED_AT, serverTime())
            .logFailure("setting pantry expiry")
    }

    override suspend fun apply(householdId: String, changes: List<PantryChange>) {
        // Separate writes, not a batch: an item another member already removed must not make
        // the other changes fail with it.
        changes.forEach { change ->
            when (change) {
                is PantryChange.Decrease -> collection(householdId).document(change.itemId)
                    .update("quantity", FieldValue.increment(-change.amount), FIELD_UPDATED_AT, serverTime())
                is PantryChange.Increase -> collection(householdId).document(change.itemId)
                    .update("quantity", FieldValue.increment(change.amount), FIELD_UPDATED_AT, serverTime())
                is PantryChange.Remove -> collection(householdId).document(change.item.id).delete()
                is PantryChange.Restore -> collection(householdId).document(change.item.id)
                    .set(FirestoreMappers.pantryItemTo(change.item) + (FIELD_UPDATED_AT to serverTime()))
            }.logFailure("changing pantry")
        }
    }
}
