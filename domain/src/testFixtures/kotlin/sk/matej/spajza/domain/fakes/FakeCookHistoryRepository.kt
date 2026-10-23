package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import sk.matej.spajza.domain.rating.CookEvent
import sk.matej.spajza.domain.repository.CookHistoryRepository
import java.time.Instant

class FakeCookHistoryRepository : CookHistoryRepository {
    val events = MutableStateFlow<List<CookEvent>>(emptyList())
    private var nextId = 1

    override fun observe(uid: String): Flow<List<CookEvent>> = events

    override suspend fun add(uid: String, recipeId: String, servings: Int): String {
        val id = "event-${nextId++}"
        events.value = events.value + CookEvent(id, recipeId, Instant.EPOCH, servings)
        return id
    }

    override suspend fun remove(uid: String, eventId: String) {
        events.value = events.value.filterNot { it.id == eventId }
    }
}
