package sk.matej.spajza.ui.recipe

import sk.matej.spajza.domain.pantry.CookingDeduction
import sk.matej.spajza.domain.repository.CookHistoryRepository
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.usecase.CookRecipeUseCase
import sk.matej.spajza.domain.usecase.CookUndo
import javax.inject.Inject

/** "Uvarené" for the detail screen and cooking mode; callers keep the pantry flow collected. */
class RecipeCooking @Inject constructor(
    private val home: HomeData,
    private val history: CookHistoryRepository,
    private val pantry: PantryRepository,
) {
    suspend fun cook(recipeId: String, servings: Int): CookUndo? {
        val catalog = home.catalog.value ?: return null
        val session = home.session.value ?: return null
        val recipe = catalog.recipesById[recipeId] ?: return null
        return CookRecipeUseCase(CookingDeduction(catalog), history, pantry)(
            uid = session.uid,
            householdId = session.householdId,
            recipe = recipe,
            servings = servings,
            currentPantry = home.pantry.value.orEmpty(),
            staples = session.household.staples,
        )
    }
}
