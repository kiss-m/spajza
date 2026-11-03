package sk.matej.spajza.domain.plan

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeekIdTest {
    @Test
    fun `weeks start on Monday and use the ISO week-based year`() {
        assertEquals(WeekId(2026, 45), WeekId.of(LocalDate.of(2026, 11, 3)))
        assertEquals(WeekId(2026, 45), WeekId.of(LocalDate.of(2026, 11, 8)))
        assertEquals(WeekId(2026, 46), WeekId.of(LocalDate.of(2026, 11, 9)))
        // Around New Year the week belongs to the year that holds its Thursday.
        assertEquals(WeekId(2026, 53), WeekId.of(LocalDate.of(2026, 12, 31)))
        assertEquals(WeekId(2026, 53), WeekId.of(LocalDate.of(2027, 1, 3)))
        assertEquals(WeekId(2027, 1), WeekId.of(LocalDate.of(2027, 1, 4)))
        assertEquals(WeekId(2025, 1), WeekId.of(LocalDate.of(2024, 12, 30)))
    }

    @Test
    fun `a week lists its days from Monday to Sunday`() {
        val days = WeekId(2026, 53).days
        assertEquals(LocalDate.of(2026, 12, 28), days.first())
        assertEquals(LocalDate.of(2027, 1, 3), days.last())
        assertEquals(7, days.size)
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertTrue(days.all { WeekId.of(it) == WeekId(2026, 53) })
    }

    @Test
    fun `next and previous cross year boundaries`() {
        assertEquals(WeekId(2027, 1), WeekId(2026, 53).next())
        assertEquals(WeekId(2026, 53), WeekId(2027, 1).previous())
        assertEquals(WeekId(2026, 46), WeekId(2026, 45).next())
        assertTrue(WeekId(2026, 53) < WeekId(2027, 1))
    }

    @Test
    fun `the document id has a zero-padded week and parses back`() {
        assertEquals("2026-W45", WeekId(2026, 45).toString())
        assertEquals("2027-W01", WeekId(2027, 1).toString())
        assertEquals(WeekId(2027, 1), WeekId.parse("2027-W01"))
        assertEquals(WeekId(2026, 53), WeekId.parse("2026-W53"))
    }

    @Test
    fun `ids of weeks that do not exist are rejected`() {
        // 2027 has 52 ISO weeks.
        assertNull(WeekId.parse("2027-W53"))
        assertNull(WeekId.parse("2026-W00"))
        assertNull(WeekId.parse("2026-W5"))
        assertNull(WeekId.parse("2026-45"))
        assertNull(WeekId.parse(""))
    }
}
