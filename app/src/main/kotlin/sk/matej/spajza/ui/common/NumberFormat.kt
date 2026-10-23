package sk.matej.spajza.ui.common

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Kitchen-friendly numbers; pure so they can be unit-tested. */
object NumberFormat {
    private val FRACTIONS = arrayOf("", "¼", "½", "¾")

    /** Spoons and pieces: the nearest quarter, never less than ¼ ("1½", "¾"). */
    fun quarters(value: Double): String {
        if (value <= 0) return "0"
        val quarters = max(1, (value * 4).roundToInt())
        val whole = quarters / 4
        val fraction = FRACTIONS[quarters % 4]
        return if (whole == 0) fraction else "$whole$fraction"
    }

    /** Grams and millilitres: whole numbers, never less than 1 for a positive amount. */
    fun whole(value: Double): String = if (value <= 0) "0" else max(1L, value.roundToLong()).toString()

    /** One decimal in the locale's format, without a trailing zero ("1,5" in Slovak, "2"). */
    fun decimal(value: Double, locale: Locale): String {
        val tenths = (value * 10).roundToLong()
        return if (tenths % 10 == 0L) (tenths / 10).toString() else String.format(locale, "%.1f", tenths / 10.0)
    }

    /** "05:30" for a countdown. */
    fun clock(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
}

enum class ExpiryState { EXPIRED, TODAY, SOON, LATER }

/** How urgent a use-by date is; SOON means within [soonDays] days. */
data class Expiry(val state: ExpiryState, val daysLeft: Long) {
    companion object {
        fun of(expiresAt: LocalDate?, today: LocalDate, soonDays: Long = 3): Expiry? {
            expiresAt ?: return null
            val days = ChronoUnit.DAYS.between(today, expiresAt)
            val state = when {
                days < 0 -> ExpiryState.EXPIRED
                days == 0L -> ExpiryState.TODAY
                days <= soonDays -> ExpiryState.SOON
                else -> ExpiryState.LATER
            }
            return Expiry(state, days)
        }
    }
}

/** Parses what a number field collects; both decimal separators work. */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
