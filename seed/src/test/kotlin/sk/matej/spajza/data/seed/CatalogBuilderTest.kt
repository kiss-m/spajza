package sk.matej.spajza.data.seed

import sk.matej.spajza.domain.model.ComputedTag
import sk.matej.spajza.domain.model.NutritionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogBuilderTest {
    private val builder = CatalogBuilder()

    private val ingredientsJson = """
        [
          {"id": "lentils", "name": {"sk": "Šošovica", "en": "Lentils"}, "category": "LEGUMES",
           "nutrition": {"kcal": 352, "protein": 24.6, "carbs": 63.4, "fat": 1.1, "fiber": 10.7, "sugar": 2.0, "salt": 0.01},
           "source": "USDA FDC 172420", "pantryUnit": "G", "density": 0.8, "price": "CHEAP"},
          {"id": "oil", "name": {"sk": "Olej", "en": "Oil"}, "category": "OILS_FATS",
           "nutrition": {"kcal": 884, "protein": 0, "carbs": 0, "fat": 100, "fiber": 0, "sugar": 0, "salt": 0},
           "source": "estimate", "pantryUnit": "ML", "density": 0.92, "price": "CHEAP", "staple": true},
          {"id": "egg", "name": {"sk": "Vajce", "en": "Egg"}, "category": "EGGS",
           "nutrition": {"kcal": 143, "protein": 12.6, "carbs": 0.7, "fat": 9.5, "fiber": 0, "sugar": 0.4, "salt": 0.36},
           "source": "estimate", "pantryUnit": "PCS", "gramsPerPiece": 50, "price": "CHEAP",
           "flags": ["EGG", "ANIMAL_PRODUCT"], "allergens": ["EGGS"]},
          {"id": "salt", "name": {"sk": "Soľ", "en": "Salt"}, "category": "SPICES",
           "nutrition": {"kcal": 0, "protein": 0, "carbs": 0, "fat": 0, "fiber": 0, "sugar": 0, "salt": 100},
           "source": "estimate", "pantryUnit": "G", "density": 1.2, "price": "CHEAP", "staple": true}
        ]
    """.trimIndent()

    private fun recipeJson(
        id: String = "lentil-stew",
        lines: String = """
            {"id": "lentils", "amount": 1, "unit": "CUP", "role": "MAIN"},
            {"id": "oil", "amount": 1, "unit": "TBSP", "role": "SECONDARY"},
            {"id": "salt", "unit": "TO_TASTE", "role": "SEASONING"}
        """,
        extra: String = "",
    ) = """
        {"id": "$id", "title": {"sk": "Šošovicový prívarok", "en": "Lentil stew"},
         "description": {"sk": "Hustý a sýty.", "en": "Thick and filling."},
         "cuisine": "SLOVAK_CZECH", "mealTypes": ["LUNCH"], "totalTimeMin": 40, "activeTimeMin": 15,
         "difficulty": 1, "servings": 2, "equipment": ["STOVE"], $extra
         "ingredients": [$lines],
         "steps": [{"sk": "Uvar šošovicu.", "en": "Cook the lentils.", "timerSeconds": 1500}]}
    """.trimIndent()

    private fun sources(
        recipes: String = "[${recipeJson()}]",
        ingredients: String = ingredientsJson,
        substitutions: String = "[]",
    ) = SeedSources(
        ingredients = listOf(SeedFile("ingredients/test.json", ingredients)),
        recipes = listOf(SeedFile("recipes/test.json", recipes)),
        substitutions = listOf(SeedFile("substitutions/test.json", substitutions)),
    )

    private fun codes(result: CatalogBuildResult) = result.issues.map { it.code }.toSet()

    @Test
    fun `valid seed builds a normalized catalog`() {
        val result = builder.build(sources())
        assertEquals(emptyList(), result.issues)
        val recipe = result.catalog.recipes.single()
        val lentils = recipe.ingredients.first { it.ingredientId == "lentils" }
        assertEquals(200.0, lentils.grams, 1e-9) // 1 cup × 250 ml × 0.8 g/ml
        assertEquals(13.8, recipe.ingredients.first { it.ingredientId == "oil" }.grams, 1e-9)
        assertEquals(0.0, recipe.ingredients.first { it.ingredientId == "salt" }.grams)
        assertEquals(NutritionSource.Usda(172420), result.catalog.ingredientsById.getValue("lentils").nutritionSource)
        assertTrue(ComputedTag.VEGAN in recipe.computedTags)
        assertTrue(ComputedTag.CHEAP in recipe.computedTags)
        assertEquals(listOf(0), recipe.steps.map { it.index })
    }

    @Test
    fun `unknown ingredient and missing conversion are reported`() {
        val result = builder.build(
            sources(
                recipes = "[${recipeJson(lines = """
                    {"id": "unicorn", "amount": 1, "unit": "G", "role": "MAIN"},
                    {"id": "egg", "amount": 2, "unit": "TBSP", "role": "MAIN"}
                """)}]",
            ),
        )
        assertEquals(setOf(SeedIssueCode.UNKNOWN_INGREDIENT, SeedIssueCode.MISSING_CONVERSION), codes(result))
        assertTrue(result.issues.all { it.location == "recipes/test.json#lentil-stew" })
        assertFailsWith<SeedValidationException> { result.getOrThrow() }
    }

    @Test
    fun `missing translation is reported`() {
        val json = recipeJson().replace("\"en\": \"Lentil stew\"", "\"en\": \" \"")
        assertEquals(setOf(SeedIssueCode.MISSING_TRANSLATION), codes(builder.build(sources(recipes = "[$json]"))))
    }

    @Test
    fun `duplicate ids and duplicate lines are reported`() {
        val twice = "[${recipeJson()}, ${recipeJson()}]"
        assertEquals(setOf(SeedIssueCode.DUPLICATE_ID), codes(builder.build(sources(recipes = twice))))
        val doubled = recipeJson(
            lines = """
                {"id": "lentils", "amount": 100, "unit": "G", "role": "MAIN"},
                {"id": "lentils", "amount": 50, "unit": "G", "role": "MAIN"}
            """,
        )
        assertEquals(setOf(SeedIssueCode.DUPLICATE_LINE), codes(builder.build(sources(recipes = "[$doubled]"))))
    }

    @Test
    fun `ingredient problems are reported`() {
        val broken = """
            [{"id": "Bad Id", "name": {"sk": "X", "en": "X"}, "category": "OTHER", "source": "wikipedia",
              "pantryUnit": "PCS", "price": "MID"}]
        """.trimIndent()
        val result = builder.build(sources(ingredients = broken, recipes = "[]"))
        assertEquals(
            setOf(
                SeedIssueCode.INVALID_ID,
                SeedIssueCode.MISSING_NUTRITION,
                SeedIssueCode.INVALID_NUTRITION_SOURCE,
                SeedIssueCode.MISSING_PANTRY_CONVERSION,
            ),
            codes(result),
        )
    }

    @Test
    fun `invalid recipe fields are reported`() {
        val json = recipeJson(extra = "\"pairsWith\": [\"nothing\"],").replace("\"difficulty\": 1", "\"difficulty\": 4")
        assertEquals(setOf(SeedIssueCode.INVALID_RECIPE, SeedIssueCode.UNKNOWN_RECIPE), codes(builder.build(sources(recipes = "[$json]"))))
    }

    @Test
    fun `unknown json fields and enum values are parse errors`() {
        val json = recipeJson(extra = "\"spiciness\": 3,")
        assertEquals(setOf(SeedIssueCode.PARSE_ERROR), codes(builder.build(sources(recipes = "[$json]"))))
        val badEnum = recipeJson().replace("\"SLOVAK_CZECH\"", "\"KLINGON\"")
        assertEquals(setOf(SeedIssueCode.PARSE_ERROR), codes(builder.build(sources(recipes = "[$badEnum]"))))
    }

    @Test
    fun `substitutions are validated`() {
        val subs = """
            [{"from": "oil", "to": "egg", "ratio": 1.0, "note": {"sk": "Nie celkom.", "en": "Not quite."}},
             {"from": "oil", "to": "butter", "ratio": 1.0},
             {"from": "salt", "to": "salt", "ratio": 1.0}]
        """.trimIndent()
        val result = builder.build(sources(substitutions = subs))
        assertEquals(setOf(SeedIssueCode.UNKNOWN_INGREDIENT, SeedIssueCode.INVALID_SUBSTITUTION), codes(result))
        assertEquals(listOf("egg"), result.catalog.substitutions.map { it.substituteId })
    }

    @Test
    fun `fingerprint changes with content and ignores file order`() {
        val a = sources()
        val reordered = a.copy(recipes = a.recipes.reversed())
        assertEquals(a.fingerprint(), reordered.fingerprint())
        val changed = sources(recipes = "[${recipeJson(id = "other")}]")
        assertTrue(a.fingerprint() != changed.fingerprint())
    }
}
