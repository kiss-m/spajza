package sk.matej.spajza.domain.pantry

import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.matching.MatcherFixture.catalog
import sk.matej.spajza.domain.matching.MatcherFixture.creamyChicken
import sk.matej.spajza.domain.matching.MatcherFixture.tomatoPasta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CookingDeductionTest {
    private val deduction = CookingDeduction(catalog(tomatoPasta, creamyChicken))
    private val staples = setOf("oil", "salt")

    private fun item(id: String, quantity: Double?) = PantryItem(IngredientRef.Catalog(id), quantity = quantity)

    @Test
    fun `subtracts used amounts scaled by servings`() {
        // 2-serving recipe cooked for 1: pasta 100 g, tomato 120 g = 1 piece, garlic 5 g.
        val pantry = listOf(item("pasta", 500.0), item("tomato", 3.0), item("garlic", 50.0))
        val plan = deduction.plan(tomatoPasta, servings = 1, pantry = pantry, staples = staples)
        assertEquals(
            setOf(
                PantryChange.Decrease("i_pasta", 100.0),
                PantryChange.Decrease("i_tomato", 1.0),
                PantryChange.Decrease("i_garlic", 5.0),
            ),
            plan.changes.toSet(),
        )
    }

    @Test
    fun `removes items that reach zero and undo restores them`() {
        val tomato = item("tomato", 2.0)
        val pantry = listOf(item("pasta", 150.0), tomato, item("garlic", 50.0))
        val plan = deduction.plan(tomatoPasta, servings = 2, pantry = pantry, staples = staples)
        assertTrue(PantryChange.Remove(tomato) in plan.changes, "2 tomatoes needed, 2 in the pantry")
        assertTrue(PantryChange.Remove(item("pasta", 150.0)) in plan.changes, "needs 200 g, only 150 g left")
        assertTrue(PantryChange.Restore(tomato) in plan.undo)
        assertTrue(PantryChange.Increase("i_garlic", 10.0) in plan.undo)
    }

    @Test
    fun `never touches staples, untracked items or optional lines`() {
        val pantry = listOf(item("pasta", null), item("tomato", 5.0), item("garlic", null), item("oil", 500.0), item("basil", 20.0))
        val plan = deduction.plan(tomatoPasta, servings = 2, pantry = pantry, staples = staples)
        assertEquals(listOf(PantryChange.Decrease("i_tomato", 2.0)), plan.changes)
    }

    @Test
    fun `missing ingredients are skipped`() {
        val plan = deduction.plan(tomatoPasta, servings = 2, pantry = listOf(item("pasta", 1000.0)), staples = staples)
        assertEquals(listOf(PantryChange.Decrease("i_pasta", 200.0)), plan.changes)
    }

    @Test
    fun `a substitute is deducted using its ratio`() {
        // creamy-chicken needs 30 g butter per 2 servings; oil substitutes 0.8 : 1 and oil is not a staple here.
        val pantry = listOf(item("chicken", 600.0), item("oil", 100.0))
        val plan = deduction.plan(creamyChicken, servings = 2, pantry = pantry, staples = emptySet())
        val oilMl = 30 * 0.8 / 0.92
        val oil = plan.changes.filterIsInstance<PantryChange.Decrease>().single { it.itemId == "i_oil" }
        assertEquals(oilMl, oil.amount, 1e-9)
    }

    @Test
    fun `undo mirrors every change`() {
        val pantry = listOf(item("pasta", 500.0), item("tomato", 2.0), item("garlic", 50.0))
        val plan = deduction.plan(tomatoPasta, servings = 2, pantry = pantry, staples = staples)
        assertEquals(plan.changes.size, plan.undo.size)
    }

    @Test
    fun `fixture sanity`() {
        assertEquals(2, MatcherFixture.tomatoPasta.servings)
    }
}
