package sk.matej.spajza.domain.usecase

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.matching.KitchenState
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.plan.PlanMeal
import sk.matej.spajza.domain.plan.PlanShopping
import sk.matej.spajza.domain.plan.WeekId
import sk.matej.spajza.domain.repository.MealPlanRepository
import sk.matej.spajza.domain.repository.ShoppingRepository
import java.time.LocalDate

/** Plans a recipe for a meal on one or more days; meal prep spreads one recipe over several days. */
class AddToPlanUseCase(
    private val plans: MealPlanRepository,
    private val newId: () -> String = PlanEntry::newId,
) {
    suspend operator fun invoke(
        householdId: String,
        recipeId: String,
        dates: Collection<LocalDate>,
        meal: PlanMeal,
        servings: Int,
        addedBy: String,
    ): List<PlanEntry> {
        require(servings in PlanEntry.MIN_SERVINGS..PlanEntry.MAX_SERVINGS) { "servings out of range: $servings" }
        return dates.distinct().sorted().map { date ->
            PlanEntry(newId(), date, meal, recipeId, servings, addedBy).also { plans.add(householdId, it, addedBy) }
        }
    }
}

/** Moves an entry to another day or meal; across weeks it moves to the other week's document. */
class MovePlanEntryUseCase(private val plans: MealPlanRepository) {
    suspend operator fun invoke(householdId: String, entry: PlanEntry, date: LocalDate, meal: PlanMeal, movedBy: String) {
        if (WeekId.of(date) == entry.week) {
            plans.move(householdId, entry, date, meal)
        } else {
            plans.remove(householdId, entry)
            plans.add(householdId, entry.copy(date = date, meal = meal), entry.addedBy ?: movedBy)
        }
    }
}

/**
 * "Do nákupu": puts what the planned meals from [today] on still need on the shopping list, minus
 * the pantry, staples and what the list already holds (D13).
 */
class PlanToShoppingUseCase(private val shopping: ShoppingRepository) {
    /** Returns how many ingredients were added or topped up. */
    suspend operator fun invoke(
        householdId: String,
        catalog: Catalog,
        entries: Collection<PlanEntry>,
        kitchen: KitchenState,
        list: List<ShoppingItem>,
        today: LocalDate,
        addedBy: String,
    ): Int {
        val upcoming = entries.filterNot { it.date.isBefore(today) }
        val needed = PlanShopping.shortfall(catalog, upcoming, kitchen.pantry, kitchen.staples, list)
        needed.forEach { shopping.addIngredient(householdId, IngredientRef.Catalog(it.ingredientId), it.amount, it.recipeIds, addedBy) }
        return needed.size
    }
}
