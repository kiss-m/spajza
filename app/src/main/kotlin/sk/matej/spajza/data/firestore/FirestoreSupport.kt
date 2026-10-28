package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
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
    const val CUSTOM_INGREDIENTS = "customIngredients"
    const val RECIPES = "recipes"

    /** Every household subcollection the security rules allow, for deleting a household. */
    val HOUSEHOLD_SUBCOLLECTIONS = listOf(PANTRY, SHOPPING, RECIPES, CUSTOM_INGREDIENTS, "mealPlans")
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

/**
 * Retries a listener the server rejected, a few times with growing pauses. Right after a household
 * is created, a listener can reach the server before the creating write does, and the membership
 * rule then fails once; a user who really lost access gets the error after the last attempt.
 */
internal fun <T> Flow<T>.retryPermissionDenied(): Flow<T> = retryWhen { cause, attempt ->
    val retry = cause is FirebaseFirestoreException &&
        cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED &&
        attempt < MAX_LISTEN_RETRIES
    if (retry) delay(LISTEN_RETRY_STEP_MS * (attempt + 1))
    retry
}

private const val MAX_LISTEN_RETRIES = 5
private const val LISTEN_RETRY_STEP_MS = 1_000L
