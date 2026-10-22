package sk.matej.spajza.domain.nutrition

import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.line
import sk.matej.spajza.domain.testing.nutrition
import sk.matej.spajza.domain.testing.recipe
import sk.matej.spajza.domain.unit.MeasureUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NutritionCalculatorTest {
    private val chicken = ingredient("chicken", nutrition(kcal = 120.0, protein = 22.5, fat = 2.6, salt = 0.2))
    private val rice = ingredient("rice", nutrition(kcal = 360.0, protein = 6.6, carbs = 80.0, fat = 0.6, fiber = 1.0))
    private val parsley = ingredient("parsley", nutrition(kcal = 36.0, protein = 3.0, carbs = 6.0))
    private val catalog = listOf(chicken, rice, parsley).associateBy { it.id }
    private val calculator = NutritionCalculator(catalog::get)

    private val bowl = recipe(
        servings = 2,
        ingredients = listOf(
            line("chicken", 300.0),
            line("rice", 150.0, IngredientRole.SECONDARY),
            line("parsley", 10.0, IngredientRole.SEASONING, optional = true),
        ),
    )

    @Test
    fun `total sums grams times per-100 g values and skips optional lines`() {
        val total = calculator.total(bowl)
        assertEquals(360.0 + 540.0, total.kcal, DELTA)
        assertEquals(67.5 + 9.9, total.protein, DELTA)
        assertEquals(120.0, total.carbs, DELTA)
        assertEquals(7.8 + 0.9, total.fat, DELTA)
        assertEquals(1.5, total.fiber, DELTA)
        assertEquals(0.6, total.salt, DELTA)
    }

    @Test
    fun `per serving divides by recipe servings`() {
        val perServing = calculator.perServing(bowl)
        assertEquals(450.0, perServing.kcal, DELTA)
        assertEquals(38.7, perServing.protein, DELTA)
    }

    @Test
    fun `optional lines can be included`() {
        val total = calculator.total(bowl, includeOptional = true)
        assertEquals(900.0 + 3.6, total.kcal, DELTA)
    }

    @Test
    fun `scaling servings scales amounts grams and totals but not per serving values`() {
        val scaled = calculator.scale(bowl, servings = 3)
        assertEquals(3, scaled.servings)
        assertEquals(450.0, scaled.ingredients.first { it.ingredientId == "chicken" }.grams, DELTA)
        assertEquals(450.0, scaled.ingredients.first { it.ingredientId == "chicken" }.amount, DELTA)
        assertEquals(1350.0, calculator.total(scaled).kcal, DELTA)
        assertEquals(450.0, calculator.perServing(scaled).kcal, DELTA)
    }

    @Test
    fun `scaling keeps to-taste lines unchanged`() {
        val withSalt = bowl.copy(
            ingredients = bowl.ingredients + line("parsley", 0.0, IngredientRole.SEASONING)
                .copy(amount = 1.0, unit = MeasureUnit.TO_TASTE),
        )
        val scaled = calculator.scale(withSalt, servings = 4)
        assertEquals(1.0, scaled.ingredients.last().amount, DELTA)
    }

    @Test
    fun `unknown ingredient is an error`() {
        assertFailsWith<IllegalArgumentException> {
            calculator.total(recipe(ingredients = listOf(line("unicorn", 100.0))))
        }
    }

    @Test
    fun `protein per 100 kcal`() {
        assertEquals(18.75, chicken.nutrition.proteinPer100Kcal, DELTA)
        assertEquals(0.0, Nutrition.ZERO.proteinPer100Kcal, DELTA)
    }

    private companion object {
        const val DELTA = 1e-9
    }
}
