package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.repository.HouseholdRepository

class FirestoreHouseholdRepository(private val db: FirebaseFirestore) : HouseholdRepository {
    private fun doc(householdId: String) = db.collection(Collections.HOUSEHOLDS).document(householdId)

    override fun observe(householdId: String): Flow<Household?> = doc(householdId).snapshots()
        .map { snapshot ->
            if (!snapshot.exists()) return@map null
            val expires = (snapshot.get("inviteExpiresAt") as? Timestamp)?.toInstant()
            FirestoreMappers.householdFrom(snapshot.id, snapshot.data.orEmpty(), expires)
        }
        .distinctUntilChanged()
        .catch { error ->
            // Reading fails once the user is no longer a member.
            Log.w("SpajzaFirestore", "observing household failed", error)
            emit(null)
        }

    override suspend fun create(
        name: String,
        ownerUid: String,
        ownerName: String?,
        staples: Set<String>,
        equipment: Set<Equipment>,
    ): String {
        val ref = db.collection(Collections.HOUSEHOLDS).document()
        val data = FirestoreMappers.newHousehold(name, ownerUid, ownerName, staples, equipment) +
            mapOf(FIELD_CREATED_AT to serverTime(), FIELD_UPDATED_AT to serverTime())
        ref.set(data).logFailure("creating household")
        return ref.id
    }

    override suspend fun setStaple(householdId: String, ingredientId: String, isStaple: Boolean) {
        val change = if (isStaple) FieldValue.arrayUnion(ingredientId) else FieldValue.arrayRemove(ingredientId)
        doc(householdId).update("staples", change, FIELD_UPDATED_AT, serverTime()).logFailure("updating staples")
    }

    override suspend fun setEquipment(householdId: String, equipment: Equipment, available: Boolean) {
        val change = if (available) FieldValue.arrayUnion(equipment.name) else FieldValue.arrayRemove(equipment.name)
        doc(householdId).update("equipment", change, FIELD_UPDATED_AT, serverTime()).logFailure("updating equipment")
    }
}
