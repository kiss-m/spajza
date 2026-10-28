package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.household.Invite
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.repository.HouseholdRepository
import java.time.Instant

class FirestoreHouseholdRepository(private val db: FirebaseFirestore) : HouseholdRepository {
    private fun doc(householdId: String) = db.collection(Collections.HOUSEHOLDS).document(householdId)

    override fun observe(householdId: String): Flow<Household?> = doc(householdId).snapshots(MetadataChanges.INCLUDE)
        // Missing in the cache only means it has not synced yet (a household just joined); waiting
        // for the server keeps the app on the previous household instead of a loading screen.
        .filter { it.exists() || !it.metadata.isFromCache }
        .map { snapshot ->
            if (!snapshot.exists()) return@map null
            val expires = (snapshot.get("inviteExpiresAt") as? Timestamp)?.toInstant()
            FirestoreMappers.householdFrom(snapshot.id, snapshot.data.orEmpty(), expires)
        }
        .distinctUntilChanged()
        .retryPermissionDenied()
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

    override suspend fun createInvite(invite: Invite, previousCode: String?) {
        val expiresAt = timestampOf(invite.expiresAt)
        invites().document(invite.code).set(
            mapOf(
                "householdId" to invite.householdId,
                "householdName" to invite.householdName.take(MAX_INVITE_NAME_LENGTH),
                "expiresAt" to expiresAt,
                "createdBy" to invite.createdBy,
            ),
        ).logFailure("creating invite")
        doc(invite.householdId).update(
            mapOf("inviteCode" to invite.code, "inviteExpiresAt" to expiresAt, FIELD_UPDATED_AT to serverTime()),
        ).logFailure("showing invite")
        if (previousCode != null && previousCode != invite.code) {
            invites().document(previousCode).delete().logFailure("withdrawing invite")
        }
    }

    override suspend fun findInvite(code: String): Result<Invite?> = runCatching {
        val data = invites().document(code).get(Source.SERVER).await().data ?: return@runCatching null
        val householdId = data["householdId"] as? String
        val expiresAt = (data["expiresAt"] as? Timestamp)?.toInstant()
        if (householdId == null || expiresAt == null) {
            null
        } else {
            Invite(
                code = code,
                householdId = householdId,
                householdName = data["householdName"] as? String ?: "",
                expiresAt = expiresAt,
                createdBy = data["createdBy"] as? String ?: "",
            )
        }
    }

    override suspend fun join(invite: Invite, uid: String, displayName: String?): Result<Unit> = runCatching {
        val ref = doc(invite.householdId)
        // A transaction fails at once without a connection, while a plain update would wait in the
        // offline queue and could join much later. The rules allow only these two fields here.
        db.runTransaction { transaction ->
            transaction.update(ref, mapOf("memberUids" to FieldValue.arrayUnion(uid), "joinCode" to invite.code))
            null
        }.await()
        if (displayName != null) {
            ref.update(mapOf("memberNames.$uid" to displayName, FIELD_UPDATED_AT to serverTime())).logFailure("adding member name")
        }
    }

    override suspend fun leave(householdId: String, uid: String) {
        // The rules allow a member to change only these two fields when leaving.
        doc(householdId).update(mapOf("memberUids" to FieldValue.arrayRemove(uid), "memberNames.$uid" to FieldValue.delete()))
            .logFailure("leaving household")
    }

    override suspend fun delete(householdId: String, inviteCode: String?) {
        val ref = doc(householdId)
        // The rules check membership through the household document, so it is deleted last.
        inviteCode?.let { invites().document(it).delete().logFailure("withdrawing invite") }
        Collections.HOUSEHOLD_SUBCOLLECTIONS.forEach { name ->
            val documents = runCatching { ref.collection(name).get().await().documents }.getOrDefault(emptyList())
            documents.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().logFailure("deleting $name")
            }
        }
        ref.delete().logFailure("deleting household")
    }

    private fun invites() = db.collection(Collections.INVITES)

    private fun timestampOf(instant: Instant) = Timestamp(instant.epochSecond, instant.nano)

    private companion object {
        /** Matches the security rules for invites. */
        const val MAX_INVITE_NAME_LENGTH = 60
        const val BATCH_LIMIT = 400
    }
}
