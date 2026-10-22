package sk.matej.spajza.domain.rating

import java.time.Instant

enum class Verdict { LIKE, DISLIKE }

data class Rating(
    val recipeId: String,
    val verdict: Verdict? = null,
    val favorite: Boolean = false,
    val note: String? = null,
)

/** One "cooked it" event; used to learn preferences, not as a nutrition diary. */
data class CookEvent(
    val id: String,
    val recipeId: String,
    val cookedAt: Instant,
    val servings: Int,
)
