package sk.matej.spajza.domain.own

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.ComputedTag
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.nutrition.NutritionCalculator
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.nutrition
import sk.matej.spajza.domain.unit.MeasureUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OwnRecipesTest {
    private val catalog = Catalog(
        ingredients = listOf(
            ingredient("chickpeas", nutrition(kcal = 0.0, protein = 7.0, carbs = 20.0, fat = 2.5), category = IngredientCategory.LEGUMES),
            ingredient("onion", nutrition(kcal = 0.0, protein = 1.1, carbs = 9.0), pantryUnit = PantryUnit.PCS, gramsPerPiece = 110.0, category = IngredientCategory.VEGETABLES),
            ingredient("oil", nutrition(kcal = 0.0, fat = 100.0), pantryUnit = PantryUnit.ML, densityGPerMl = 0.92, category = IngredientCategory.OILS_FATS),
            ingredient("curry", nutrition(kcal = 0.0, protein = 13.0, carbs = 56.0, fat = 14.0), category = IngredientCategory.SPICES),
            ingredient("spinach", nutrition(kcal = 0.0, protein = 2.9, carbs = 3.6), category = IngredientCategory.VEGETABLES),
            ingredient("chicken", nutrition(kcal = 0.0, protein = 22.5, fat = 2.6), category = IngredientCategory.MEAT, dietFlags = setOf(DietFlag.MEAT, DietFlag.ANIMAL_PRODUCT)),
        ),
        recipes = emptyList(),
        substitutions = emptyList(),
    )

    private val curry = OwnRecipe(
        id = "abc",
        title = "Cícerové kari od babky",
        description = "Rýchla večera.",
        servings = 2,
        totalTimeMin = 20,
        ingredients = listOf(
            OwnIngredientLine("400 g cíceru", "chickpeas", 400.0, MeasureUnit.G),
            OwnIngredientLine("1 cibuľa", "onion", 1.0, MeasureUnit.PCS),
            OwnIngredientLine("2 lyžice oleja", "oil", 2.0, MeasureUnit.TBSP),
            OwnIngredientLine("kari podľa chuti", "curry", null, MeasureUnit.TO_TASTE),
        ),
        steps = listOf(OwnStep("Cibuľu opeč do sklovita."), OwnStep("Pridaj cícer a duste 10 minút.", timerSeconds = 600)),
    )

    @Test
    fun `own recipes join the catalog under prefixed ids and an empty list changes nothing`() {
        assertSame(catalog, OwnRecipes.merge(catalog, emptyList()))

        val merged = OwnRecipes.merge(catalog, listOf(curry))
        val recipe = merged.recipesById.getValue("own:abc")
        assertEquals(LocalizedText.same("Cícerové kari od babky"), recipe.title)
        assertTrue(OwnRecipes.isOwn(recipe.id))
        assertEquals("abc", OwnRecipes.ownId(recipe.id))
        assertEquals(null, OwnRecipes.ownId("goulash"))
        assertEquals(catalog.ingredients, merged.ingredients)
    }

    @Test
    fun `matched lines are weighed and roles follow the ingredient`() {
        val recipe = OwnRecipes.toRecipe(curry, catalog)

        assertEquals(listOf(400.0, 110.0, 2 * 15 * 0.92, 0.0), recipe.ingredients.map { it.grams })
        assertEquals(
            listOf(IngredientRole.MAIN, IngredientRole.MAIN, IngredientRole.SECONDARY, IngredientRole.SEASONING),
            recipe.ingredients.map { it.role },
        )
        assertEquals(listOf(0, 1), recipe.steps.map { it.index })
        assertEquals(600, recipe.steps[1].timerSeconds)
        assertEquals(LocalizedText.same("Pridaj cícer a duste 10 minút."), recipe.steps[1].text)
    }

    @Test
    fun `nutrition counts only the weighed lines and every tag applies when all lines count`() {
        val recipe = OwnRecipes.toRecipe(curry, catalog)
        val perServing = NutritionCalculator(catalog.ingredientsById::get).perServing(recipe)

        assertEquals((400 * 7.0 + 110 * 1.1) / 100 / 2, perServing.protein, 1e-9)
        assertTrue(OwnRecipes.uncounted(curry, catalog).isEmpty())
        assertTrue(ComputedTag.VEGAN in recipe.computedTags)
        assertTrue(ComputedTag.QUICK in recipe.computedTags)
    }

    @Test
    fun `unmatched lines and amounts that cannot be weighed are shown but not counted`() {
        val messy = curry.copy(
            ingredients = curry.ingredients + listOf(
                OwnIngredientLine("1 kocka zeleninového bujónu"),
                OwnIngredientLine("2 hrnčeky špenátu", "spinach", 2.0, MeasureUnit.CUP),
                OwnIngredientLine("trocha masla", "butter-unknown", 1.0, MeasureUnit.TBSP),
            ),
        )
        val recipe = OwnRecipes.toRecipe(messy, catalog)

        assertEquals(listOf("chickpeas", "onion", "oil", "curry", "spinach"), recipe.ingredients.map { it.ingredientId })
        val spinach = recipe.ingredients.last()
        assertEquals(2.0, spinach.amount)
        assertEquals(MeasureUnit.CUP, spinach.unit)
        assertEquals(0.0, spinach.grams)
        assertEquals(
            listOf("1 kocka zeleninového bujónu", "2 hrnčeky špenátu", "trocha masla"),
            OwnRecipes.uncounted(messy, catalog).map { it.text },
        )
        assertEquals(listOf("1 kocka zeleninového bujónu", "trocha masla"), OwnRecipes.unmatched(messy, catalog).map { it.text })
        // A line the app cannot see might be meat or sugar, so only the time-based tag is safe.
        assertEquals(setOf(ComputedTag.QUICK), recipe.computedTags)
    }

    @Test
    fun `meat makes a recipe neither vegan nor vegetarian`() {
        val withChicken = curry.copy(ingredients = curry.ingredients + OwnIngredientLine("300 g kuracieho mäsa", "chicken", 300.0, MeasureUnit.G))
        val tags = OwnRecipes.toRecipe(withChicken, catalog).computedTags

        assertFalse(ComputedTag.VEGAN in tags)
        assertFalse(ComputedTag.VEGETARIAN in tags)
    }

    @Test
    fun `an amount without a unit counts pieces when the ingredient has a piece weight`() {
        val onions = curry.copy(ingredients = listOf(OwnIngredientLine("2 cibule", "onion", 2.0, null)))
        val recipe = OwnRecipes.toRecipe(onions, catalog)

        assertEquals(MeasureUnit.PCS, recipe.ingredients.single().unit)
        assertEquals(220.0, recipe.ingredients.single().grams)
        assertTrue(OwnRecipes.uncounted(onions, catalog).isEmpty())

        val chickpeas = curry.copy(ingredients = listOf(OwnIngredientLine("400 cíceru", "chickpeas", 400.0, null)))
        assertEquals(MeasureUnit.TO_TASTE, OwnRecipes.toRecipe(chickpeas, catalog).ingredients.single().unit, "still needed, but not weighed")
        assertEquals(1, OwnRecipes.uncounted(chickpeas, catalog).size)
    }
}
