package sk.matej.spajza.ui.product

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.custom.CustomIngredient
import sk.matej.spajza.domain.custom.CustomSource
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.nutrition.Nutrition
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductFormTest {
    private val catalog = MatcherFixture.catalog()
    private val scanned = CustomIngredient(
        id = "b8584004040016",
        name = "Smotana na varenie",
        brand = "Rajo",
        barcode = "8584004040016",
        nutrition = Nutrition(kcal = 999.0, protein = 2.6, carbs = 4.1, fat = 12.0, fiber = 0.0, sugar = 4.1, salt = 0.1),
        nutritionKnown = true,
        pantryUnit = PantryUnit.ML,
        packageQuantity = 250.0,
        allergens = setOf(Allergen.MILK),
        catalogId = "cream",
        source = CustomSource.OPEN_FOOD_FACTS,
    )

    @Test
    fun `a drafted product fills the form and the package becomes the amount`() {
        val form = ProductForm.of(scanned, catalog)

        assertEquals("Smotana na varenie", form.name)
        assertEquals("2.6", form.protein)
        assertEquals("12", form.fat)
        assertEquals("250", form.packageText)
        assertEquals("250", form.quantityText)
        assertEquals("cream", form.link?.ingredientId)
        assertEquals(250.0, form.quantity)
    }

    @Test
    fun `energy is computed from the macros and never taken from the label`() {
        val form = ProductForm.of(scanned, catalog)

        assertEquals(4 * 2.6 + 4 * 4.1 + 9 * 12.0, form.kcal!!, 1e-9)
        val saved = form.toIngredient()
        assertEquals(form.kcal!!, saved.nutrition.kcal, 1e-9)
        assertTrue(saved.nutritionKnown)
        assertEquals(setOf(Allergen.MILK), saved.allergens, "what the form does not show is kept")
        assertEquals(CustomSource.OPEN_FOOD_FACTS, saved.source)
    }

    @Test
    fun `missing macros leave the nutrition unknown`() {
        val form = ProductForm.of(scanned, catalog).copy(fat = "")

        assertNull(form.kcal)
        val saved = form.toIngredient()
        assertFalse(saved.nutritionKnown)
        assertEquals(Nutrition.ZERO, saved.nutrition)
    }

    @Test
    fun `impossible label values are not accepted`() {
        val form = ProductForm.of(scanned, catalog).copy(protein = "60", carbs = "50", fat = "10")

        assertNull(form.kcal, "more than 100 g of macros in 100 g")
        assertEquals(4.1, ProductForm.of(scanned, catalog).copy(sugar = "9").toIngredient().nutrition.sugar, "sugar is part of the carbohydrates")
    }

    @Test
    fun `edits are trimmed, decimal commas work and an unlinked product stays own`() {
        val form = ProductForm.of(scanned, catalog).copy(
            name = "  Smotana 12 %  ",
            brand = " ",
            protein = "2,6",
            link = null,
            unit = PantryUnit.PCS,
            pieceText = "250",
            packageText = "",
            quantityText = "0",
        )

        val saved = form.toIngredient()
        assertEquals("Smotana 12 %", saved.name)
        assertNull(saved.brand)
        assertEquals(2.6, saved.nutrition.protein)
        assertNull(saved.catalogId)
        assertEquals(PantryUnit.PCS, saved.pantryUnit)
        assertEquals(250.0, saved.gramsPerPiece)
        assertNull(saved.packageQuantity)
        assertNull(form.quantity, "zero is no amount")
    }

    @Test
    fun `a product needs a name`() {
        val blank = ProductForm.of(CustomIngredient(id = "m1", name = ""), catalog)

        assertFalse(blank.canSave)
        assertTrue(blank.copy(name = "Domáci kváskový chlieb").canSave)
        assertEquals("", blank.quantityText)
        assertEquals(PantryUnit.G, blank.toIngredient().pantryUnit)
    }
}
