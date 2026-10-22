package sk.matej.spajza.domain.pantry

import java.time.Instant
import java.time.LocalDate

/** Points either at a catalog ingredient or at a household's custom ingredient. */
sealed interface IngredientRef {
    /** Deterministic Firestore document id, so the same ingredient always lands in one document. */
    val docId: String

    data class Catalog(val id: String) : IngredientRef {
        override val docId: String get() = "i_$id"
    }

    data class Custom(val id: String) : IngredientRef {
        override val docId: String get() = "c_$id"
    }
}

data class PantryItem(
    val ingredient: IngredientRef,
    /** In the ingredient's pantry unit; null when the household does not track the amount. */
    val quantity: Double? = null,
    val expiresAt: LocalDate? = null,
    val addedBy: String? = null,
    val updatedAt: Instant? = null,
) {
    val id: String get() = ingredient.docId

    val catalogId: String? get() = (ingredient as? IngredientRef.Catalog)?.id
}

data class ShoppingItem(
    val id: String,
    /** Null for free-text items. */
    val ingredient: IngredientRef?,
    val text: String? = null,
    /** In the ingredient's pantry unit. */
    val amount: Double? = null,
    val checked: Boolean = false,
    val reasonRecipeIds: Set<String> = emptySet(),
    val addedBy: String? = null,
)
