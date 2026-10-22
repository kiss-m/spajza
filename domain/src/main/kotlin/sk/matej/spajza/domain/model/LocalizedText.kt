package sk.matej.spajza.domain.model

enum class Language { SK, EN }

/** User-facing catalog text in both supported languages. */
data class LocalizedText(val sk: String, val en: String) {
    operator fun get(language: Language): String = when (language) {
        Language.SK -> sk
        Language.EN -> en
    }

    companion object {
        /** Single-language text, e.g. for household recipes typed by the user. */
        fun same(text: String) = LocalizedText(sk = text, en = text)
    }
}
