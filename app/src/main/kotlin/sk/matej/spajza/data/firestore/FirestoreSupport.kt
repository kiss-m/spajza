package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/** Firestore collection names; see docs/SPEC.md and docs/DECISIONS.md for the structure. */
internal object Collections {
    const val USERS = "users"
    const val HOUSEHOLDS = "households"
    const val PANTRY = "pantry"
    const val SHOPPING = "shopping"
    const val RATINGS = "ratings"
    const val COOK_HISTORY = "cookHistory"
    const val INVITES = "invites"

    /** Every household subcollection the security rules allow, for deleting a household. */
    val HOUSEHOLD_SUBCOLLECTIONS = listOf(PANTRY, SHOPPING, "recipes", "customIngredients", "mealPlans")
}

internal const val FIELD_UPDATED_AT = "updatedAt"
internal const val FIELD_CREATED_AT = "createdAt"

internal fun serverTime(): FieldValue = FieldValue.serverTimestamp()

/**
 * Offline-first writes: the local cache applies a write at once and the task completes only when
 * the server confirms it, which may be much later. So writes are never awaited; failures (for
 * example a rejected write) are only logged.
 */
internal fun Task<*>.logFailure(what: String) {
    addOnFailureListener { Log.w("SpajzaFirestore", "$what failed", it) }
}

/** The document as the local cache has it right now, or null when it is not cached. */
internal suspend fun DocumentReference.cachedData(): Map<String, Any?>? =
    runCatching { get(Source.CACHE).await() }.getOrNull()?.takeIf { it.exists() }?.data
