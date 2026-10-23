package sk.matej.spajza.data.seed

import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.domain.model.NutritionSource
import sk.matej.spajza.domain.nutrition.NutritionCalculator
import sk.matej.spajza.domain.text.TextNormalizer
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Validates the real seed bundled into the app (app/src/main/assets/seed). */
class SeedContentTest {
    private val result = CatalogBuilder().build(SeedDirectory.read(File(System.getProperty("spajza.seedDir"))))
    private val catalog = result.catalog
    private val calculator = NutritionCalculator(catalog.ingredientsById::get)

    @Test
    fun `seed builds without issues`() {
        assertNoProblems(result.issues.map { it.toString() })
    }

    @Test
    fun `catalog is not empty`() {
        assertTrue(catalog.recipes.isNotEmpty() && catalog.ingredients.isNotEmpty())
    }

    @Test
    fun `every recipe has energy per serving`() {
        assertNoProblems(
            catalog.recipes.filter { calculator.perServing(it).kcal <= 0 }.map { "${it.id}: kcal per serving is 0" },
        )
    }

    @Test
    fun `macros per serving are within sanity bounds`() {
        assertNoProblems(
            catalog.recipes.mapNotNull { recipe ->
                val n = calculator.perServing(recipe)
                val problems = listOfNotNull(
                    "kcal ${n.kcal.toInt()} outside 40..1800".takeIf { n.kcal !in 40.0..1800.0 },
                    "protein ${n.protein.toInt()} g > 150".takeIf { n.protein > 150 },
                    "fat ${n.fat.toInt()} g > 120".takeIf { n.fat > 120 },
                    "carbs ${n.carbs.toInt()} g > 250".takeIf { n.carbs > 250 },
                    "salt %.1f g > 7".format(n.salt).takeIf { n.salt > 7 },
                )
                problems.takeIf { it.isNotEmpty() }?.let { "${recipe.id}: ${it.joinToString()}" }
            },
        )
    }

    @Test
    fun `ingredient nutrition per 100 g is plausible`() {
        assertNoProblems(
            catalog.ingredients.mapNotNull { ingredient ->
                val n = ingredient.nutrition
                // USDA carbohydrate is "by difference" and includes fiber.
                val mass = n.protein + n.carbs + n.fat
                val atwater = 4 * n.protein + 4 * (n.carbs - n.fiber) + 2 * n.fiber + 9 * n.fat
                val problems = listOfNotNull(
                    "macros add up to ${mass.toInt()} g".takeIf { mass > 101 },
                    "kcal ${n.kcal} > 910".takeIf { n.kcal > 910 },
                    "sugar exceeds carbs".takeIf { n.sugar > n.carbs + 0.5 },
                    "fiber exceeds carbs".takeIf { n.fiber > n.carbs + 0.5 },
                    "salt > 100 g".takeIf { n.salt > 100 },
                    // Hand-entered estimates are checked against Atwater factors to catch typos;
                    // USDA values use food-specific energy factors and are taken as published.
                    "kcal ${n.kcal} vs Atwater ${atwater.toInt()}".takeIf {
                        ingredient.nutritionSource == NutritionSource.Estimate &&
                            ingredient.id !in ALCOHOLIC &&
                            abs(atwater - n.kcal) > max(20.0, 0.2 * n.kcal)
                    },
                )
                problems.takeIf { it.isNotEmpty() }?.let { "${ingredient.id}: ${it.joinToString()}" }
            },
        )
    }

    @Test
    fun `diet flags are consistent`() {
        assertNoProblems(
            catalog.ingredients.mapNotNull { ingredient ->
                val flags = ingredient.dietFlags
                val animal = setOf(DietFlag.MEAT, DietFlag.FISH, DietFlag.DAIRY, DietFlag.EGG, DietFlag.PORK)
                val allergens = ingredient.allergens
                when {
                    flags.any { it in animal } && DietFlag.ANIMAL_PRODUCT !in flags -> "${ingredient.id}: animal flag without ANIMAL_PRODUCT"
                    DietFlag.PORK in flags && DietFlag.MEAT !in flags -> "${ingredient.id}: PORK without MEAT"
                    (Allergen.GLUTEN in allergens) != (DietFlag.GLUTEN in flags) -> "${ingredient.id}: GLUTEN allergen and flag disagree"
                    Allergen.MILK in allergens && DietFlag.DAIRY !in flags -> "${ingredient.id}: MILK allergen without DAIRY"
                    Allergen.EGGS in allergens && DietFlag.EGG !in flags -> "${ingredient.id}: EGGS allergen without EGG"
                    allergens.any { it in SEAFOOD_ALLERGENS } && DietFlag.FISH !in flags -> "${ingredient.id}: seafood allergen without FISH"
                    else -> null
                }
            },
        )
    }

    @Test
    fun `no two recipes have near-duplicate titles`() {
        val problems = mutableListOf<String>()
        Language.entries.forEach { language ->
            val titles = catalog.recipes.map { it.id to TextNormalizer.normalize(it.title[language]) }
            for (i in titles.indices) {
                for (j in i + 1 until titles.size) {
                    val (idA, a) = titles[i]
                    val (idB, b) = titles[j]
                    if (similarity(a, b) >= 0.85) problems += "$idA ~ $idB ($language: '$a' / '$b')"
                }
            }
        }
        assertNoProblems(problems)
    }

    @Test
    fun `names and synonyms point to a single ingredient`() {
        val owners = mutableMapOf<String, MutableSet<String>>()
        catalog.ingredients.forEach { ingredient ->
            (ingredient.synonyms + ingredient.name.sk + ingredient.name.en).forEach {
                owners.getOrPut(TextNormalizer.normalize(it)) { mutableSetOf() } += ingredient.id
            }
        }
        assertNoProblems(owners.filterValues { it.size > 1 }.map { (term, ids) -> "'$term' is used by $ids" })
    }

    @Test
    fun `there are default staples`() {
        assertTrue(catalog.ingredients.count { it.isDefaultStaple } >= 5, "expected at least 5 default staples")
    }

    private fun assertNoProblems(problems: List<String>) {
        if (problems.isNotEmpty()) {
            fail("${problems.size} problem(s):\n" + problems.take(100).joinToString("\n") { "  - $it" })
        }
    }

    /** 1 − normalized Levenshtein distance. */
    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return 1.0 - previous[b.length].toDouble() / max(a.length, b.length)
    }

    private companion object {
        /** Alcohol adds 7 kcal/g, which the Atwater check above does not model. */
        val ALCOHOLIC = setOf("white-wine", "red-wine", "beer", "rum")

        val SEAFOOD_ALLERGENS = setOf(Allergen.FISH, Allergen.CRUSTACEANS, Allergen.MOLLUSCS)
    }
}
