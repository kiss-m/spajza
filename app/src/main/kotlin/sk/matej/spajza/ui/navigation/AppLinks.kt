package sk.matej.spajza.ui.navigation

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import sk.matej.spajza.ui.ownrecipe.RecipeLinks
import javax.inject.Inject
import javax.inject.Singleton

/** A place outside the app asked to open, for example a notification or "Share" from a browser. */
sealed interface AppLink {
    data object Pantry : AppLink {
        const val key = "pantry"
    }

    /** A recipe page shared from another app, to import as an own recipe. */
    data class ImportRecipe(val url: String) : AppLink

    companion object {
        const val EXTRA_OPEN = "sk.matej.spajza.extra.OPEN"

        fun from(intent: Intent?): AppLink? {
            intent ?: return null
            if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
                return intent.getStringExtra(Intent.EXTRA_TEXT)?.let(RecipeLinks::urlIn)?.let(::ImportRecipe)
            }
            return when (intent.getStringExtra(EXTRA_OPEN)) {
                Pantry.key -> Pantry
                else -> null
            }
        }
    }
}

/**
 * Links waiting for the main screen. A link that arrives before sign-in or onboarding is finished
 * stays here until the main screen shows.
 */
@Singleton
class AppLinks @Inject constructor() {
    private val mutablePending = MutableStateFlow<AppLink?>(null)
    val pending: StateFlow<AppLink?> = mutablePending.asStateFlow()

    fun open(link: AppLink) {
        mutablePending.value = link
    }

    fun consume(link: AppLink) = mutablePending.update { if (it == link) null else it }
}
