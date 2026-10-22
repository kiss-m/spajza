package sk.matej.spajza.data.seed

enum class SeedIssueCode {
    PARSE_ERROR,
    INVALID_ID,
    DUPLICATE_ID,
    MISSING_TRANSLATION,
    MISSING_NUTRITION,
    INVALID_NUTRITION,
    INVALID_NUTRITION_SOURCE,
    INVALID_VALUE,
    MISSING_PANTRY_CONVERSION,
    UNKNOWN_INGREDIENT,
    UNKNOWN_RECIPE,
    MISSING_CONVERSION,
    DUPLICATE_LINE,
    INVALID_RECIPE,
    INVALID_SUBSTITUTION,
}

/** One problem found in the seed; [location] names the file and entry, e.g. `recipes/italian.json#carbonara`. */
data class SeedIssue(val code: SeedIssueCode, val location: String, val message: String) {
    override fun toString() = "$code at $location: $message"
}

class SeedValidationException(val issues: List<SeedIssue>) : IllegalStateException(
    "The bundled seed has ${issues.size} problem(s):\n" + issues.take(50).joinToString("\n") { "  - $it" },
)
