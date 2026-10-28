package sk.matej.spajza.data.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.own.RecipeDraft
import sk.matej.spajza.domain.own.RecipeSource
import sk.matej.spajza.domain.own.StepTimers
import sk.matej.spajza.domain.text.TextNormalizer

/**
 * Reads a schema.org Recipe from the JSON-LD of a web page, the form recipe sites publish for
 * search engines. Nutrition from the page is ignored: the app computes its own.
 */
object RecipePageParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(html: String, url: String): RecipeDraft? {
        val recipe = SCRIPT.findAll(html)
            .mapNotNull { block -> runCatching { json.parseToJsonElement(unwrap(block.groupValues[1])) }.getOrNull() }
            .firstNotNullOfOrNull { findRecipe(it, depth = 0) }
            ?: return null
        val title = recipe.text("name")?.let(::clean)?.takeIf { it.isNotEmpty() } ?: return null
        val minutes = minutes(recipe.text("totalTime"))
            ?: listOfNotNull(minutes(recipe.text("prepTime")), minutes(recipe.text("cookTime"))).takeIf { it.isNotEmpty() }?.sum()
        return RecipeDraft(
            title = title.take(MAX_TITLE),
            description = recipe.text("description")?.let(::clean)?.take(MAX_DESCRIPTION).orEmpty(),
            servings = servings(recipe["recipeYield"]),
            totalTimeMin = minutes?.takeIf { it in 1..MAX_MINUTES },
            ingredientLines = (recipe["recipeIngredient"] ?: recipe["ingredients"]).texts().flatMap(::lines),
            steps = instructions(recipe["recipeInstructions"]),
            mealTypes = mealTypes(recipe["recipeCategory"].texts()),
            source = RecipeSource.URL,
            sourceUrl = url,
        )
    }

    /** The first Recipe object anywhere in the JSON-LD: top level, in a list, in `@graph` or nested. */
    private fun findRecipe(element: JsonElement, depth: Int): JsonObject? {
        if (depth > MAX_DEPTH) return null
        return when (element) {
            is JsonObject -> if (isRecipe(element)) element else element.values.firstNotNullOfOrNull { findRecipe(it, depth + 1) }
            is JsonArray -> element.firstNotNullOfOrNull { findRecipe(it, depth + 1) }
            else -> null
        }
    }

    private fun isRecipe(element: JsonObject): Boolean = element["@type"].texts().any { it.substringAfterLast('/').substringAfterLast(':') == "Recipe" }

    /** Steps from text, a list of texts, HowToStep objects or HowToSection groups of them. */
    private fun instructions(element: JsonElement?): List<String> = when (element) {
        null -> emptyList()
        is JsonPrimitive -> element.contentOrNull?.let(::lines).orEmpty()
        is JsonArray -> element.flatMap(::instructions)
        is JsonObject -> {
            val items = element["itemListElement"]
            if (items != null) {
                instructions(items)
            } else {
                listOfNotNull((element.text("text") ?: element.text("name") ?: element.text("description"))?.let(::clean)?.takeIf { it.isNotEmpty() })
            }
        }
    }

    private fun servings(element: JsonElement?): Int? = element.texts().firstNotNullOfOrNull { text ->
        NUMBER.find(text)?.value?.toIntOrNull()?.takeIf { it in 1..MAX_SERVINGS }
    }

    /** ISO 8601 durations as schema.org writes them ("PT1H30M"); plain text as a fallback. */
    private fun minutes(text: String?): Int? {
        text ?: return null
        val match = ISO_DURATION.matchEntire(text.trim().uppercase()) ?: return StepTimers.minutesIn(text)
        val (days, hours, minutes, seconds) = match.destructured
        val total = days.toDoubleOrNull().orZero() * 24 * 60 + hours.toDoubleOrNull().orZero() * 60 +
            minutes.toDoubleOrNull().orZero() + seconds.toDoubleOrNull().orZero() / 60
        return Math.round(total).toInt().takeIf { it > 0 }
    }

    private fun Double?.orZero() = this ?: 0.0

    private fun mealTypes(categories: List<String>): Set<MealType> {
        val words = categories.joinToString(" ") { TextNormalizer.normalize(it) }
        return MEAL_WORDS.filterValues { stems -> stems.any { words.contains(it) } }.keys.flatMapTo(linkedSetOf()) { it }
    }

    /** Values of [this] as text: a string, a number, a list of them, or objects with a name or text. */
    private fun JsonElement?.texts(): List<String> = when (this) {
        null -> emptyList()
        is JsonPrimitive -> listOfNotNull(intOrNull?.toString() ?: contentOrNull)
        is JsonArray -> flatMap { it.texts() }
        is JsonObject -> listOfNotNull(text("text") ?: text("name") ?: text("@value"))
    }

    private fun JsonObject.text(key: String): String? = this[key].texts().firstOrNull()?.takeIf { it.isNotBlank() }

    /** Text split at line breaks and paragraph ends, each part cleaned; empty parts dropped. */
    private fun lines(text: String): List<String> = text.replace(BREAKS, "\n").split('\n').map(::clean).filter { it.isNotEmpty() }

    /** Tags removed, entities decoded, spaces collapsed. */
    private fun clean(text: String): String = decode(text.replace(TAG, " ")).replace(SPACES, " ").trim()

    private fun decode(text: String): String = ENTITY.replace(text) { match ->
        val name = match.groupValues[1]
        when {
            name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)?.let(::char)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::char)
            else -> NAMED[name]
        } ?: match.value
    }

    private fun char(code: Int): String? = if (Character.isValidCodePoint(code)) String(Character.toChars(code)) else null

    private fun unwrap(text: String): String = text.trim().removePrefix("<!--").removeSuffix("-->").removePrefix("<![CDATA[").removeSuffix("]]>").trim()

    private const val MAX_DEPTH = 8
    private const val MAX_TITLE = 120
    private const val MAX_DESCRIPTION = 500
    private const val MAX_MINUTES = 24 * 60
    private const val MAX_SERVINGS = 50

    private val SCRIPT = Regex(
        """<script[^>]*type\s*=\s*["']?application/ld\+json["']?[^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val ISO_DURATION = Regex("""P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?""")
    private val NUMBER = Regex("""\d+""")
    private val BREAKS = Regex("""(?i)<br\s*/?>|</p>|</li>|</div>|\r""")
    private val TAG = Regex("""<[^>]*>""")
    private val SPACES = Regex("""\s+""")
    private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+\d*);""")
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—",
        "hellip" to "…", "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "deg" to "°", "times" to "×", "rsquo" to "’",
        "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "bdquo" to "„", "laquo" to "«", "raquo" to "»",
        "aacute" to "á", "eacute" to "é", "iacute" to "í", "oacute" to "ó", "uacute" to "ú", "yacute" to "ý", "auml" to "ä",
        "ocirc" to "ô", "scaron" to "š", "ccaron" to "č", "zcaron" to "ž", "tcaron" to "ť", "dcaron" to "ď", "ncaron" to "ň",
        "lcaron" to "ľ", "lacute" to "ĺ", "racute" to "ŕ", "ecaron" to "ě", "rcaron" to "ř", "uring" to "ů",
        "Scaron" to "Š", "Ccaron" to "Č", "Zcaron" to "Ž",
    )
    private val MEAL_WORDS: Map<Set<MealType>, List<String>> = mapOf(
        setOf(MealType.BREAKFAST) to listOf("breakfast", "brunch", "ranajk", "snidan"),
        setOf(MealType.DESSERT) to listOf("dessert", "dezert", "sladk", "kolac", "cake", "torta", "cookie", "kolacik"),
        setOf(MealType.SNACK) to listOf("snack", "appetizer", "predjed", "desiat", "olovrant", "starter"),
        setOf(MealType.LUNCH, MealType.DINNER) to listOf("main", "hlavn", "dinner", "lunch", "obed", "vecer", "soup", "polievk", "polevk"),
    )
}
