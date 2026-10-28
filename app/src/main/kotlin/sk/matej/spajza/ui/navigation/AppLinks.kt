package sk.matej.spajza.ui.navigation

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** A place outside the app asked to open, for example a notification. */
sealed interface AppLink {
    data object Pantry : AppLink {
        const val key = "pantry"
    }

    companion object {
        const val EXTRA_OPEN = "sk.matej.spajza.extra.OPEN"

        fun from(intent: Intent?): AppLink? = when (intent?.getStringExtra(EXTRA_OPEN)) {
            Pantry.key -> Pantry
            else -> null
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
