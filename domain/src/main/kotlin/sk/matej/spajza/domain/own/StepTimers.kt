package sk.matej.spajza.domain.own

import java.text.Normalizer

/** Finds the waiting time a recipe step mentions, for the cooking-mode timer. */
object StepTimers {
    /** Seconds of the longest wait in [text] ("varte 20 minút", "bake for 1 hour"); a range counts to its end. */
    fun detect(text: String): Int? {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")
        val durations = DURATION.findAll(plain).map { match ->
            val (from, to, unit) = match.destructured
            number(to.ifEmpty { from }) * secondsPer(unit)
        } + HALF_HOURS.findAll(plain).map { match ->
            (match.groupValues[1].toIntOrNull() ?: 0) * HOUR + HOUR / 2.0
        }
        return durations.maxOrNull()?.toInt()?.takeIf { it in 1..MAX_SECONDS }
    }

    /** "45 min", "1,5 hodiny", "1 hour"; the duration in minutes, for recipe times. */
    fun minutesIn(text: String): Int? = detect(text)?.let { (it + MINUTE / 2) / MINUTE }?.takeIf { it > 0 }

    private fun number(text: String) = text.replace(',', '.').toDouble()

    private fun secondsPer(unit: String): Double = when {
        unit.startsWith("h") -> HOUR.toDouble()
        unit.startsWith("m") -> MINUTE.toDouble()
        else -> 1.0
    }

    private const val MINUTE = 60
    private const val HOUR = 3600
    private const val MAX_SECONDS = 24 * HOUR

    private val MARKS = Regex("""\p{M}+""")
    private const val NUMBER = """(\d+(?:[.,]\d+)?)"""
    private val DURATION = Regex(
        """$NUMBER(?:\s*(?:-|–|az|to)\s*$NUMBER)?\s*(sekund[a-z]*|sek|sec|seconds?|minut[a-z]*|mins?|hodin[a-z]*|hod|hours?|hrs?|h)(?![a-z])""",
    )

    /** "pol hodiny", "1 a pol hodiny", "půl hodiny", "half an hour". */
    private val HALF_HOURS = Regex("""(?:(\d+)\s+a\s+)?(?:pol|pul)\s+hodin|half an hour""")
}
