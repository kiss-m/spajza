package sk.matej.spajza.domain.own

import sk.matej.spajza.domain.receipt.OcrLine
import sk.matej.spajza.domain.text.TextNormalizer

/** What a recognized line of a recipe photo is. */
enum class PhotoLineKind { TITLE, INGREDIENT, STEP, IGNORE }

/** A recognized line of a recipe photo, as the app guessed it or the user marked it. */
data class PhotoLine(val text: String, val kind: PhotoLineKind, val block: Int = 0)

/**
 * Turns the text of a photographed recipe (a cookbook page, a handwritten card) into a draft: the
 * app guesses what each line is, the user corrects it, and [draft] joins wrapped lines into
 * ingredients and steps.
 */
object RecipePhotoImport {

    /** Lines in reading order with a first guess of what each one is. */
    fun guess(lines: List<OcrLine>, matcher: RecipeLineMatcher): List<PhotoLine> {
        val texts = lines.filter { it.text.isNotBlank() }
        // The title is usually printed largest near the top.
        val titleIndex = texts.take(TITLE_WINDOW).withIndex()
            .filter { (_, line) -> isTitleCandidate(line.text.trim()) }
            .maxByOrNull { it.value.height }
            ?.index
        var section: PhotoLineKind? = null
        var previous: PhotoLine? = null
        return texts.mapIndexed { index, line ->
            val text = line.text.trim()
            val normalized = TextNormalizer.normalize(text)
            val kind = when {
                isHeading(text, normalized, INGREDIENT_HEADINGS) -> {
                    section = PhotoLineKind.INGREDIENT
                    PhotoLineKind.IGNORE
                }
                isHeading(text, normalized, STEP_HEADINGS) -> {
                    section = PhotoLineKind.STEP
                    PhotoLineKind.IGNORE
                }
                LONE_NUMBER.matches(text) || isMeta(text) -> PhotoLineKind.IGNORE
                index == titleIndex && section == null -> PhotoLineKind.TITLE
                RecipeLineMatcher.isHeading(text) -> PhotoLineKind.IGNORE
                section == PhotoLineKind.STEP -> PhotoLineKind.STEP
                STEP_NUMBER.containsMatchIn(text) -> {
                    section = PhotoLineKind.STEP
                    PhotoLineKind.STEP
                }
                previous?.let { it.kind == PhotoLineKind.STEP && continues(it.text, text) } == true -> PhotoLineKind.STEP
                section == PhotoLineKind.INGREDIENT -> if (text.length > MAX_INGREDIENT_CHARS) PhotoLineKind.STEP else PhotoLineKind.INGREDIENT
                looksLikeIngredient(text, normalized, matcher) -> PhotoLineKind.INGREDIENT
                else -> PhotoLineKind.STEP
            }
            PhotoLine(text, kind, line.block).also { previous = it }
        }
    }

    /** The draft from [lines] as marked; wrapped step lines are joined into paragraphs. */
    fun draft(lines: List<PhotoLine>): RecipeDraft {
        val steps = mutableListOf<String>()
        var previous: PhotoLine? = null
        for (line in lines) {
            if (line.kind == PhotoLineKind.STEP) {
                val last = previous
                // A wrapped line continues the step above it; a number, a gap or a new paragraph starts one.
                val goesOn = last != null && last.kind == PhotoLineKind.STEP && steps.isNotEmpty() &&
                    !STEP_NUMBER.containsMatchIn(line.text) &&
                    !(line.block != last.block && last.text.endsWithSentence())
                val text = line.text.replace(STEP_NUMBER, "").trim()
                if (goesOn) steps[steps.lastIndex] = join(steps.last(), text) else steps += text
            }
            previous = line
        }
        val meta = lines.filter { it.kind == PhotoLineKind.IGNORE }.map { it.text }
        return RecipeDraft(
            title = lines.filter { it.kind == PhotoLineKind.TITLE }.joinToString(" ") { it.text },
            servings = lines.firstNotNullOfOrNull { servingsIn(it.text) },
            totalTimeMin = meta.filter(::isMeta).firstNotNullOfOrNull(StepTimers::minutesIn),
            ingredientLines = lines.filter { it.kind == PhotoLineKind.INGREDIENT }.map { it.text },
            steps = steps.filter { it.isNotEmpty() },
            source = RecipeSource.PHOTO,
        )
    }

    private fun isTitleCandidate(text: String): Boolean {
        val normalized = TextNormalizer.normalize(text)
        return text.length <= MAX_TITLE_CHARS && !STARTS_WITH_AMOUNT.containsMatchIn(text) && !isMeta(text) &&
            !isHeading(text, normalized, INGREDIENT_HEADINGS) && !isHeading(text, normalized, STEP_HEADINGS)
    }

    /** "Suroviny", "Postup", "Suroviny na 4 porcie" — but not a sentence that starts with the word. */
    private fun isHeading(text: String, normalized: String, headings: List<String>): Boolean =
        headings.any { heading ->
            normalized == heading ||
                (normalized.startsWith("$heading ") && normalized.split(' ').size <= MAX_HEADING_WORDS && !text.endsWithSentence())
        }

    /** "4 porcie · 30 minút", "Čas prípravy: 45 min". */
    private fun isMeta(text: String): Boolean {
        if (text.length > MAX_META_CHARS) return false
        val normalized = TextNormalizer.normalize(text)
        return servingsIn(text) != null || (TIME_LABEL.containsMatchIn(normalized) && StepTimers.minutesIn(text) != null)
    }

    private fun servingsIn(text: String): Int? {
        val normalized = TextNormalizer.normalize(text)
        val match = SERVINGS_AFTER.find(normalized) ?: SERVINGS_BEFORE.find(normalized) ?: return null
        return match.groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() }?.takeIf { it in 1..MAX_SERVINGS }
    }

    private fun looksLikeIngredient(text: String, normalized: String, matcher: RecipeLineMatcher): Boolean =
        text.length <= MAX_INGREDIENT_CHARS &&
            (
                STARTS_WITH_AMOUNT.containsMatchIn(text) ||
                    (normalized.split(' ').size <= MAX_INGREDIENT_WORDS && matcher.confidence(text) >= RecipeLineMatcher.MIN_CONFIDENCE)
                )

    /** A wrapped line goes on when the previous one has not finished its sentence. */
    private fun continues(previous: String, text: String): Boolean = !previous.endsWithSentence() || text.first().isLowerCase()

    private fun join(start: String, next: String): String =
        if (start.endsWith("-") && next.firstOrNull()?.isLowerCase() == true) start.dropLast(1) + next else "$start $next"

    private fun String.endsWithSentence(): Boolean = trimEnd().lastOrNull()?.let { it in ".!?" } == true

    private const val TITLE_WINDOW = 3
    private const val MAX_TITLE_CHARS = 60
    private const val MAX_META_CHARS = 50
    private const val MAX_HEADING_WORDS = 4
    private const val MAX_INGREDIENT_CHARS = 50
    private const val MAX_INGREDIENT_WORDS = 5
    private const val MAX_SERVINGS = 50

    private val INGREDIENT_HEADINGS = listOf(
        "suroviny", "ingrediencie", "ingredience", "potrebujeme", "potrebujete", "budete potrebovat", "prisady",
        "ingredients", "you will need", "you ll need",
    )
    private val STEP_HEADINGS = listOf(
        "postup", "postup pripravy", "pracovny postup", "priprava", "navod", "method", "instructions", "directions",
        "preparation", "steps",
    )
    private val LONE_NUMBER = Regex("""\d{1,3}""")
    private val STEP_NUMBER = Regex("""^\s*(?:krok\s+)?\d{1,2}\s*[.)]\s+""", RegexOption.IGNORE_CASE)
    private val STARTS_WITH_AMOUNT = Regex("""^\s*(?:\d|[½¼¾⅓⅔]|(?:pol|pul|půl|half)\s)""", RegexOption.IGNORE_CASE)
    private val SERVINGS_AFTER = Regex("""\b(\d{1,2})\s*(?:porci\w*|osob\w*|serving\w*|portion\w*)\b""")
    private val SERVINGS_BEFORE = Regex("""\b(?:porci\w*|serves|servings|portions)\s+(\d{1,2})\b""")
    private val TIME_LABEL = Regex("""\b(?:cas|time|priprava|varenie|pecenie|celkom|total|prep|cook)\b""")
}
