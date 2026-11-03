package sk.matej.spajza.domain.plan

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Locale
import java.util.UUID

/** The meals of a day a recipe can be planned for, in the order a day shows them. */
enum class PlanMeal { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * An ISO week, Monday first, in its week-based year: the id of a meal plan document (`2026-W45`,
 * docs/DECISIONS.md D13). Around New Year a week belongs to the year that holds its Thursday.
 */
data class WeekId(val year: Int, val week: Int) : Comparable<WeekId> {
    init {
        require(week in 1..MAX_WEEK) { "week must be 1..$MAX_WEEK, was $week" }
    }

    val monday: LocalDate
        get() = LocalDate.of(year, 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
            .with(DayOfWeek.MONDAY)

    /** Monday to Sunday. */
    val days: List<LocalDate> get() = monday.let { first -> (0L until DAYS).map { first.plusDays(it) } }

    operator fun contains(date: LocalDate): Boolean = of(date) == this

    fun next(): WeekId = of(monday.plusWeeks(1))

    fun previous(): WeekId = of(monday.minusWeeks(1))

    override fun compareTo(other: WeekId): Int = compareValuesBy(this, other, WeekId::year, WeekId::week)

    override fun toString(): String = String.format(Locale.ROOT, "%04d-W%02d", year, week)

    companion object {
        private const val MAX_WEEK = 53
        private const val DAYS = 7L
        private val PATTERN = Regex("""(\d{4})-W(\d{2})""")

        fun of(date: LocalDate): WeekId =
            WeekId(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

        /** The week behind a document id, or null for an id that names no real week. */
        fun parse(text: String): WeekId? {
            val match = PATTERN.matchEntire(text) ?: return null
            val (year, week) = match.destructured
            val candidate = week.toInt().takeIf { it in 1..MAX_WEEK }?.let { WeekId(year.toInt(), it) } ?: return null
            // A year has 52 or 53 weeks; week 53 of a 52-week year rolls into the next year.
            return candidate.takeIf { of(it.monday) == it }
        }
    }
}

/** One recipe planned for a meal of a day (D13). */
data class PlanEntry(
    val id: String,
    val date: LocalDate,
    val meal: PlanMeal,
    /** A catalog recipe id or `own:<id>`. */
    val recipeId: String,
    val servings: Int,
    val addedBy: String? = null,
    val addedAt: Instant? = null,
) {
    val week: WeekId get() = WeekId.of(date)

    companion object {
        const val MIN_SERVINGS = 1
        const val MAX_SERVINGS = 20
        private const val ID_LENGTH = 16

        /** 16 random hex characters. */
        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(ID_LENGTH)
    }
}

/** A week of the household's plan; [entries] may come in any order. */
data class WeekPlan(val week: WeekId, val entries: List<PlanEntry>) {
    /** A day's entries by meal, then in the order they were added. */
    fun on(date: LocalDate): List<PlanEntry> = entries
        .filter { it.date == date }
        .sortedWith(compareBy<PlanEntry> { it.meal }.thenBy(nullsLast()) { it.addedAt }.thenBy { it.id })
}
