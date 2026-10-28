package sk.matej.spajza.domain.usecase

import kotlinx.coroutines.test.runTest
import sk.matej.spajza.domain.custom.CustomIngredient
import sk.matej.spajza.domain.custom.Nutriments
import sk.matej.spajza.domain.custom.ProductFacts
import sk.matej.spajza.domain.fakes.FakeCustomIngredientRepository
import sk.matej.spajza.domain.fakes.FakePantryRepository
import sk.matej.spajza.domain.fakes.FakeProductLookup
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.pantry.IngredientRef
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProductUseCasesTest {
    private val catalog = MatcherFixture.catalog()
    private val passata = ProductFacts(
        barcode = "8001234567890",
        names = listOf("Tomato passata"),
        quantity = 700.0,
        quantityUnit = "g",
        nutriments = Nutriments(protein = 1.4, carbs = 4.5, fat = 0.2, fiber = 1.2),
    )
    private val lookup = FakeProductLookup(mapOf(passata.barcode to passata))
    private val scan = ScanProductUseCase(lookup)

    @Test
    fun `a product the household knows is not looked up again`() = runTest {
        val known = CustomIngredient(id = "b8001234567890", name = "Passata", barcode = "8001234567890")
        val result = scan("8001234567890", listOf(known), catalog, Language.SK)
        assertEquals(ScanResult.Known(known), result)
        assertTrue(lookup.lookups.isEmpty())
    }

    @Test
    fun `unknown, offline and found products`() = runTest {
        val found = scan("8001234567890", emptyList(), catalog, Language.SK)
        assertIs<ScanResult.Found>(found)
        assertEquals("Tomato passata", found.ingredient.name)
        assertEquals("tomato", found.ingredient.catalogId)

        val unknown = scan("4000000000001", emptyList(), catalog, Language.SK)
        assertIs<ScanResult.Unknown>(unknown)
        assertEquals("4000000000001", unknown.ingredient.barcode)

        lookup.offline = true
        assertIs<ScanResult.Offline>(scan("4000000000001", emptyList(), catalog, Language.SK))
        assertIs<ScanResult.Unknown>(scan("12", emptyList(), catalog, Language.SK), "not a barcode, nothing to look up")
    }

    @Test
    fun `a linked product goes to the pantry as the catalog ingredient`() = runTest {
        val custom = FakeCustomIngredientRepository()
        val pantry = FakePantryRepository()
        val add = AddCustomToPantryUseCase(custom, pantry)
        val product = CustomIngredient(id = "b1", name = " Passata ", pantryUnit = PantryUnit.G, catalogId = "tomato")
        val expiry = LocalDate.of(2026, 11, 30)

        val ref = add("home", product, quantity = 360.0, expiresAt = expiry, uid = "me", catalog = catalog)

        assertEquals(IngredientRef.Catalog("tomato"), ref)
        assertEquals("Passata", custom.itemsOf().getValue("b1").name)
        assertEquals(3.0, pantry.itemsOf().getValue("i_tomato").quantity, "360 g of 120 g tomatoes")
        assertEquals(expiry, pantry.itemsOf().getValue("i_tomato").expiresAt)
    }

    @Test
    fun `an unlinked product goes to the pantry as itself`() = runTest {
        val pantry = FakePantryRepository()
        val add = AddCustomToPantryUseCase(FakeCustomIngredientRepository(), pantry)
        val id = AddCustomToPantryUseCase.newManualId()
        add("home", CustomIngredient(id = id, name = "Horalky", pantryUnit = PantryUnit.PCS), 5.0, null, "me", catalog)
        assertEquals(5.0, pantry.itemsOf().getValue("c_$id").quantity)
        assertTrue(id.startsWith("m") && id.length == 33)
    }
}
