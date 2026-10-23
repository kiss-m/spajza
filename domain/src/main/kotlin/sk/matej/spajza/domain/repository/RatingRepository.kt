package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.rating.Verdict

interface RatingRepository {
    /** Ratings by recipe id. */
    fun observe(uid: String): Flow<Map<String, Rating>>

    /** Null clears a like or dislike. */
    suspend fun setVerdict(uid: String, recipeId: String, verdict: Verdict?)

    suspend fun setFavorite(uid: String, recipeId: String, favorite: Boolean)
}
