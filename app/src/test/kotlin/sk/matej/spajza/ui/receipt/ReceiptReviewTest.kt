package sk.matej.spajza.ui.receipt

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.receipt.ReceiptItem
import sk.matej.spajza.domain.receipt.ReceiptParser
import sk.matej.spajza.ui.common.toOption
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptReviewTest {
    private val byId = MatcherFixture.ingredients.associateBy { it.id }
    private val items = listOf(
        ReceiptItem("CESTOVINY PENNE 500G", byId.getValue("pasta"), confidence = 0.9, quantity = 500.0),
        ReceiptItem("PARADAJKY", byId.getValue("tomato"), confidence = ReceiptParser.MIN_CONFIDENCE - 0.1, quantity = null),
        ReceiptItem("TASKA PAPIEROVA", null, confidence = 0.0, quantity = null),
        ReceiptItem("CESTOVINY FUSILLI 500G", byId.getValue("pasta"), confidence = 0.8, quantity = 499.99999),
    )

    @Test
    fun `confident matches start ticked and amounts are rounded`() {
        val rows = ReceiptRow.of(items)

        assertEquals(listOf(true, false, false, true), rows.map { it.checked })
        assertEquals("500", rows[3].quantityText)
        assertNull(rows[2].option)
        assertFalse(rows[2].canAdd)
    }

    @Test
    fun `rows of one ingredient are added up and unticked rows stay out`() {
        val additions = ReceiptAdditions.of(ReceiptRow.of(items))

        assertEquals(listOf(ReceiptAddition("pasta", 1000.0)), additions)
    }

    @Test
    fun `picking another ingredient ticks the row and drops an amount in another unit`() {
        val rows = ReceiptRow.of(items)
        val tomatoes = rows[2].matchedTo(byId.getValue("tomato").toOption()).copy(quantityText = "6")
        assertTrue(tomatoes.canAdd)

        val rice = rows[0].matchedTo(byId.getValue("rice").toOption())
        assertEquals("500", rice.quantityText, "grams stay grams")
        val asPieces = rows[0].matchedTo(byId.getValue("onion").toOption())
        assertEquals("", asPieces.quantityText)

        val additions = ReceiptAdditions.of(listOf(rows[1].copy(checked = true), tomatoes))
        assertEquals(listOf(ReceiptAddition("tomato", 6.0)), additions, "an unknown amount does not hide a known one")
    }
}
