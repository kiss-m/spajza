package sk.matej.spajza.domain.usecase

import kotlinx.coroutines.test.runTest
import sk.matej.spajza.domain.fakes.FakeCookHistoryRepository
import sk.matej.spajza.domain.fakes.FakePantryRepository
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.pantry.CookingDeduction
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CookRecipeUseCaseTest {
    private val start = listOf(
        PantryItem(IngredientRef.Catalog("pasta"), quantity = 500.0),
        PantryItem(IngredientRef.Catalog("tomato"), quantity = 2.0),
        PantryItem(IngredientRef.Catalog("garlic"), quantity = null),
    )
    private val pantry = FakePantryRepository(start)
    private val history = FakeCookHistoryRepository()
    private val cook = CookRecipeUseCase(CookingDeduction(MatcherFixture.catalog(MatcherFixture.tomatoPasta)), history, pantry)

    @Test
    fun `cooking logs history and deducts the pantry, undo restores both`() = runTest {
        val undo = cook("me", "home", MatcherFixture.tomatoPasta, servings = 2, currentPantry = start, staples = setOf("oil", "salt"))

        assertEquals(listOf("tomato-pasta"), history.events.value.map { it.recipeId })
        assertEquals(300.0, pantry.itemsOf().getValue("i_pasta").quantity)
        assertTrue("i_tomato" !in pantry.itemsOf(), "both tomatoes were used")
        assertEquals(null, pantry.itemsOf().getValue("i_garlic").quantity)

        undo.undo()
        assertEquals(emptyList(), history.events.value)
        assertEquals(start.associateBy { it.id }, pantry.itemsOf())
    }
}
