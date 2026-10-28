package sk.matej.spajza.domain.pantry

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** The device's daily expiry reminder (stored on the device, not in the account). */
data class ExpiryReminder(
    val enabled: Boolean = false,
    val time: LocalTime = DEFAULT_TIME,
) {
    companion object {
        /** Before dinner gets planned. */
        val DEFAULT_TIME: LocalTime = LocalTime.of(17, 0)
    }
}

/** A pantry item for the reminder; [daysLeft] is negative once the date has passed. */
data class ExpiringItem(val item: PantryItem, val daysLeft: Long)

/** The tunables of the daily reminder. */
object ExpiryReminderConfig {
    /** Items that expire within this many days from today are mentioned (0 = today, 1 = tomorrow). */
    const val DAYS_AHEAD = 1L

    /** Items past their date keep being mentioned this many days, then the reminder stops nagging. */
    const val DAYS_PAST = 2L

    /** How many items the notification names before "and N more". */
    const val MAX_NAMED = 5
}

object ExpiryDigest {
    /** What the reminder mentions today, most urgent first; empty when there is nothing to say. */
    fun of(pantry: List<PantryItem>, today: LocalDate): List<ExpiringItem> =
        pantry.mapNotNull { item ->
            val date = item.expiresAt ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(today, date)
            ExpiringItem(item, days).takeIf { days in -ExpiryReminderConfig.DAYS_PAST..ExpiryReminderConfig.DAYS_AHEAD }
        }.sortedWith(compareBy<ExpiringItem> { it.daysLeft }.thenBy { it.item.id })

    /** The wait until the next [time] after [now], so the daily check runs at the chosen hour. */
    fun delayUntil(time: LocalTime, now: ZonedDateTime): Duration {
        var next = now.with(time).truncatedTo(ChronoUnit.MINUTES)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
