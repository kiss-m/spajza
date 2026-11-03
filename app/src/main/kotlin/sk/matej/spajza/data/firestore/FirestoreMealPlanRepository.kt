package sk.matej.spajza.data.firestore

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.plan.PlanMeal
import sk.matej.spajza.domain.plan.WeekId
import sk.matej.spajza.domain.plan.WeekPlan
import sk.matej.spajza.domain.repository.MealPlanRepository
import java.time.LocalDate

/**
 * households/{hid}/mealPlans/{weekId} with entries in a map keyed by entry id (D13). Every write
 * merges into one entry's fields, so members editing the same week never overwrite each other.
 */
class FirestoreMealPlanRepository(private val db: FirebaseFirestore) : MealPlanRepository {
    private fun collection(householdId: String) =
        db.collection(Collections.HOUSEHOLDS).document(householdId).collection(Collections.MEAL_PLANS)

    private fun document(householdId: String, week: WeekId) = collection(householdId).document(week.toString())

    override fun observe(householdId: String, week: WeekId): Flow<WeekPlan> = document(householdId, week).snapshots()
        .map { snapshot ->
            snapshot.data?.let { FirestoreMappers.weekPlanFrom(snapshot.id, it.withInstants()) } ?: WeekPlan(week, emptyList())
        }
        .retryPermissionDenied()
        .catch { error ->
            Log.w("SpajzaFirestore", "observing meal plan failed", error)
            emit(WeekPlan(week, emptyList()))
        }

    override fun observeFrom(householdId: String, from: WeekId): Flow<List<WeekPlan>> = collection(householdId)
        // Week ids sort like the weeks themselves ("2026-W53" < "2027-W01").
        .whereGreaterThanOrEqualTo(FieldPath.documentId(), from.toString())
        .snapshots()
        .map { snapshot ->
            snapshot.documents.mapNotNull { FirestoreMappers.weekPlanFrom(it.id, it.data.orEmpty().withInstants()) }.sortedBy { it.week }
        }
        .retryPermissionDenied()
        .catch { error ->
            Log.w("SpajzaFirestore", "observing meal plans failed", error)
            emit(emptyList())
        }

    override suspend fun add(householdId: String, entry: PlanEntry, addedBy: String) {
        writeEntry(householdId, entry, FirestoreMappers.planEntryTo(entry, addedBy) + ("addedAt" to serverTime()), "adding to the meal plan")
    }

    override suspend fun setServings(householdId: String, entry: PlanEntry, servings: Int) {
        writeEntry(householdId, entry, mapOf("servings" to servings), "changing planned servings")
    }

    override suspend fun move(householdId: String, entry: PlanEntry, date: LocalDate, meal: PlanMeal) {
        require(WeekId.of(date) == entry.week) { "an entry moves to another week by remove and add" }
        writeEntry(householdId, entry, mapOf("date" to date.toString(), "meal" to meal.name), "moving a planned meal")
    }

    override suspend fun remove(householdId: String, entry: PlanEntry) {
        val data = mapOf("entries" to mapOf(entry.id to FieldValue.delete()), FIELD_UPDATED_AT to serverTime())
        document(householdId, entry.week).set(data, SetOptions.merge()).logFailure("removing a planned meal")
    }

    /** Merges [fields] into `entries.<id>`, leaving the entry's other fields and other entries alone. */
    private fun writeEntry(householdId: String, entry: PlanEntry, fields: Map<String, Any?>, what: String) {
        val data = mapOf("entries" to mapOf(entry.id to fields), FIELD_UPDATED_AT to serverTime())
        document(householdId, entry.week).set(data, SetOptions.merge()).logFailure(what)
    }
}
