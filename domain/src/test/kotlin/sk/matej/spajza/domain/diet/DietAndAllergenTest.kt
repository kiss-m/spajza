package sk.matej.spajza.domain.diet

import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.line
import sk.matej.spajza.domain.testing.recipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DietAndAllergenTest {
    private val ingredients = listOf(
        ingredient("pasta", dietFlags = setOf(DietFlag.GLUTEN)).copy(allergens = setOf(Allergen.GLUTEN)),
        ingredient("cheese", dietFlags = setOf(DietFlag.DAIRY, DietFlag.ANIMAL_PRODUCT)).copy(allergens = setOf(Allergen.MILK)),
        ingredient("bacon", dietFlags = setOf(DietFlag.MEAT, DietFlag.PORK, DietFlag.ANIMAL_PRODUCT)),
        ingredient("tuna", dietFlags = setOf(DietFlag.FISH, DietFlag.ANIMAL_PRODUCT)).copy(allergens = setOf(Allergen.FISH)),
        ingredient("honey", dietFlags = setOf(DietFlag.ANIMAL_PRODUCT)),
        ingredient("tomato"),
    ).associateBy { it.id }
    private val lookup = ingredients::get

    private val cheesePasta = recipe(ingredients = listOf(line("pasta", 100.0), line("cheese", 30.0)))
    private val baconPasta = recipe(ingredients = listOf(line("pasta", 100.0), line("bacon", 50.0)))
    private val tunaPasta = recipe(ingredients = listOf(line("pasta", 100.0), line("tuna", 80.0)))
    private val tomatoPasta = recipe(
        ingredients = listOf(line("pasta", 100.0), line("tomato", 200.0), line("cheese", 10.0, IngredientRole.SEASONING, optional = true)),
    )

    @Test
    fun `diets forbid the right flags`() {
        assertTrue(DietFilter.allows(cheesePasta, lookup, setOf(Diet.VEGETARIAN)))
        assertFalse(DietFilter.allows(cheesePasta, lookup, setOf(Diet.VEGAN)))
        assertFalse(DietFilter.allows(baconPasta, lookup, setOf(Diet.PORK_FREE)))
        assertFalse(DietFilter.allows(tunaPasta, lookup, setOf(Diet.VEGETARIAN)))
        assertTrue(DietFilter.allows(tunaPasta, lookup, setOf(Diet.PESCATARIAN)))
        assertFalse(DietFilter.allows(tunaPasta, lookup, setOf(Diet.GLUTEN_FREE)))
        assertFalse(DietFilter.allows(recipe(ingredients = listOf(line("honey", 10.0))), lookup, setOf(Diet.VEGAN)))
        assertTrue(DietFilter.allows(baconPasta, lookup, emptySet()))
    }

    @Test
    fun `optional lines are reported but do not block`() {
        assertTrue(DietFilter.allows(tomatoPasta, lookup, setOf(Diet.VEGAN)))
        assertEquals(
            listOf(DietViolation("cheese", Diet.VEGAN, optional = true)),
            DietFilter.violations(tomatoPasta, lookup, setOf(Diet.VEGAN)),
        )
    }

    @Test
    fun `allergen guard finds conflicts`() {
        assertFalse(AllergenGuard.isSafe(cheesePasta, lookup, setOf(Allergen.MILK)))
        assertTrue(AllergenGuard.isSafe(cheesePasta, lookup, setOf(Allergen.FISH)))
        assertTrue(AllergenGuard.isSafe(tomatoPasta, lookup, setOf(Allergen.MILK)))
        assertEquals(
            listOf(AllergenConflict("pasta", setOf(Allergen.GLUTEN), false), AllergenConflict("tuna", setOf(Allergen.FISH), false)),
            AllergenGuard.conflicts(tunaPasta, lookup, setOf(Allergen.GLUTEN, Allergen.FISH)),
        )
    }
}
