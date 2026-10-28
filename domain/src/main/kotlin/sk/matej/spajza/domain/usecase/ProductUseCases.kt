package sk.matej.spajza.domain.usecase

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.custom.CustomIngredient
import sk.matej.spajza.domain.custom.CustomSource
import sk.matej.spajza.domain.custom.ProductImport
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.repository.CustomIngredientRepository
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.repository.ProductLookup
import java.time.LocalDate
import java.util.UUID

sealed interface ScanResult {
    val ingredient: CustomIngredient

    /** The household has scanned this product before. */
    data class Known(override val ingredient: CustomIngredient) : ScanResult

    /** Open Food Facts knows it; the draft waits for the user to check it. */
    data class Found(override val ingredient: CustomIngredient) : ScanResult

    /** Nobody knows it, so the user types it in. */
    data class Unknown(override val ingredient: CustomIngredient) : ScanResult

    /** Not known here and Open Food Facts cannot be reached; the user may type it in. */
    data class Offline(override val ingredient: CustomIngredient) : ScanResult
}

/** What a scanned barcode is: known to the household first, then Open Food Facts. */
class ScanProductUseCase(private val lookup: ProductLookup) {
    suspend operator fun invoke(barcode: String, known: Collection<CustomIngredient>, catalog: Catalog, language: Language): ScanResult {
        val code = barcode.filter(Char::isDigit)
        known.firstOrNull { it.barcode == code || it.id == CustomIngredient.idForBarcode(code) }?.let { return ScanResult.Known(it) }
        val blank = CustomIngredient(id = CustomIngredient.idForBarcode(code), name = "", barcode = code, source = CustomSource.MANUAL)
        if (!isBarcode(code)) return ScanResult.Unknown(blank)
        val facts = lookup.find(code, language).getOrElse { return ScanResult.Offline(blank) }
            ?: return ScanResult.Unknown(blank)
        return ScanResult.Found(ProductImport(catalog).draft(facts))
    }

    private fun isBarcode(code: String) = code.length in MIN_LENGTH..MAX_LENGTH

    private companion object {
        /** EAN-8 up to GTIN-14. */
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 14
    }
}

/**
 * Saves a custom food and puts [quantity] (in its own pantry unit) into the pantry. A product
 * linked to a catalog ingredient goes in as that ingredient, converted to its pantry unit, so
 * recipes count it.
 */
class AddCustomToPantryUseCase(
    private val customIngredients: CustomIngredientRepository,
    private val pantry: PantryRepository,
) {
    suspend operator fun invoke(
        householdId: String,
        ingredient: CustomIngredient,
        quantity: Double?,
        expiresAt: LocalDate?,
        uid: String,
        catalog: Catalog,
    ): IngredientRef {
        val saved = ingredient.copy(name = ingredient.name.trim())
        customIngredients.save(householdId, saved, uid)
        val target = saved.catalogId?.let { catalog.ingredientsById[it] }
        val ref = if (target != null) IngredientRef.Catalog(target.id) else IngredientRef.Custom(saved.id)
        val amount = if (target != null) quantity?.let { saved.amountAs(target, it) } else quantity
        pantry.add(householdId, ref, amount, expiresAt, uid)
        return ref
    }

    companion object {
        /** Ids for foods typed in without a barcode. */
        fun newManualId(): String = "m" + UUID.randomUUID().toString().replace("-", "")
    }
}
