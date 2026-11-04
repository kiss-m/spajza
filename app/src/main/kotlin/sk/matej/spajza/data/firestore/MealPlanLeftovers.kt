package sk.matej.spajza.data.firestore

import com.google.firebase.firestore.FirebaseFirestore

/**
 * Deletes the weekly meal plan documents (`households/{hid}/mealPlans`) left from the removed Plán
 * tab (D15). It runs once per household each time the app starts, so plans written by a member who
 * has not updated the app yet disappear too. Offline it deletes what the cache knows and the
 * deletions reach the server later.
 */
class MealPlanLeftovers(private val db: FirebaseFirestore) {
    private val cleaned = mutableSetOf<String>()

    fun delete(householdId: String) {
        synchronized(cleaned) { if (!cleaned.add(householdId)) return }
        db.collection(Collections.HOUSEHOLDS).document(householdId).collection(Collections.MEAL_PLANS).get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { it.reference.delete().logFailure("Deleting an old meal plan") }
            }
            .logFailure("Reading old meal plans")
    }
}
