package sk.matej.spajza.domain.pantry

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpiryDigestTest {
    private val today = LocalDate.of(2026, 10, 28)

    private fun item(id: String, expires: LocalDate?) = PantryItem(IngredientRef.Catalog(id), expiresAt = expires)

    @Test
    fun `mentions recent past dates, today and tomorrow, most urgent first`() {
        val pantry = listOf(
            item("milk", today),
            item("yoghurt", today.minusDays(1)),
            item("chicken", today.plusDays(1)),
            item("cheese", today.plusDays(2)),
            item("ham", today.minusDays(5)),
            item("rice", null),
        )

        val digest = ExpiryDigest.of(pantry, today)

        assertEquals(listOf("yoghurt", "milk", "chicken"), digest.map { it.item.catalogId })
        assertEquals(listOf(-1L, 0L, 1L), digest.map { it.daysLeft })
    }

    @Test
    fun `nothing to say without dates`() {
        assertTrue(ExpiryDigest.of(listOf(item("rice", null)), today).isEmpty())
    }

    @Test
    fun `the check waits until the next reminder time`() {
        val zone = ZoneId.of("Europe/Bratislava")
        val morning = ZonedDateTime.of(today.atTime(9, 30), zone)
        assertEquals(Duration.ofHours(7).plusMinutes(30), ExpiryDigest.delayUntil(LocalTime.of(17, 0), morning))

        val evening = ZonedDateTime.of(today.atTime(18, 0), zone)
        assertEquals(Duration.ofHours(23), ExpiryDigest.delayUntil(LocalTime.of(17, 0), evening))

        val exactly = ZonedDateTime.of(today.atTime(17, 0), zone)
        assertEquals(Duration.ofDays(1), ExpiryDigest.delayUntil(LocalTime.of(17, 0), exactly))
    }

    @Test
    fun `daylight saving time keeps the wall-clock hour`() {
        // Clocks go back on 25 October 2026 in Central Europe, so that day has 25 hours.
        val zone = ZoneId.of("Europe/Bratislava")
        val before = ZonedDateTime.of(LocalDate.of(2026, 10, 24).atTime(18, 0), zone)
        assertEquals(Duration.ofHours(24), ExpiryDigest.delayUntil(LocalTime.of(17, 0), before))
    }
}
