package sk.matej.spajza.domain.search

import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.text.TextNormalizer
import sk.matej.spajza.domain.unit.MeasureUnit
import sk.matej.spajza.domain.unit.MissingConversionException
import sk.matej.spajza.domain.unit.UnitConverter

data class ParsedLine(
    val raw: String,
    val amount: Double?,
    val unit: MeasureUnit?,
    val ingredient: Ingredient?,
    /** The normalized words that matched the ingredient. */
    val matchedText: String?,
    /** 0..1: how sure the parser is about the whole line. */
    val confidence: Double,
)

/**
 * Parses one free-text ingredient line ("200 g kuracích pŕs", "2 dl mlieka", "soľ podľa chuti")
 * into amount, unit and catalog ingredient. Slovak, Czech and English units are understood; the
 * ingredient is the longest run of words that [IngredientSearch] matches well, so descriptions
 * like "nadrobno nakrájaná" are skipped and "olivového oleja" beats plain "oleja".
 */
class IngredientLineParser(private val search: IngredientSearch) {

    fun parse(line: String): ParsedLine {
        val raw = line.trim()
        val (amount, afterAmount) = parseAmount(raw.lowercase())
        var words = afterAmount.split(WHITESPACE).map(TextNormalizer::normalize).filter { it.isNotEmpty() }.flatMap { it.split(' ') }

        var unit: MeasureUnit? = null
        var factor = 1.0
        toTaste(words)?.let { remaining ->
            unit = MeasureUnit.TO_TASTE
            words = remaining
        }
        if (unit == null) {
            UNITS_2.entries.firstOrNull { words.take(2) == it.key }?.let { (_, u) ->
                unit = u.unit
                factor = u.factor
                words = words.drop(2)
            } ?: UNITS_1[words.firstOrNull()]?.let { u ->
                unit = u.unit
                factor = u.factor
                words = words.drop(1)
            }
        }
        if (words.firstOrNull() == "of") words = words.drop(1)

        val match = matchIngredient(words)
        val ingredient = match?.hit?.ingredient
        val explicitUnit = unit != null
        if (amount != null && unit == null && ingredient?.gramsPerPiece != null) unit = MeasureUnit.PCS
        // A unit without a number ("štipka soli", "lyžica cukru") means one of it.
        val finalAmount = amount?.let { it * factor } ?: if (unit != null && unit != MeasureUnit.TO_TASTE) 1.0 else null

        val confidence = if (ingredient == null) {
            if (amount != null) NO_INGREDIENT_CONFIDENCE else NO_INGREDIENT_CONFIDENCE / 2
        } else {
            var c = match.hit.score
            if (finalAmount == null && unit != MeasureUnit.TO_TASTE) c *= NO_AMOUNT_FACTOR
            if (!explicitUnit && amount != null) c *= if (unit == MeasureUnit.PCS) IMPLIED_PIECES_FACTOR else NO_UNIT_FACTOR
            unit?.let { u -> if (finalAmount != null && !converts(finalAmount, u, ingredient)) c *= NO_CONVERSION_FACTOR }
            c * (WORD_COVERAGE_BASE + (1 - WORD_COVERAGE_BASE) * match.words.toDouble() / words.size)
        }
        return ParsedLine(raw, finalAmount, unit, ingredient, match?.text, confidence.coerceIn(0.0, 1.0))
    }

    private class WindowMatch(val hit: SearchHit, val text: String, val words: Int)

    /** Longest window of words with a good match; among equally long ones the best score. */
    private fun matchIngredient(words: List<String>): WindowMatch? {
        if (words.isEmpty()) return null
        var fallback: WindowMatch? = null
        for (size in minOf(words.size, MAX_WINDOW) downTo 1) {
            val best = words.windowed(size)
                .mapNotNull { window -> window.joinToString(" ").let { text -> search.bestMatch(text)?.let { WindowMatch(it, text, size) } } }
                .maxByOrNull { it.hit.score }
                ?: continue
            if (best.hit.score >= GOOD_MATCH) return best
            if (fallback == null || best.hit.score > fallback.hit.score) fallback = best
        }
        return fallback?.takeIf { it.hit.score >= WEAK_MATCH }
    }

    private fun converts(amount: Double, unit: MeasureUnit, ingredient: Ingredient): Boolean = try {
        UnitConverter.toGrams(amount, unit, ingredient)
        true
    } catch (e: MissingConversionException) {
        false
    }

    /** Words without a "to taste" phrase, or null when there is none. */
    private fun toTaste(words: List<String>): List<String>? {
        for (phrase in TO_TASTE) {
            val n = phrase.size
            for (i in 0..words.size - n) {
                if (words.subList(i, i + n) == phrase) return words.subList(0, i) + words.subList(i + n, words.size)
            }
        }
        return null
    }

    private fun parseAmount(text: String): Pair<Double?, String> {
        MIXED.find(text)?.let { m ->
            val (whole, numerator, denominator) = m.destructured
            return (whole.toDouble() + numerator.toDouble() / denominator.toDouble()) to text.substring(m.range.last + 1)
        }
        FRACTION.find(text)?.let { m ->
            val (numerator, denominator) = m.destructured
            return (numerator.toDouble() / denominator.toDouble()) to text.substring(m.range.last + 1)
        }
        RANGE.find(text)?.let { m ->
            val (from, to) = m.destructured
            return (number(from) + number(to)) / 2 to text.substring(m.range.last + 1)
        }
        NUMBER.find(text)?.let { m ->
            val (value, glyph) = m.destructured
            return (number(value) + (UNICODE_FRACTIONS[glyph.firstOrNull()] ?: 0.0)) to text.substring(m.range.last + 1)
        }
        UNICODE_FRACTIONS[text.firstOrNull()]?.let { return it to text.substring(1) }
        val first = text.substringBefore(' ')
        WORD_NUMBERS[TextNormalizer.normalize(first)]?.let { return it to text.substringAfter(' ', "") }
        return null to text
    }

    private fun number(text: String) = text.replace(',', '.').toDouble()

    private data class UnitFactor(val unit: MeasureUnit, val factor: Double = 1.0)

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val MIXED = Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)""")
        val FRACTION = Regex("""^(\d+)\s*/\s*(\d+)""")
        val RANGE = Regex("""^(\d+(?:[.,]\d+)?)\s*[-–]\s*(\d+(?:[.,]\d+)?)""")
        val NUMBER = Regex("""^(\d+(?:[.,]\d+)?)([½¼¾⅓⅔]?)""")
        val UNICODE_FRACTIONS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

        val WORD_NUMBERS = mapOf(
            "pol" to 0.5, "pul" to 0.5, "half" to 0.5,
            "stvrt" to 0.25, "ctvrt" to 0.25, "quarter" to 0.25,
            "jeden" to 1.0, "jedna" to 1.0, "jedno" to 1.0, "one" to 1.0, "a" to 1.0, "an" to 1.0,
            "dva" to 2.0, "dve" to 2.0, "two" to 2.0,
            "tri" to 3.0, "three" to 3.0,
            "styri" to 4.0, "ctyri" to 4.0, "four" to 4.0,
            "pat" to 5.0, "pet" to 5.0, "five" to 5.0,
        )

        val TO_TASTE = listOf(listOf("podla", "chuti"), listOf("dle", "chuti"), listOf("to", "taste"))

        val UNITS_2 = mapOf(
            listOf("polievkova", "lyzica") to UnitFactor(MeasureUnit.TBSP),
            listOf("polievkove", "lyzice") to UnitFactor(MeasureUnit.TBSP),
            listOf("kavova", "lyzicka") to UnitFactor(MeasureUnit.TSP),
            listOf("kavove", "lyzicky") to UnitFactor(MeasureUnit.TSP),
        )

        val UNITS_1: Map<String, UnitFactor> = buildMap {
            fun put(unit: MeasureUnit, factor: Double, vararg words: String) = words.forEach { put(it, UnitFactor(unit, factor)) }
            put(MeasureUnit.G, 1.0, "g", "gr", "gram", "gramy", "gramov", "gramu", "grams")
            put(MeasureUnit.KG, 1.0, "kg", "kilo", "kila", "kilogram", "kilogramy", "kilogramov", "kilograms")
            put(MeasureUnit.ML, 1.0, "ml", "mililiter", "mililitrov", "mililitru", "milliliter", "milliliters")
            put(MeasureUnit.ML, 100.0, "dl", "deci", "deciliter", "decilitra", "decilitrov", "decilitr")
            put(MeasureUnit.L, 1.0, "l", "liter", "litra", "litre", "litrov", "litry", "litru", "litr", "liters", "litres")
            put(
                MeasureUnit.PCS, 1.0,
                "ks", "kus", "kusy", "kusov", "kusu", "pcs", "pc", "piece", "pieces",
                "strucik", "struciky", "strucikov", "strouzek", "strouzky", "strouzku", "clove", "cloves",
                "platok", "platky", "platkov", "platek", "plateky", "slice", "slices",
                "konzerva", "konzervy", "konzerv", "plechovka", "plechovky", "can", "cans", "tin", "tins",
                "balenie", "balenia", "baleni", "pack", "packs", "package",
                "zvazok", "zvazky", "zvazku", "svazek", "svazky", "bunch",
                "hlavka", "hlavky", "head", "kocka", "kocky", "kostka", "kostky", "cube", "cubes",
                "hrst", "hrste", "handful",
            )
            put(
                MeasureUnit.TBSP, 1.0,
                "lyzica", "lyzice", "lyzic", "lyzicu", "lyzicou", "pl", "lzice", "lzic", "lzici",
                "tbsp", "tbs", "tablespoon", "tablespoons",
            )
            put(
                MeasureUnit.TSP, 1.0,
                // "ČL" (čajová lyžička) is far more common in Slovak recipes than centiliters.
                "lyzicka", "lyzicky", "lyziciek", "lyzicku", "cl", "lzicka", "lzicky", "lzicek", "lzicku",
                "tsp", "teaspoon", "teaspoons",
            )
            put(MeasureUnit.CUP, 1.0, "hrncek", "hrnceky", "hrnceka", "hrnek", "hrnky", "salka", "salky", "salku", "cup", "cups")
            put(MeasureUnit.PINCH, 1.0, "stipka", "stipky", "stipku", "stipiek", "spetka", "spetky", "spetku", "pinch", "pinches")
        }

        /** A window has to match at least this well before a shorter one is tried. */
        const val GOOD_MATCH = 0.6
        const val WEAK_MATCH = 0.5
        const val MAX_WINDOW = 4
        const val NO_INGREDIENT_CONFIDENCE = 0.1
        const val NO_AMOUNT_FACTOR = 0.85
        const val IMPLIED_PIECES_FACTOR = 0.95
        const val NO_UNIT_FACTOR = 0.7
        const val NO_CONVERSION_FACTOR = 0.8
        const val WORD_COVERAGE_BASE = 0.85
    }
}
