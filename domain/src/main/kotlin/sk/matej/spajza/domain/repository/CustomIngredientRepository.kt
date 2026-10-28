package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.custom.CustomIngredient
import sk.matej.spajza.domain.custom.ProductFacts
import sk.matej.spajza.domain.model.Language

/** The household's own foods. Saving merges field by field, so two members' edits both survive. */
interface CustomIngredientRepository {
    fun observe(householdId: String): Flow<List<CustomIngredient>>

    suspend fun save(householdId: String, ingredient: CustomIngredient, savedBy: String)

    suspend fun delete(householdId: String, id: String)
}

/** A product database looked up by barcode (Open Food Facts). */
interface ProductLookup {
    /** Null when the barcode is unknown; fails without a connection. Names in [language] come first. */
    suspend fun find(barcode: String, language: Language): Result<ProductFacts?>
}
