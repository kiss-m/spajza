package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.repository.ShoppingRepository

/** households/{hid}/shopping: ingredient items use the pantry's deterministic ids, text items random ones. */
class FirestoreShoppingRepository(private val db: FirebaseFirestore) : ShoppingRepository {
    private fun collection(householdId: String) =
        db.collection(Collections.HOUSEHOLDS).document(householdId).collection(Collections.SHOPPING)

    override fun observe(householdId: String): Flow<List<ShoppingItem>> = collection(householdId).snapshots()
        .map { snapshot -> snapshot.documents.mapNotNull { FirestoreMappers.shoppingItemFrom(it.id, it.data.orEmpty()) } }
        .retryPermissionDenied()
        .catch { error ->
            Log.w("SpajzaFirestore", "observing shopping list failed", error)
            emit(emptyList())
        }

    override suspend fun addIngredient(
        householdId: String,
        ingredient: IngredientRef,
        amount: Double?,
        reasonRecipeIds: Set<String>,
        addedBy: String,
    ) {
        val ref = collection(householdId).document(ingredient.docId)
        val cached = ref.cachedData()
        val data = FirestoreMappers.ingredientRefTo(ingredient).toMutableMap()
        data["checked"] = false
        data[FIELD_UPDATED_AT] = serverTime()
        if (cached == null) {
            data["addedBy"] = addedBy
            data[FIELD_CREATED_AT] = serverTime()
        }
        if (cached?.get("checked") == true) {
            // A bought item that is needed again starts over instead of adding to the old amount.
            data["amount"] = amount
            data["reasonRecipeIds"] = reasonRecipeIds.sorted()
        } else {
            if (amount != null) data["amount"] = FieldValue.increment(amount)
            if (reasonRecipeIds.isNotEmpty()) data["reasonRecipeIds"] = FieldValue.arrayUnion(*reasonRecipeIds.toTypedArray())
        }
        ref.set(data, SetOptions.merge()).logFailure("adding to shopping list")
    }

    override suspend fun addText(householdId: String, text: String, addedBy: String) {
        val data = mapOf(
            "text" to text,
            "checked" to false,
            "addedBy" to addedBy,
            FIELD_CREATED_AT to serverTime(),
            FIELD_UPDATED_AT to serverTime(),
        )
        collection(householdId).document().set(data).logFailure("adding text to shopping list")
    }

    override suspend fun setChecked(householdId: String, itemId: String, checked: Boolean) {
        collection(householdId).document(itemId).update("checked", checked, FIELD_UPDATED_AT, serverTime())
            .logFailure("checking shopping item")
    }

    override suspend fun remove(householdId: String, itemIds: Collection<String>) {
        itemIds.forEach { collection(householdId).document(it).delete().logFailure("removing shopping item") }
    }
}
