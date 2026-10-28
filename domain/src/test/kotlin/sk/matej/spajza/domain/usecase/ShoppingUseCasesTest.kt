package sk.matej.spajza.domain.usecase

import kotlinx.coroutines.test.runTest
import sk.matej.spajza.domain.fakes.FakePantryRepository
import sk.matej.spajza.domain.fakes.FakeShoppingRepository
import sk.matej.spajza.domain.matching.MatcherFixture
import sk.matej.spajza.domain.matching.MatcherFixture.MONDAY
import sk.matej.spajza.domain.matching.MatcherFixture.chickenRice
import sk.matej.spajza.domain.matching.MatcherFixture.creamyChicken
import sk.matej.spajza.domain.matching.MatcherFixture.kitchen
import sk.matej.spajza.domain.matching.MatcherFixture.pantry
import sk.matej.spajza.domain.matching.MatcherFixture.taste
import sk.matej.spajza.domain.matching.MatcherFixture.tomatoPasta
import sk.matej.spajza.domain.matching.RecipeMatcher
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.ShoppingItem
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShoppingUseCasesTest {
    private val catalog = MatcherFixture.catalog(tomatoPasta, chickenRice, creamyChicken)
    private val matcher = RecipeMatcher(catalog)
    private val shopping = FakeShoppingRepository()
    private val addMissing = AddMissingToShoppingUseCase(catalog, shopping)

    @Test
    fun `missing ingredients go on the list with the amounts the servings need`() = runTest {
        val match = matcher.evaluate(tomatoPasta, kitchen(pantry("pasta" to null)), taste(), MONDAY, servings = 2)

        val added = addMissing("home", match, addedBy = "me")

        // tomato 240 g = 2 pieces, garlic 10 g; oil and salt are staples, basil is optional.
        assertEquals(2, added)
        val items = shopping.itemsOf()
        assertEquals(setOf("i_tomato", "i_garlic"), items.keys)
        assertEquals(2.0, items.getValue("i_tomato").amount)
        assertEquals(10.0, items.getValue("i_garlic").amount)
        assertEquals(setOf("tomato-pasta"), items.getValue("i_tomato").reasonRecipeIds)
    }

    @Test
    fun `partial pantry quantities add only the shortfall`() = runTest {
        val home = kitchen(pantry("pasta" to 50.0, "tomato" to null, "garlic" to null))
        val match = matcher.evaluate(tomatoPasta, home, taste(), MONDAY, servings = 2)

        addMissing("home", match, addedBy = "me")

        assertEquals(mapOf("i_pasta" to 150.0), shopping.itemsOf().mapValues { it.value.amount })
    }

    @Test
    fun `pieces round up and substitutes are not bought`() = runTest {
        // 3 servings of chicken-rice need 165 g onion = 1.5 pieces; creamy-chicken's butter has oil.
        val home = kitchen(pantry("chicken" to null, "rice" to null, "cream" to null, "pasta" to null))
        addMissing("home", matcher.evaluate(chickenRice, home, taste(), MONDAY, servings = 3), addedBy = "me")
        addMissing("home", matcher.evaluate(creamyChicken, home, taste(), MONDAY, servings = 2), addedBy = "me")

        val items = shopping.itemsOf()
        assertEquals(setOf("i_onion"), items.keys)
        assertEquals(2.0 + 1.0, items.getValue("i_onion").amount)
        assertEquals(setOf("chicken-rice", "creamy-chicken"), items.getValue("i_onion").reasonRecipeIds)
    }

    @Test
    fun `bought items move to the pantry and leave the list`() = runTest {
        val list = FakeShoppingRepository(
            listOf(
                ShoppingItem("i_tomato", IngredientRef.Catalog("tomato"), amount = 2.0, checked = true),
                ShoppingItem("i_garlic", IngredientRef.Catalog("garlic"), checked = true),
                ShoppingItem("text-1", ingredient = null, text = "baking paper", checked = true),
                ShoppingItem("i_onion", IngredientRef.Catalog("onion"), checked = false),
            ),
        )
        val pantryRepo = FakePantryRepository()
        val expiry = LocalDate.of(2026, 10, 30)

        MoveBoughtToPantryUseCase(list, pantryRepo)(
            householdId = "home",
            bought = listOf(
                BoughtItem(list.itemsOf().getValue("i_tomato"), quantity = 4.0, expiresAt = expiry),
                BoughtItem(list.itemsOf().getValue("i_garlic"), quantity = null, expiresAt = null),
                BoughtItem(list.itemsOf().getValue("text-1"), quantity = null, expiresAt = null),
            ),
            addedBy = "me",
        )

        assertEquals(setOf("i_onion"), list.itemsOf().keys)
        val stored = pantryRepo.itemsOf()
        assertEquals(setOf("i_tomato", "i_garlic"), stored.keys)
        assertEquals(4.0, stored.getValue("i_tomato").quantity)
        assertEquals(expiry, stored.getValue("i_tomato").expiresAt)
        assertTrue(stored.getValue("i_garlic").quantity == null)
    }
}
