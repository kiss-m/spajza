package sk.matej.spajza.ui.pantry

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import sk.matej.spajza.domain.model.LocalizedText
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets the scan and receipt screens tell the pantry what they added, so its snackbar confirms it
 * after they close.
 */
@Singleton
class PantryNotices @Inject constructor() {
    private val mutableEvents = MutableSharedFlow<PantryEvent>(extraBufferCapacity = BUFFER)
    val events: SharedFlow<PantryEvent> = mutableEvents.asSharedFlow()

    fun announceAdded(name: LocalizedText) {
        mutableEvents.tryEmit(PantryEvent.Added(name))
    }

    fun announceAddedMany(count: Int) {
        mutableEvents.tryEmit(PantryEvent.AddedMany(count))
    }

    private companion object {
        const val BUFFER = 8
    }
}
