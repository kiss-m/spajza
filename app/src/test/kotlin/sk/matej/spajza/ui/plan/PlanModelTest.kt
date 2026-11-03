package sk.matej.spajza.ui.plan

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.matching.KitchenState
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.plan.PlanMeal
import sk.matej.spajza.domain.plan.WeekId
import sk.matej.spajza.domain.plan.WeekPlan
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.line
import sk.matej.spajza.domain.testing.nutrition
import sk.matej.spajza.domain.testing.recipe
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanModelTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val wednesday = monday.plusDays(2)
    private val catalog = Catalog(
        ingredients = listOf(ingredient("pasta", nutrition(kcal = 350.0)), ingredient("tomato", nutrition(kcal = 20.0))),
        recipes = listOf(recipe("pasta-dish", servings = 2, ingredients = listOf(line("pasta", 200.0), line("tomato", 100.0)))),
        substitutions = emptyList(),
    )
    private val kitchen = KitchenState(listOf(PantryItem(IngredientRef.Catalog("pasta"), quantity = 1000.0)), emptySet(), emptySet())

    @Test
    fun `days list their meals in order and mark today and the past`() {
        val plan = WeekPlan(
            WeekId.of(monday),
            listOf(
                PlanEntry("b", wednesday, PlanMeal.DINNER, "pasta-dish", 2),
                PlanEntry("a", wednesday, PlanMeal.LUNCH, "pasta-dish", 4, addedAt = Instant.parse("2026-11-01T10:00:00Z")),
                PlanEntry("gone", monday, PlanMeal.LUNCH, "own:deleted", 2),
            ),
        )

        val state = PlanWeekBuilder(catalog).build(plan, kitchen, today = monday.plusDays(1))

        assertEquals(0, state.relativeWeek)
        assertEquals(7, state.days.size)
        val tuesday = state.days[1]
        assertTrue(tuesday.isToday)
        assertTrue(state.days[0].isPast)
        assertFalse(state.days[2].isPast)
        assertEquals(listOf("a", "b"), state.days[2].entries.map { it.entry.id })
        val lunch = state.days[2].entries.first()
        // (200 g × 350 + 100 g × 20) / 2 servings.
        assertEquals(360, lunch.kcalPerServing)
        assertEquals(1, lunch.missing, "tomato is not in the pantry")
        assertNull(state.days[0].entries.single().title, "a deleted recipe shows as unavailable")
        assertTrue(state.canShop)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `do nakupu needs entries from today on and weeks know where they are`() {
        val past = WeekPlan(WeekId.of(monday), listOf(PlanEntry("a", monday, PlanMeal.LUNCH, "pasta-dish", 2)))
        val builder = PlanWeekBuilder(catalog)
        assertFalse(builder.build(past, kitchen, today = wednesday).canShop)
        assertEquals(1, builder.build(WeekPlan(WeekId.of(monday).next(), emptyList()), kitchen, monday).relativeWeek)
        assertEquals(-1, builder.build(WeekPlan(WeekId.of(monday).previous(), emptyList()), kitchen, monday).relativeWeek)
        assertNull(builder.build(WeekPlan(WeekId(2027, 10), emptyList()), kitchen, monday).relativeWeek)
        assertTrue(builder.build(WeekPlan(WeekId.of(monday), emptyList()), kitchen, monday).isEmpty)
    }

    @Test
    fun `the add sheet starts today with the meal the recipe is for`() {
        val draft = AddToPlanDraft.initial(setOf(MealType.LUNCH, MealType.DINNER), servings = 2, today = wednesday, preferredDate = monday)
        assertEquals(setOf(wednesday), draft.dates, "a past day is not offered")
        assertEquals(PlanMeal.LUNCH, draft.meal)
        assertEquals(WeekId.of(wednesday), draft.week)

        assertEquals(PlanMeal.DINNER, AddToPlanDraft.defaultMeal(setOf(MealType.DINNER)))
        assertEquals(PlanMeal.BREAKFAST, AddToPlanDraft.defaultMeal(setOf(MealType.BREAKFAST, MealType.SNACK)))
        assertEquals(PlanMeal.SNACK, AddToPlanDraft.defaultMeal(setOf(MealType.DESSERT)))

        val friday = wednesday.plusDays(2)
        val toggled = draft.toggle(friday).toggle(wednesday)
        assertEquals(setOf(friday), toggled.dates)
        assertEquals(PlanEntry.MAX_SERVINGS, draft.withServings(50).servings)
        assertEquals(PlanEntry.MIN_SERVINGS, draft.withServings(0).servings)
    }

    @Test
    fun `headers read naturally in both languages`() {
        val sk = Locale.forLanguageTag("sk")
        val en = Locale.ENGLISH
        assertEquals("Pondelok 2. 11.", PlanDates.dayTitle(monday, sk))
        assertEquals("Monday, Nov 2", PlanDates.dayTitle(monday, en))
        assertEquals("Po", PlanDates.shortDay(monday, sk))
        assertEquals("Mon", PlanDates.shortDay(monday, en))
        assertEquals("2. – 8. 11.", PlanDates.weekRange(WeekId(2026, 45), sk))
        assertEquals("28. 12. – 3. 1.", PlanDates.weekRange(WeekId(2026, 53), sk))
        assertEquals("Nov 2 – 8", PlanDates.weekRange(WeekId(2026, 45), en))
        assertEquals("Dec 28 – Jan 3", PlanDates.weekRange(WeekId(2026, 53), en))
    }
}
