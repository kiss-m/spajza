package sk.matej.spajza.ui.plan

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.matching.KitchenState
import sk.matej.spajza.domain.matching.Personalization
import sk.matej.spajza.domain.matching.RecipeMatcher
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.plan.PlanMeal
import sk.matej.spajza.domain.plan.WeekId
import sk.matej.spajza.domain.plan.WeekPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** One planned meal as the Plán tab shows it. */
data class PlanEntryModel(
    val entry: PlanEntry,
    /** Null when the recipe no longer exists (a deleted own recipe). */
    val title: LocalizedText?,
    val cuisine: Cuisine?,
    val kcalPerServing: Int?,
    /** Required ingredients missing from the pantry for the planned servings. */
    val missing: Int,
)

data class PlanDayModel(val date: LocalDate, val isToday: Boolean, val isPast: Boolean, val entries: List<PlanEntryModel>)

data class PlanUiState(
    val isLoading: Boolean = true,
    val week: WeekId? = null,
    /** -1 last week, 0 this week, 1 next week, null for any other week. */
    val relativeWeek: Int? = null,
    val days: List<PlanDayModel> = emptyList(),
    /** Whether "Do nákupu" has anything to look at: entries from today on. */
    val canShop: Boolean = false,
) {
    val isEmpty: Boolean get() = days.all { it.entries.isEmpty() }
}

/** Builds the Plán tab from the household's data. Pure, so it is unit-tested. */
class PlanWeekBuilder(private val catalog: Catalog) {
    private val matcher = RecipeMatcher(catalog)

    fun build(plan: WeekPlan, kitchen: KitchenState, today: LocalDate): PlanUiState {
        val thisWeek = WeekId.of(today)
        val days = plan.week.days.map { date ->
            PlanDayModel(
                date = date,
                isToday = date == today,
                isPast = date.isBefore(today),
                entries = plan.on(date).map { model(it, kitchen, today) },
            )
        }
        return PlanUiState(
            isLoading = false,
            week = plan.week,
            relativeWeek = when (plan.week) {
                thisWeek -> 0
                thisWeek.next() -> 1
                thisWeek.previous() -> -1
                else -> null
            },
            days = days,
            canShop = plan.entries.any { !it.date.isBefore(today) && it.recipeId in catalog.recipesById },
        )
    }

    private fun model(entry: PlanEntry, kitchen: KitchenState, today: LocalDate): PlanEntryModel {
        val recipe = catalog.recipesById[entry.recipeId] ?: return PlanEntryModel(entry, null, null, null, 0)
        val match = matcher.evaluate(recipe, kitchen, Personalization(), today, entry.servings)
        return PlanEntryModel(entry, recipe.title, recipe.cuisine, match.perServing.kcal.roundToInt(), match.missing.size)
    }
}

/** What the "Pridať do plánu" sheet is about to add: days (also across weeks), meal and servings. */
data class AddToPlanDraft(
    /** The week whose days the sheet shows. */
    val week: WeekId,
    val dates: Set<LocalDate>,
    val meal: PlanMeal,
    val servings: Int,
) {
    fun toggle(date: LocalDate): AddToPlanDraft = copy(dates = if (date in dates) dates - date else dates + date)

    fun showWeek(week: WeekId): AddToPlanDraft = copy(week = week)

    fun withServings(value: Int): AddToPlanDraft = copy(servings = value.coerceIn(PlanEntry.MIN_SERVINGS, PlanEntry.MAX_SERVINGS))

    companion object {
        /** Starts on [preferredDate], or today; the meal follows what the recipe is for. */
        fun initial(mealTypes: Set<MealType>, servings: Int, today: LocalDate, preferredDate: LocalDate? = null): AddToPlanDraft {
            val date = preferredDate?.takeUnless { it.isBefore(today) } ?: today
            return AddToPlanDraft(
                week = WeekId.of(date),
                dates = setOf(date),
                meal = defaultMeal(mealTypes),
                servings = servings.coerceIn(PlanEntry.MIN_SERVINGS, PlanEntry.MAX_SERVINGS),
            )
        }

        fun defaultMeal(mealTypes: Set<MealType>): PlanMeal {
            val main = MealType.LUNCH in mealTypes || MealType.DINNER in mealTypes
            return when {
                main && MealType.LUNCH !in mealTypes -> PlanMeal.DINNER
                main -> PlanMeal.LUNCH
                MealType.BREAKFAST in mealTypes -> PlanMeal.BREAKFAST
                mealTypes.isNotEmpty() -> PlanMeal.SNACK
                else -> PlanMeal.LUNCH
            }
        }
    }
}

/** Dates for plan headers in the app language. */
object PlanDates {
    /** "Pondelok 2. 11." / "Monday, Nov 2". */
    fun dayTitle(date: LocalDate, locale: Locale): String {
        val pattern = if (locale.language == "en") "EEEE, MMM d" else "EEEE d. M."
        return DateTimeFormatter.ofPattern(pattern, locale).format(date).replaceFirstChar { it.titlecase(locale) }
    }

    /** "Po" / "Mon": the short day name for day chips. */
    fun shortDay(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern("EEE", locale).format(date).removeSuffix(".").replaceFirstChar { it.titlecase(locale) }

    /** "2. – 8. 11." or "28. 12. – 3. 1." / "Nov 2 – 8" or "Dec 28 – Jan 3". */
    fun weekRange(week: WeekId, locale: Locale): String {
        val first = week.monday
        val last = week.days.last()
        val sameMonth = first.month == last.month
        return if (locale.language == "en") {
            val start = DateTimeFormatter.ofPattern("MMM d", locale).format(first)
            val end = DateTimeFormatter.ofPattern(if (sameMonth) "d" else "MMM d", locale).format(last)
            "$start – $end"
        } else {
            val start = if (sameMonth) "${first.dayOfMonth}." else "${first.dayOfMonth}. ${first.monthValue}."
            "$start – ${last.dayOfMonth}. ${last.monthValue}."
        }
    }
}
