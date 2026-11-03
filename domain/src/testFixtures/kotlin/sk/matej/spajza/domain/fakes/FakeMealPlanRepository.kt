package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.plan.PlanMeal
import sk.matej.spajza.domain.plan.WeekId
import sk.matej.spajza.domain.plan.WeekPlan
import sk.matej.spajza.domain.repository.MealPlanRepository
import java.time.LocalDate

/** Plan entries by household id; [initial] goes into [householdId]. */
class FakeMealPlanRepository(initial: List<PlanEntry> = emptyList(), householdId: String = "home") : MealPlanRepository {
    val households = MutableStateFlow(mapOf(householdId to initial.associateBy { it.id }))

    fun entriesOf(householdId: String = "home"): Map<String, PlanEntry> = households.value[householdId].orEmpty()

    override fun observe(householdId: String, week: WeekId): Flow<WeekPlan> =
        households.map { all -> WeekPlan(week, all[householdId].orEmpty().values.filter { it.week == week }) }

    override fun observeFrom(householdId: String, from: WeekId): Flow<List<WeekPlan>> = households.map { all ->
        all[householdId].orEmpty().values
            .filter { it.week >= from }
            .groupBy { it.week }
            .map { (week, entries) -> WeekPlan(week, entries) }
            .sortedBy { it.week }
    }

    override suspend fun add(householdId: String, entry: PlanEntry, addedBy: String) = edit(householdId) {
        it + (entry.id to entry.copy(addedBy = addedBy))
    }

    override suspend fun setServings(householdId: String, entry: PlanEntry, servings: Int) = edit(householdId) { entries ->
        val stored = entries[entry.id] ?: return@edit entries
        entries + (entry.id to stored.copy(servings = servings))
    }

    override suspend fun move(householdId: String, entry: PlanEntry, date: LocalDate, meal: PlanMeal) = edit(householdId) { entries ->
        require(WeekId.of(date) == entry.week) { "moves stay within the week" }
        val stored = entries[entry.id] ?: return@edit entries
        entries + (entry.id to stored.copy(date = date, meal = meal))
    }

    override suspend fun remove(householdId: String, entry: PlanEntry) = edit(householdId) { it - entry.id }

    private fun edit(householdId: String, change: (Map<String, PlanEntry>) -> Map<String, PlanEntry>) {
        val all = households.value
        households.value = all + (householdId to change(all[householdId].orEmpty()))
    }
}
