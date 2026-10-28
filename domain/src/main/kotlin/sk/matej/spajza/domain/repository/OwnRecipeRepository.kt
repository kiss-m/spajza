package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.own.RecipeDraft

/** The household's own recipes, `households/{hid}/recipes` (D12). */
interface OwnRecipeRepository {
    fun observe(householdId: String): Flow<List<OwnRecipe>>

    suspend fun save(householdId: String, recipe: OwnRecipe, savedBy: String)

    suspend fun delete(householdId: String, id: String)
}

/** Reads a recipe from a web page; null when the page has none in a form the app can read. */
interface RecipeImporter {
    suspend fun fromUrl(url: String): Result<RecipeDraft?>
}
