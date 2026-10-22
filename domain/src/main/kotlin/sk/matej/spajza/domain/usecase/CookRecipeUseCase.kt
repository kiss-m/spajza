package sk.matej.spajza.domain.usecase

import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.pantry.CookingDeduction
import sk.matej.spajza.domain.pantry.PantryChange
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.repository.CookHistoryRepository
import sk.matej.spajza.domain.repository.PantryRepository

/** What "Uvarené" did, so the snackbar's "Späť" can take it back. */
class CookUndo internal constructor(
    private val uid: String,
    private val householdId: String,
    private val eventId: String,
    private val pantryUndo: List<PantryChange>,
    private val history: CookHistoryRepository,
    private val pantry: PantryRepository,
) {
    val pantryChanges: Int get() = pantryUndo.size

    suspend fun undo() {
        if (pantryUndo.isNotEmpty()) pantry.apply(householdId, pantryUndo)
        history.remove(uid, eventId)
    }
}

/** "Uvarené": logs the cook event and deducts the used ingredients from the pantry. */
class CookRecipeUseCase(
    private val deduction: CookingDeduction,
    private val history: CookHistoryRepository,
    private val pantry: PantryRepository,
) {
    suspend operator fun invoke(
        uid: String,
        householdId: String,
        recipe: Recipe,
        servings: Int,
        currentPantry: List<PantryItem>,
        staples: Set<String>,
    ): CookUndo {
        val plan = deduction.plan(recipe, servings, currentPantry, staples)
        val eventId = history.add(uid, recipe.id, servings)
        if (!plan.isEmpty) pantry.apply(householdId, plan.changes)
        return CookUndo(uid, householdId, eventId, plan.undo, history, pantry)
    }
}
