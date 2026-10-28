package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.own.RecipeDraft
import sk.matej.spajza.domain.repository.OwnRecipeRepository
import sk.matej.spajza.domain.repository.RecipeImporter

/** Own recipes by household id; [initial] goes into [householdId]. */
class FakeOwnRecipeRepository(initial: List<OwnRecipe> = emptyList(), householdId: String = "home") : OwnRecipeRepository {
    val households = MutableStateFlow(mapOf(householdId to initial.associateBy { it.id }))

    fun itemsOf(householdId: String = "home"): Map<String, OwnRecipe> = households.value[householdId].orEmpty()

    override fun observe(householdId: String): Flow<List<OwnRecipe>> = households.map { it[householdId].orEmpty().values.toList() }

    override suspend fun save(householdId: String, recipe: OwnRecipe, savedBy: String) {
        val all = households.value
        households.value = all + (householdId to all[householdId].orEmpty() + (recipe.id to recipe))
    }

    override suspend fun delete(householdId: String, id: String) {
        val all = households.value
        households.value = all + (householdId to all[householdId].orEmpty() - id)
    }
}

class FakeRecipeImporter(private val pages: Map<String, RecipeDraft> = emptyMap()) : RecipeImporter {
    var offline = false

    override suspend fun fromUrl(url: String): Result<RecipeDraft?> =
        if (offline) Result.failure(IllegalStateException("offline")) else Result.success(pages[url])
}
