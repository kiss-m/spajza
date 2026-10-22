package sk.matej.spajza.domain.catalog

import sk.matej.spajza.domain.model.ComputedTag
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.line
import sk.matej.spajza.domain.testing.nutrition
import sk.matej.spajza.domain.testing.recipe
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogTaggerTest {
    private val ingredients = listOf(
        ingredient(
            "chicken",
            nutrition(kcal = 120.0, protein = 22.5, fat = 2.6),
            dietFlags = setOf(DietFlag.MEAT, DietFlag.ANIMAL_PRODUCT),
        ),
        ingredient("salmon", nutrition(kcal = 208.0, protein = 20.0, fat = 13.0), dietFlags = setOf(DietFlag.FISH, DietFlag.ANIMAL_PRODUCT), priceTier = PriceTier.EXPENSIVE),
        ingredient("cheese", nutrition(kcal = 350.0, protein = 25.0, fat = 27.0), dietFlags = setOf(DietFlag.DAIRY, DietFlag.ANIMAL_PRODUCT)),
        ingredient("rice", nutrition(kcal = 360.0, protein = 7.0, carbs = 80.0), priceTier = PriceTier.CHEAP),
        ingredient("lentils", nutrition(kcal = 350.0, protein = 25.0, carbs = 60.0), priceTier = PriceTier.CHEAP),
        ingredient("onion", nutrition(kcal = 40.0, carbs = 9.0, sugar = 4.0), priceTier = PriceTier.CHEAP),
        ingredient("sugar", nutrition(kcal = 400.0, carbs = 100.0, sugar = 100.0), priceTier = PriceTier.CHEAP),
        ingredient("saffron", nutrition(kcal = 310.0), priceTier = PriceTier.EXPENSIVE),
    ).associateBy { it.id }
    private val tagger = CatalogTagger(ingredients::get)

    @Test
    fun `plant-only recipe is vegan and vegetarian`() {
        val tags = tagger.computeTags(recipe(ingredients = listOf(line("lentils", 100.0), line("onion", 50.0))))
        assertTrue(ComputedTag.VEGAN in tags)
        assertTrue(ComputedTag.VEGETARIAN in tags)
    }

    @Test
    fun `dairy recipe is vegetarian but not vegan`() {
        val tags = tagger.computeTags(recipe(ingredients = listOf(line("rice", 80.0), line("cheese", 30.0))))
        assertFalse(ComputedTag.VEGAN in tags)
        assertTrue(ComputedTag.VEGETARIAN in tags)
    }

    @Test
    fun `optional animal ingredient still blocks vegan`() {
        val tags = tagger.computeTags(
            recipe(ingredients = listOf(line("rice", 80.0), line("cheese", 10.0, IngredientRole.SEASONING, optional = true))),
        )
        assertFalse(ComputedTag.VEGAN in tags)
    }

    @Test
    fun `meat and fish block vegetarian`() {
        assertFalse(ComputedTag.VEGETARIAN in tagger.computeTags(recipe(ingredients = listOf(line("chicken", 150.0)))))
        assertFalse(ComputedTag.VEGETARIAN in tagger.computeTags(recipe(ingredients = listOf(line("salmon", 150.0)))))
    }

    @Test
    fun `high protein needs both grams and energy share`() {
        // 200 g chicken: 240 kcal, 45 g protein -> 75 % of energy from protein.
        assertTrue(ComputedTag.HIGH_PROTEIN in tagger.computeTags(recipe(ingredients = listOf(line("chicken", 200.0)))))
        // 200 g rice: 14 g protein -> too little.
        assertFalse(ComputedTag.HIGH_PROTEIN in tagger.computeTags(recipe(ingredients = listOf(line("rice", 200.0)))))
        // 120 g lentils + 150 g rice: 30 + 10.5 g protein but 960 kcal -> 17 % share.
        assertFalse(
            ComputedTag.HIGH_PROTEIN in tagger.computeTags(recipe(ingredients = listOf(line("lentils", 120.0), line("rice", 150.0)))),
        )
    }

    @Test
    fun `bulk needs high energy and enough protein`() {
        // 250 g chicken + 150 g rice: 840 kcal, 66.75 g protein.
        val bulk = recipe(ingredients = listOf(line("chicken", 250.0), line("rice", 150.0)))
        assertTrue(ComputedTag.BULK in tagger.computeTags(bulk))
        // 250 g rice: 900 kcal but only 17.5 g protein.
        assertFalse(ComputedTag.BULK in tagger.computeTags(recipe(ingredients = listOf(line("rice", 250.0)))))
    }

    @Test
    fun `light means low energy and little sugar per serving`() {
        assertTrue(ComputedTag.LIGHT in tagger.computeTags(recipe(ingredients = listOf(line("chicken", 200.0), line("onion", 100.0)))))
        assertFalse(ComputedTag.LIGHT in tagger.computeTags(recipe(ingredients = listOf(line("sugar", 30.0)))))
        assertFalse(ComputedTag.LIGHT in tagger.computeTags(recipe(ingredients = listOf(line("rice", 150.0)))))
    }

    @Test
    fun `per-serving thresholds use recipe servings`() {
        // 900 kcal total over 2 servings = 450 kcal each, which still counts as light.
        val shared = recipe(servings = 2, ingredients = listOf(line("rice", 250.0)))
        assertTrue(ComputedTag.LIGHT in tagger.computeTags(shared))
    }

    @Test
    fun `cheap averages price tiers of main and secondary lines weighted by role`() {
        val chickenRice = recipe(
            ingredients = listOf(
                line("chicken", 150.0, IngredientRole.MAIN),
                line("rice", 80.0, IngredientRole.MAIN),
                line("onion", 50.0, IngredientRole.SECONDARY),
            ),
        )
        assertTrue(ComputedTag.CHEAP in tagger.computeTags(chickenRice))
        val chickenOnly = recipe(ingredients = listOf(line("chicken", 150.0)))
        assertFalse(ComputedTag.CHEAP in tagger.computeTags(chickenOnly))
    }

    @Test
    fun `any expensive main or secondary line blocks cheap, seasoning does not`() {
        val withSalmon = recipe(
            ingredients = listOf(
                line("rice", 200.0, IngredientRole.MAIN),
                line("onion", 50.0, IngredientRole.MAIN),
                line("salmon", 50.0, IngredientRole.SECONDARY),
            ),
        )
        assertFalse(ComputedTag.CHEAP in tagger.computeTags(withSalmon))
        val withSaffron = recipe(
            ingredients = listOf(line("rice", 200.0), line("saffron", 0.1, IngredientRole.SEASONING)),
        )
        assertTrue(ComputedTag.CHEAP in tagger.computeTags(withSaffron))
    }

    @Test
    fun `quick means at most 20 minutes in total`() {
        assertTrue(ComputedTag.QUICK in tagger.computeTags(recipe(totalTimeMin = 20, ingredients = listOf(line("rice", 80.0)))))
        assertFalse(ComputedTag.QUICK in tagger.computeTags(recipe(totalTimeMin = 21, ingredients = listOf(line("rice", 80.0)))))
    }

    @Test
    fun `thresholds come from the config object`() {
        val strict = CatalogTagger(ingredients::get, TagThresholds(quickMaxMinutes = 10))
        assertFalse(ComputedTag.QUICK in strict.computeTags(recipe(totalTimeMin = 15, ingredients = listOf(line("rice", 80.0)))))
    }
}
