package sk.matej.spajza.data.seed

import sk.matej.spajza.domain.receipt.OcrLine
import sk.matej.spajza.domain.receipt.ReceiptParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** A typical Slovak supermarket receipt (invented) must turn into the right pantry suggestions. */
class ReceiptCatalogTest {
    private val catalog = CatalogBuilder().build(SeedDirectory.read(File(System.getProperty("spajza.seedDir")))).catalog
    private val parser = ReceiptParser(catalog)

    private val receipt = listOf(
        "SUPERMARKET ŠPAJZA s.r.o.",
        "Hlavná 12, 811 01 Bratislava",
        "IČO: 12345678  DIČ: 2020123456",
        "MLIEKO POLOTUČNÉ 1,5% 1L      0,89 A",
        "2 x 0,89",
        "KURACIE PRSIA                 5,49 A",
        "0,532 kg x 10,32",
        "ZEMIAKY PRÍLOHOVÉ 2KG         1,99 A",
        "CESNAK                        0,59 A",
        "BRYNDZA 250G                  2,49 A",
        "VAJCIA M 10KS                 2,29 A",
        "HORALKY                       0,45 A",
        "ZĽAVA                        -0,30 A",
        "SPOLU EUR                    14,19",
        "PLATBA KARTOU                14,19",
        "Ďakujeme za nákup",
    )

    @Test
    fun `a receipt turns into matched pantry items`() {
        val items = parser.parseRows(receipt)
        // Only rows matched with enough confidence are offered checked; the rest wait for the user.
        val found = items.map { (it.ingredient?.id.takeIf { _ -> it.confidence >= ReceiptParser.MIN_CONFIDENCE }) to it.quantity }
        val expected = listOf(
            "milk" to 2000.0,
            "chicken-breast" to 532.0,
            "potato" to 2000.0,
            "garlic" to null,
            "bryndza" to 250.0,
            "egg" to 10.0,
            null to null,
        )
        if (found != expected) fail("got\n" + items.joinToString("\n") { "${it.text} → ${it.ingredient?.id} ${it.quantity} (${"%.2f".format(it.confidence)})" })
    }

    @Test
    fun `names and prices recognized as separate columns are joined into rows`() {
        val lines = listOf(
            OcrLine("MASLO 250G", left = 10, top = 100, right = 200, bottom = 120),
            OcrLine("2,19 A", left = 400, top = 102, right = 470, bottom = 121),
            OcrLine("CIBUĽA", left = 10, top = 130, right = 120, bottom = 150),
            OcrLine("0,49 A", left = 400, top = 131, right = 470, bottom = 151),
        )
        assertEquals(listOf("MASLO 250G 2,19 A", "CIBUĽA 0,49 A"), parser.rows(lines))
        assertEquals(listOf("butter", "onion"), parser.parse(lines).map { it.ingredient?.id })
    }
}
