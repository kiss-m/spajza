package sk.matej.spajza.domain.custom

import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.pantry.IngredientRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductImportTest {
    private val import = ProductImport(MatcherFixture.catalog())

    private val yoghurt = ProductFacts(
        barcode = "8586000000017",
        names = listOf("", "Jogurt biely 3 %"),
        brand = "Rajo, Meggle",
        quantity = 150.0,
        quantityUnit = "g",
        nutriments = Nutriments(kcal = 999.0, protein = 4.5, carbs = 5.0, fat = 3.0, fiber = null, sugar = 6.0, salt = 0.12),
        allergenTags = setOf("en:milk"),
        analysisTags = setOf("en:non-vegan", "en:vegetarian"),
        categoryTags = listOf("en:dairies", "en:fermented-foods", "en:yogurts"),
    )

    @Test
    fun `a product becomes a draft with recomputed energy`() {
        val draft = import.draft(yoghurt)

        assertEquals("b8586000000017", draft.id)
        assertEquals("Jogurt biely 3 %", draft.name)
        assertEquals("Rajo", draft.brand)
        assertEquals(IngredientCategory.DAIRY, draft.category)
        assertEquals(PantryUnit.G, draft.pantryUnit)
        assertEquals(150.0, draft.packageQuantity)
        assertTrue(draft.nutritionKnown)
        // 4 × 4.5 + 4 × 5 + 9 × 3: the label's 999 kcal is ignored.
        assertEquals(65.0, draft.nutrition.kcal, 1e-9)
        assertEquals(5.0, draft.nutrition.sugar, 1e-9, "sugar cannot exceed carbs")
        assertEquals(setOf(Allergen.MILK), draft.allergens)
        assertEquals(setOf(DietFlag.ANIMAL_PRODUCT, DietFlag.DAIRY), draft.dietFlags)
        assertEquals(CustomSource.OPEN_FOOD_FACTS, draft.source)
    }

    @Test
    fun `missing or impossible label values leave nutrition unknown`() {
        assertFalse(import.draft(yoghurt.copy(nutriments = Nutriments(kcal = 60.0))).nutritionKnown)
        assertFalse(import.draft(yoghurt.copy(nutriments = Nutriments(protein = 60.0, carbs = 50.0, fat = 10.0))).nutritionKnown)
        assertFalse(import.draft(yoghurt.copy(nutriments = Nutriments(protein = -1.0, carbs = 5.0, fat = 3.0))).nutritionKnown)
    }

    @Test
    fun `package sizes are read from the quantity or the printed text`() {
        fun size(quantity: Double?, unit: String?, text: String?) =
            import.draft(yoghurt.copy(quantity = quantity, quantityUnit = unit, quantityText = text)).let { it.packageQuantity to it.pantryUnit }

        assertEquals(1000.0 to PantryUnit.ML, size(1.0, "l", null))
        assertEquals(500.0 to PantryUnit.G, size(null, null, "0,5 kg"))
        assertEquals(600.0 to PantryUnit.G, size(null, null, "6 x 100 g"))
        assertEquals(330.0 to PantryUnit.ML, size(null, null, "33 cl"))
        assertEquals(10.0 to PantryUnit.PCS, size(null, null, "10 ks"))
        assertEquals(null to PantryUnit.G, size(null, null, "veľké balenie"))
    }

    @Test
    fun `linked products go to the pantry as the catalog ingredient`() {
        val linked = CustomIngredient(id = "b1", name = "Tomato passata", pantryUnit = PantryUnit.G, catalogId = "tomato")
        assertEquals(IngredientRef.Catalog("tomato"), linked.pantryRef)
        assertEquals(IngredientRef.Custom("b1"), linked.copy(catalogId = null).pantryRef)

        // 360 g of a product kept in grams is three 120 g tomatoes.
        val tomato = MatcherFixture.catalog().ingredient("tomato")
        assertEquals(3.0, linked.amountAs(tomato, 360.0)!!, 1e-9)
        assertEquals(2.0, linked.copy(pantryUnit = PantryUnit.PCS).amountAs(tomato, 2.0))
        assertNull(linked.copy(pantryUnit = PantryUnit.PCS).amountAs(MatcherFixture.catalog().ingredient("pasta"), 2.0))
    }

    @Test
    fun `seafood and gluten allergens set the matching diet flags`() {
        val draft = import.draft(
            yoghurt.copy(allergenTags = setOf("en:crustaceans", "en:gluten"), analysisTags = setOf("en:non-vegetarian")),
        )
        assertEquals(setOf(Allergen.CRUSTACEANS, Allergen.GLUTEN), draft.allergens)
        assertEquals(setOf(DietFlag.MEAT, DietFlag.ANIMAL_PRODUCT, DietFlag.GLUTEN, DietFlag.FISH), draft.dietFlags)
    }
}
