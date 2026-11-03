package sk.matej.spajza.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.fakes.FakeMealPlanRepository
import sk.matej.spajza.domain.fakes.FakeShoppingRepository
import sk.matej.spajza.domain.matching.KitchenState
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.plan.PlanMeal
import sk.matej.spajza.domain.plan.WeekId
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.line
import sk.matej.spajza.domain.testing.recipe
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MealPlanUseCasesTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val plans = FakeMealPlanRepository()

    @Test
    fun `adding a recipe to several days creates one entry per day`() = runTest {
        var next = 0
        val add = AddToPlanUseCase(plans, newId = { "e${++next}" })
        val added = add("home", "lecso", listOf(monday, monday.plusDays(2)), PlanMeal.LUNCH, servings = 3, addedBy = "me")

        assertEquals(listOf("e1", "e2"), added.map { it.id })
        val stored = plans.entriesOf()
        assertEquals(setOf(monday, monday.plusDays(2)), stored.values.map { it.date }.toSet())
        assertTrue(stored.values.all { it.recipeId == "lecso" && it.meal == PlanMeal.LUNCH && it.servings == 3 && it.addedBy == "me" })
    }

    @Test
    fun `servings stay within the allowed range`() = runTest {
        val add = AddToPlanUseCase(plans)
        assertFailsWith<IllegalArgumentException> { add("home", "lecso", listOf(monday), PlanMeal.LUNCH, servings = 0, addedBy = "me") }
        assertFailsWith<IllegalArgumentException> { add("home", "lecso", listOf(monday), PlanMeal.LUNCH, servings = 21, addedBy = "me") }
    }

    @Test
    fun `an entry moves within its week in place and to another week by delete and create`() = runTest {
        val entry = PlanEntry("e1", monday, PlanMeal.DINNER, "lecso", 2, addedBy = "sam")
        plans.add("home", entry, "sam")
        val move = MovePlanEntryUseCase(plans)

        move("home", entry, monday.plusDays(3), PlanMeal.LUNCH, movedBy = "me")
        val moved = plans.entriesOf().getValue("e1")
        assertEquals(monday.plusDays(3), moved.date)
        assertEquals(PlanMeal.LUNCH, moved.meal)

        val nextMonday = monday.plusWeeks(1)
        move("home", moved, nextMonday, PlanMeal.DINNER, movedBy = "me")
        assertEquals(emptyList(), plans.observe("home", WeekId.of(monday)).first().entries)
        val next = plans.observe("home", WeekId.of(nextMonday)).first().entries.single()
        assertEquals(nextMonday, next.date)
        assertEquals("sam", next.addedBy)
        assertEquals(2, next.servings)
    }

    @Test
    fun `do nakupu adds what the days from today on still need`() = runTest {
        val catalog = Catalog(
            ingredients = listOf(ingredient("pasta"), ingredient("rice")),
            recipes = listOf(
                recipe("pasta-dish", servings = 1, ingredients = listOf(line("pasta", 100.0))),
                recipe("rice-dish", servings = 1, ingredients = listOf(line("rice", 80.0))),
            ),
            substitutions = emptyList(),
        )
        val shopping = FakeShoppingRepository()
        val entries = listOf(
            PlanEntry("past", monday, PlanMeal.DINNER, "rice-dish", 1),
            PlanEntry("today", monday.plusDays(1), PlanMeal.DINNER, "pasta-dish", 2),
        )
        val toShopping = PlanToShoppingUseCase(shopping)

        val added = toShopping(
            "home",
            catalog,
            entries,
            KitchenState(pantry = emptyList(), staples = emptySet(), equipment = emptySet()),
            list = emptyList(),
            today = monday.plusDays(1),
            addedBy = "me",
        )

        assertEquals(1, added)
        val item = shopping.itemsOf().values.single()
        assertEquals(IngredientRef.Catalog("pasta"), item.ingredient)
        assertEquals(200.0, item.amount)
        assertEquals(setOf("pasta-dish"), item.reasonRecipeIds)
    }
}
