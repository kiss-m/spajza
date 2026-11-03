package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.plan.PlanMeal
import sk.matej.spajza.domain.plan.WeekId
import sk.matej.spajza.domain.plan.WeekPlan
import java.time.LocalDate

/**
 * The household's shared weekly meal plan (D13). Every change touches a single entry, so members
 * editing the same week never overwrite each other.
 */
interface MealPlanRepository {
    /** The week's entries; an empty plan when nothing is planned. */
    fun observe(householdId: String, week: WeekId): Flow<WeekPlan>

    /** Every planned week from [from] on, oldest first; for bringing a plan into another household. */
    fun observeFrom(householdId: String, from: WeekId): Flow<List<WeekPlan>>

    suspend fun add(householdId: String, entry: PlanEntry, addedBy: String)

    suspend fun setServings(householdId: String, entry: PlanEntry, servings: Int)

    /** Moves [entry] to another day or meal of the same week. */
    suspend fun move(householdId: String, entry: PlanEntry, date: LocalDate, meal: PlanMeal)

    suspend fun remove(householdId: String, entry: PlanEntry)
}
