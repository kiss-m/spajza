package sk.matej.spajza.domain.plan

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.model.RecipeIngredient
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.line
import sk.matej.spajza.domain.testing.recipe
import sk.matej.spajza.domain.unit.MeasureUnit
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanShoppingTest {
    private val monday = LocalDate.of(2026, 11, 2)

    private val catalog = Catalog(
        ingredients = listOf(
            ingredient("pasta"),
            ingredient("onion", pantryUnit = PantryUnit.PCS, gramsPerPiece = 100.0),
            ingredient("milk", pantryUnit = PantryUnit.ML, densityGPerMl = 1.0),
            ingredient("salt"),
            ingredient("pepper"),
            ingredient("basil"),
        ),
        recipes = listOf(
            // Two servings per recipe batch.
            recipe(
                "pasta-dish",
                servings = 2,
                ingredients = listOf(
                    line("pasta", 200.0),
                    line("onion", 100.0, IngredientRole.SECONDARY),
                    line("salt", 5.0, IngredientRole.SEASONING),
                    line("basil", 10.0, IngredientRole.SEASONING, optional = true),
                ),
            ),
            recipe("porridge", servings = 1, ingredients = listOf(line("milk", 250.0))),
            recipe(
                "seasoned",
                servings = 1,
                ingredients = listOf(
                    line("pasta", 100.0),
                    RecipeIngredient("pepper", 0.0, MeasureUnit.TO_TASTE, 0.0, IngredientRole.SEASONING),
                ),
            ),
        ),
        substitutions = emptyList(),
    )

    private fun entry(recipeId: String, servings: Int, date: LocalDate = monday, id: String = "$recipeId-$date") =
        PlanEntry(id, date, PlanMeal.DINNER, recipeId, servings)

    private fun shortfall(
        entries: List<PlanEntry>,
        pantry: List<PantryItem> = emptyList(),
        staples: Set<String> = emptySet(),
        shopping: List<ShoppingItem> = emptyList(),
    ) = PlanShopping.shortfall(catalog, entries, pantry, staples, shopping).associateBy { it.ingredientId }

    @Test
    fun `entries add up and scale to their servings`() {
        val result = shortfall(
            listOf(entry("pasta-dish", servings = 2), entry("pasta-dish", servings = 4, date = monday.plusDays(1)), entry("porridge", 2)),
        )
        // 2 + 4 servings of a 2-serving recipe = 3 batches.
        assertEquals(600.0, result.getValue("pasta").amount)
        assertEquals(3.0, result.getValue("onion").amount)
        assertEquals(500.0, result.getValue("milk").amount)
        assertEquals(setOf("pasta-dish"), result.getValue("pasta").recipeIds)
        // Optional lines are not bought.
        assertTrue("basil" !in result)
    }

    @Test
    fun `what the pantry holds is subtracted, untracked items and staples count as enough`() {
        val result = shortfall(
            listOf(entry("pasta-dish", servings = 4), entry("porridge", 1)),
            pantry = listOf(
                PantryItem(IngredientRef.Catalog("pasta"), quantity = 150.0),
                PantryItem(IngredientRef.Catalog("onion"), quantity = 1.0),
                PantryItem(IngredientRef.Catalog("milk")),
            ),
            staples = setOf("salt"),
        )
        assertEquals(250.0, result.getValue("pasta").amount)
        assertEquals(1.0, result.getValue("onion").amount)
        assertTrue("milk" !in result)
        assertTrue("salt" !in result)
    }

    @Test
    fun `amounts already on the list are subtracted, so asking twice adds nothing`() {
        val first = shortfall(listOf(entry("pasta-dish", servings = 2)))
        val list = first.values.map { ShoppingItem("i_${it.ingredientId}", IngredientRef.Catalog(it.ingredientId), amount = it.amount) }
        assertEquals(emptyMap(), shortfall(listOf(entry("pasta-dish", servings = 2)), shopping = list))

        val partly = shortfall(
            listOf(entry("pasta-dish", servings = 2)),
            shopping = listOf(ShoppingItem("i_pasta", IngredientRef.Catalog("pasta"), amount = 50.0)),
        )
        assertEquals(150.0, partly.getValue("pasta").amount)
    }

    @Test
    fun `a bought item on the list counts as enough`() {
        val result = shortfall(
            listOf(entry("porridge", 2)),
            shopping = listOf(ShoppingItem("i_milk", IngredientRef.Catalog("milk"), amount = 100.0, checked = true)),
        )
        assertTrue("milk" !in result)
    }

    @Test
    fun `pieces round up and a to-taste line is bought without an amount only when missing`() {
        val result = shortfall(listOf(entry("pasta-dish", servings = 1), entry("seasoned", 1)))
        assertEquals(1.0, result.getValue("onion").amount)
        assertEquals(null, result.getValue("pepper").amount)
        assertEquals(setOf("seasoned"), result.getValue("pepper").recipeIds)
        assertEquals(setOf("pasta-dish", "seasoned"), result.getValue("pasta").recipeIds)

        val inPantry = shortfall(listOf(entry("seasoned", 1)), pantry = listOf(PantryItem(IngredientRef.Catalog("pepper"), quantity = 0.5)))
        assertTrue("pepper" !in inPantry)
    }

    @Test
    fun `recipes that no longer exist are skipped`() {
        assertEquals(emptyMap(), shortfall(listOf(entry("own:deleted", 2))))
    }
}
