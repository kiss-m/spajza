package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.rating.Verdict
import sk.matej.spajza.domain.repository.RatingRepository

class FakeRatingRepository(initial: Map<String, Rating> = emptyMap()) : RatingRepository {
    val ratings = MutableStateFlow(initial)

    override fun observe(uid: String): Flow<Map<String, Rating>> = ratings

    override suspend fun setVerdict(uid: String, recipeId: String, verdict: Verdict?) = update(recipeId) { it.copy(verdict = verdict) }

    override suspend fun setFavorite(uid: String, recipeId: String, favorite: Boolean) = update(recipeId) { it.copy(favorite = favorite) }

    private fun update(recipeId: String, change: (Rating) -> Rating) {
        val current = ratings.value[recipeId] ?: Rating(recipeId)
        ratings.value = ratings.value + (recipeId to change(current))
    }
}
