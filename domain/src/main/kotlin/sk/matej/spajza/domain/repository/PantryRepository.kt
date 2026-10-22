package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryChange
import sk.matej.spajza.domain.pantry.PantryItem
import java.time.LocalDate

/**
 * The shared household pantry. Implementations must merge concurrent edits from household members:
 * quantities change through increments, never by writing a computed total.
 */
interface PantryRepository {
    fun observe(householdId: String): Flow<List<PantryItem>>

    /** Adds the ingredient or, if it is already there, increases its quantity. */
    suspend fun add(householdId: String, ingredient: IngredientRef, quantity: Double?, expiresAt: LocalDate?, addedBy: String)

    suspend fun remove(householdId: String, itemId: String)

    /** Applies cooking deductions or their undo. */
    suspend fun apply(householdId: String, changes: List<PantryChange>)
}
