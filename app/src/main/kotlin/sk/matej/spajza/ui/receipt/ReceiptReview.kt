package sk.matej.spajza.ui.receipt

import sk.matej.spajza.domain.receipt.ReceiptItem
import sk.matej.spajza.domain.receipt.ReceiptParser
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.parseNumber
import sk.matej.spajza.ui.common.quantityInput
import sk.matej.spajza.ui.common.toOption

/** One receipt row on the review screen; plain Kotlin, so it is tested without Android. */
data class ReceiptRow(
    val id: Int,
    /** The row as printed on the receipt. */
    val text: String,
    /** The catalog ingredient it goes into the pantry as; null until the user picks one. */
    val option: IngredientOption?,
    val checked: Boolean,
    /** In the option's pantry unit. */
    val quantityText: String,
) {
    val canAdd: Boolean get() = checked && option != null

    val quantity: Double? get() = parseNumber(quantityText)?.takeIf { it > 0 }

    /** Matched to another ingredient; an amount in a different unit no longer fits. */
    fun matchedTo(choice: IngredientOption): ReceiptRow = copy(
        option = choice,
        checked = true,
        quantityText = if (option?.pantryUnit == choice.pantryUnit) quantityText else "",
    )

    companion object {
        /** Confident matches start ticked; the rest wait for the user. */
        fun of(items: List<ReceiptItem>): List<ReceiptRow> = items.mapIndexed { index, item ->
            ReceiptRow(
                id = index,
                text = item.text,
                option = item.ingredient?.toOption(),
                checked = item.ingredient != null && item.confidence >= ReceiptParser.MIN_CONFIDENCE,
                quantityText = quantityInput(item.quantity?.let { Math.round(it * 10) / 10.0 }),
            )
        }
    }
}

/** An ingredient going to the pantry; rows of one ingredient are added up. */
data class ReceiptAddition(val ingredientId: String, val quantity: Double?)

object ReceiptAdditions {
    fun of(rows: List<ReceiptRow>): List<ReceiptAddition> =
        rows.mapNotNull { row -> row.option?.takeIf { row.checked }?.let { it.ingredientId to row.quantity } }
            .groupBy({ it.first }, { it.second })
            .map { (id, quantities) ->
                val amounts = quantities.filterNotNull()
                ReceiptAddition(id, if (amounts.isEmpty()) null else amounts.sum())
            }
}
