package sk.matej.spajza.data.account

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import sk.matej.spajza.data.firestore.Collections
import sk.matej.spajza.domain.repository.AccountRepository

/**
 * Deletes everything the user owns. Unlike normal writes these calls are awaited: deletion needs
 * a connection, and the account itself is deleted only after all data is gone.
 */
class FirebaseAccountRepository(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : AccountRepository {

    override suspend fun deleteAccount(uid: String, idToken: String): Result<Unit> = runCatching {
        val user = auth.currentUser?.takeIf { it.uid == uid } ?: error("Not signed in as $uid")
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()

        val userRef = db.collection(Collections.USERS).document(uid)
        val householdId = userRef.get(Source.SERVER).await().getString("householdId")
        deleteAll(userRef.collection(Collections.RATINGS))
        deleteAll(userRef.collection(Collections.COOK_HISTORY))
        if (householdId != null) leaveOrDeleteHousehold(uid, householdId)
        userRef.delete().await()

        user.delete().await()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        Unit
    }

    private suspend fun leaveOrDeleteHousehold(uid: String, householdId: String) {
        val householdRef = db.collection(Collections.HOUSEHOLDS).document(householdId)
        val household = householdRef.get(Source.SERVER).await()
        if (!household.exists()) return
        val members = (household.get("memberUids") as? List<*>).orEmpty()
        if (members == listOf(uid)) {
            Collections.HOUSEHOLD_SUBCOLLECTIONS.forEach { deleteAll(householdRef.collection(it)) }
            // The rules check membership through the household, so its invite goes first.
            household.getString("inviteCode")?.let { code ->
                runCatching { db.collection(Collections.INVITES).document(code).delete().await() }
            }
            householdRef.delete().await()
        } else {
            householdRef.update(
                mapOf("memberUids" to FieldValue.arrayRemove(uid), "memberNames.$uid" to FieldValue.delete()),
            ).await()
        }
    }

    private suspend fun deleteAll(collection: CollectionReference) {
        while (true) {
            val page = collection.limit(PAGE_SIZE).get(Source.SERVER).await()
            if (page.isEmpty) return
            val batch = db.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private companion object {
        const val PAGE_SIZE = 200L
    }
}
