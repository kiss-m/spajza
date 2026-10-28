package sk.matej.spajza.ui.ownrecipe

/** Recipe page addresses in shared or pasted text. Plain Kotlin, so it is tested without Android. */
object RecipeLinks {
    /** The first web address in [text]; a bare "example.com/recept" gets https. */
    fun urlIn(text: String): String? {
        URL.find(text)?.let { return it.value.trimEnd(*TRAILING) }
        val word = text.trim().takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) } ?: return null
        return if (BARE_HOST.containsMatchIn(word)) "https://${word.trimEnd(*TRAILING)}" else null
    }

    private val URL = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val BARE_HOST = Regex("""^[\p{L}\d-]+(\.[\p{L}\d-]+)*\.[a-z]{2,}(/|$)""", RegexOption.IGNORE_CASE)
    private val TRAILING = charArrayOf('.', ',', ')', ']', '!', '?', ';', ':', '"', '\'', '»', '“', '”')
}
