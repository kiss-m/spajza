package sk.matej.spajza.domain.receipt

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.search.IngredientLineParser
import sk.matej.spajza.domain.search.IngredientSearch
import sk.matej.spajza.domain.text.TextNormalizer
import sk.matej.spajza.domain.unit.MissingConversionException
import sk.matej.spajza.domain.unit.UnitConverter
import kotlin.math.abs

/** One line of recognized text with its box on the photo, in pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/** A product row of a receipt and what it most likely is. */
data class ReceiptItem(
    /** The row as printed, without price and count but with any package size ("MLIEKO 1L"). */
    val text: String,
    val ingredient: Ingredient?,
    /** 0..1; rows below [ReceiptParser.MIN_CONFIDENCE] are offered unchecked. */
    val confidence: Double,
    /** In the ingredient's pantry unit, when the receipt says how much. */
    val quantity: Double?,
)

/**
 * Reads a shop receipt from recognized text lines: rebuilds rows (names and prices are often
 * recognized as separate columns), drops headers, totals and payment lines, reads counts and
 * weights, and matches the product names against the catalog. Everything it finds is a suggestion
 * the user confirms.
 */
class ReceiptParser(catalog: Catalog) {
    private val parser = IngredientLineParser(IngredientSearch(catalog.ingredients))

    fun parse(lines: List<OcrLine>): List<ReceiptItem> = parseRows(rows(lines))

    /** Lines that sit at the same height become one row, left to right. */
    fun rows(lines: List<OcrLine>): List<String> {
        val sorted = lines.filter { it.text.isNotBlank() }.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val row = rows.lastOrNull()
            val sameRow = row != null && abs(row.first().centerY - line.centerY) <= maxOf(row.first().height, line.height) / 2
            if (sameRow) row.add(line) else rows.add(mutableListOf(line))
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    fun parseRows(rows: List<String>): List<ReceiptItem> {
        val items = mutableListOf<ReceiptItem>()
        // Whether an item's amount came from a count or weight on its own row.
        val counted = mutableListOf<Boolean>()
        for (raw in rows) {
            val row = raw.trim()
            val amount = AMOUNT.find(row.lowercase().replace(',', '.'))
            // Package sizes ("1L", "500 g") stay for the quantity but do not help the matching.
            val withSizes = row.replace(PRICE_AT_END, "").replace(AMOUNT, " ").replace(SPACES, " ").trim()
            val name = withSizes.replace(PACKAGE_SIZE_ANY_CASE, " ").replace(LONE_NUMBERS, " ").replace(SPACES, " ").trim()
            val words = TextNormalizer.normalize(name)
            when {
                // "2 x 0,89" or "0,532 kg x 2,49" under a product applies to that product.
                words.none { it.isLetter() } || isOnlyAmount(words) -> {
                    val last = items.lastOrNull()
                    if (amount != null && last != null && !counted.last()) {
                        items[items.lastIndex] = last.copy(quantity = quantityOf(amount, last.ingredient, sizeText = last.text))
                        counted[counted.lastIndex] = true
                    }
                }
                isNotProduct(words) || POSTAL_CODE.containsMatchIn(row) -> Unit
                else -> {
                    val parsed = parser.parse(name)
                    val ingredient = parsed.ingredient
                    items += ReceiptItem(
                        text = withSizes,
                        ingredient = ingredient,
                        confidence = if (ingredient == null) 0.0 else parsed.confidence,
                        quantity = quantityOf(amount, ingredient, withSizes),
                    )
                    counted += amount != null
                }
            }
        }
        return items
    }

    private fun isOnlyAmount(words: String): Boolean = words.split(' ').all { it.isEmpty() || it.all(Char::isDigit) || it in AMOUNT_WORDS }

    private fun isNotProduct(words: String): Boolean {
        val tokens = words.split(' ')
        return tokens.any { it in STOP_WORDS } || STOP_PHRASES.any { words.contains(it) } || tokens.count { it.any(Char::isLetter) } == 0
    }

    /**
     * The amount in the ingredient's pantry unit: a weight ("0,532 kg"), a count ("2 x") times a
     * package size in the name ("MLIEKO 1L"), or a count of pieces.
     */
    private fun quantityOf(amount: MatchResult?, ingredient: Ingredient?, sizeText: String?): Double? {
        ingredient ?: return null
        val number = amount?.groupValues?.get(1)?.toDoubleOrNull()
        val isWeight = amount?.groupValues?.get(2) == "kg"
        if (number != null && isWeight) return convertGrams(number * 1000, ingredient)
        val count = number?.takeIf { !isWeight }
        val size = sizeText?.let { PACKAGE_SIZE.find(it.lowercase().replace(',', '.')) }
        val packages = count ?: 1.0
        if (size != null) {
            val value = size.groupValues[1].toDouble()
            val grams = when (size.groupValues[2]) {
                "kg" -> value * 1000
                "g" -> value
                "l" -> return volumeIn(value * 1000 * packages, ingredient)
                "ml" -> return volumeIn(value * packages, ingredient)
                "ks" -> return piecesIn(value * packages, ingredient)
                else -> null
            }
            if (grams != null) return convertGrams(grams * packages, ingredient)
        }
        return if (ingredient.pantryUnit == PantryUnit.PCS && count != null) count else null
    }

    private fun piecesIn(pieces: Double, ingredient: Ingredient): Double? = when (ingredient.pantryUnit) {
        PantryUnit.PCS -> pieces
        else -> ingredient.gramsPerPiece?.let { convertGrams(pieces * it, ingredient) }
    }

    private fun volumeIn(millilitres: Double, ingredient: Ingredient): Double? = when (ingredient.pantryUnit) {
        PantryUnit.ML -> millilitres
        else -> ingredient.densityGPerMl?.let { convertGrams(millilitres * it, ingredient) }
    }

    private fun convertGrams(grams: Double, ingredient: Ingredient): Double? = try {
        UnitConverter.gramsToPantryUnit(grams, ingredient)
    } catch (e: MissingConversionException) {
        null
    }

    companion object {
        /** Rows matched at least this well are pre-selected. */
        const val MIN_CONFIDENCE = 0.55

        /** "1,29 A", "12.90 B", "-0,30" at the end of a row. */
        private val PRICE_AT_END = Regex("""\s-?\d+[.,]\d{2}\s*[A-Da-d]?\s*$""")

        /** "2 x 0,89", "2 ks x 0,89", "0.532 kg x 2.49", "3x". Group 1 is the number, group 2 "kg" for weights. */
        private val AMOUNT = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|ks)?\s*[x×*]\s*(?:\d+[.,]\d{2})?""", RegexOption.IGNORE_CASE)

        private val LONE_NUMBERS = Regex("""(?<![\p{L}\d.,])\d{3,}(?![\p{L}\d])""")

        /** A package size printed in the name: "1L", "0,5 l", "500G", "250 g". */
        private val PACKAGE_SIZE = Regex("""(\d+(?:\.\d+)?)\s*(kg|g|ml|l|ks)\b""")
        private val PACKAGE_SIZE_ANY_CASE = Regex("""\d+(?:[.,]\d+)?\s*(kg|g|ml|l|ks)\b""", RegexOption.IGNORE_CASE)

        /** Slovak and Czech postal codes ("811 01") mark the shop's address. */
        private val POSTAL_CODE = Regex("""\b\d{3} \d{2}\b""")
        private val SPACES = Regex("""\s+""")

        private val AMOUNT_WORDS = setOf("x", "ks", "kg", "g", "eur")

        /** Words that never appear in a product row (normalized, without diacritics). */
        private val STOP_WORDS = setOf(
            "spolu", "suma", "celkom", "medzisucet", "dph", "zaklad", "hotovost", "hotovosti", "platba", "platene",
            "karta", "kartou", "vydavok", "vratene", "zlava", "zlavy", "bonus", "ico", "dic", "pokladnica", "pokladna",
            "kasa", "blok", "doklad", "datum", "cas", "okp", "pkp", "uid", "www", "tel", "dakujeme", "nakup", "eur",
            "celkem", "hotove", "sleva", "uctenka", "pokladni", "total", "change", "cash",
        )
        private val STOP_PHRASES = listOf("ic dph", "dic ", "ico ", "s r o", "a s ")
    }
}
