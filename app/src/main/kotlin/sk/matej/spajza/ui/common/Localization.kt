package sk.matej.spajza.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.domain.model.LocalizedText
import java.util.Locale

/** The language catalog text is shown in; follows the per-app language (English or Slovak). */
@Composable
@ReadOnlyComposable
fun currentLanguage(): Language = languageOf(currentLocale())

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

fun languageOf(locale: Locale): Language = if (locale.language == "en") Language.EN else Language.SK

/** This catalog text in the current language. */
@Composable
@ReadOnlyComposable
fun LocalizedText.text(): String = this[currentLanguage()]
