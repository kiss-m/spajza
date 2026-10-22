package sk.matej.spajza.domain.ai

import sk.matej.spajza.domain.model.Language

data class AiRecipeRequest(
    val availableIngredientIds: List<String>,
    val wish: String? = null,
    val language: Language = Language.SK,
)

/**
 * A recipe draft as free text. It goes through IngredientLineParser and the user's review like any
 * other import, and its nutrition is always recomputed from the ingredient table.
 */
data class AiRecipeDraft(
    val title: String,
    val ingredientLines: List<String>,
    val steps: List<String>,
)

/**
 * Future AI features (recipes on demand, ingredients from a fridge photo, smarter imports) behind
 * one seam. A real implementation will call a backend proxy; an API key never ships in the app.
 */
interface AiRecipeService {
    val isAvailable: Boolean

    suspend fun generateRecipe(request: AiRecipeRequest): Result<AiRecipeDraft>
}

/** The only implementation until AI is switched on. */
object DisabledAiRecipeService : AiRecipeService {
    override val isAvailable: Boolean = false

    override suspend fun generateRecipe(request: AiRecipeRequest): Result<AiRecipeDraft> =
        Result.failure(UnsupportedOperationException("AI features are not enabled"))
}
