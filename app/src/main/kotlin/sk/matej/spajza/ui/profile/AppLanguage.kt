package sk.matej.spajza.ui.profile

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** In-app language through the per-app language API; [SYSTEM] follows the phone. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    SK("sk"),
    EN("en"),
    ;

    fun apply() {
        AppCompatDelegate.setApplicationLocales(
            tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList(),
        )
    }

    companion object {
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return entries.firstOrNull { it.tag != null && tag.startsWith(it.tag) } ?: SYSTEM
        }
    }
}
