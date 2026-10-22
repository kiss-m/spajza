package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.rating.CookEvent

interface CookHistoryRepository {
    fun observe(uid: String): Flow<List<CookEvent>>

    /** Returns the id of the new event. */
    suspend fun add(uid: String, recipeId: String, servings: Int): String

    suspend fun remove(uid: String, eventId: String)
}
