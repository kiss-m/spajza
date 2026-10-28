package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sk.matej.spajza.domain.custom.CustomIngredient
import sk.matej.spajza.domain.custom.ProductFacts
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.domain.repository.CustomIngredientRepository
import sk.matej.spajza.domain.repository.ProductLookup

/** Custom ingredients by household id; [initial] goes into [householdId]. */
class FakeCustomIngredientRepository(initial: List<CustomIngredient> = emptyList(), householdId: String = "home") : CustomIngredientRepository {
    val households = MutableStateFlow(mapOf(householdId to initial.associateBy { it.id }))

    fun itemsOf(householdId: String = "home"): Map<String, CustomIngredient> = households.value[householdId].orEmpty()

    override fun observe(householdId: String): Flow<List<CustomIngredient>> = households.map { it[householdId].orEmpty().values.toList() }

    override suspend fun save(householdId: String, ingredient: CustomIngredient, savedBy: String) {
        val all = households.value
        households.value = all + (householdId to all[householdId].orEmpty() + (ingredient.id to ingredient))
    }

    override suspend fun delete(householdId: String, id: String) {
        val all = households.value
        households.value = all + (householdId to all[householdId].orEmpty() - id)
    }
}

class FakeProductLookup(private val products: Map<String, ProductFacts> = emptyMap()) : ProductLookup {
    var offline = false
    val lookups = mutableListOf<String>()

    override suspend fun find(barcode: String, language: Language): Result<ProductFacts?> {
        lookups += barcode
        return if (offline) Result.failure(IllegalStateException("offline")) else Result.success(products[barcode])
    }
}
