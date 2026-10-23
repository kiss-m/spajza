package sk.matej.spajza.ui.common

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NumberFormatTest {
    @Test
    fun `spoons and pieces round to quarters`() {
        assertEquals("1", NumberFormat.quarters(1.0))
        assertEquals("½", NumberFormat.quarters(0.5))
        assertEquals("¼", NumberFormat.quarters(0.1))
        assertEquals("1½", NumberFormat.quarters(1.49))
        assertEquals("2¾", NumberFormat.quarters(2.8))
        assertEquals("0", NumberFormat.quarters(0.0))
    }

    @Test
    fun `grams are whole and decimals follow the locale`() {
        assertEquals("138", NumberFormat.whole(137.6))
        assertEquals("1", NumberFormat.whole(0.3))
        assertEquals("1,5", NumberFormat.decimal(1.5, Locale.forLanguageTag("sk")))
        assertEquals("1.5", NumberFormat.decimal(1.5, Locale.ENGLISH))
        assertEquals("2", NumberFormat.decimal(1.96, Locale.ENGLISH))
        assertEquals("05:30", NumberFormat.clock(330))
    }

    @Test
    fun `expiry states`() {
        val today = LocalDate.of(2026, 10, 23)
        assertNull(Expiry.of(null, today))
        assertEquals(ExpiryState.EXPIRED, Expiry.of(today.minusDays(1), today)?.state)
        assertEquals(ExpiryState.TODAY, Expiry.of(today, today)?.state)
        assertEquals(Expiry(ExpiryState.SOON, 3), Expiry.of(today.plusDays(3), today))
        assertEquals(ExpiryState.LATER, Expiry.of(today.plusDays(4), today)?.state)
    }
}
