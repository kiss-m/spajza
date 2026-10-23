package sk.matej.spajza.data.catalog

import org.junit.jupiter.api.Test
import sk.matej.spajza.data.seed.CatalogBuilder
import sk.matej.spajza.data.seed.SeedDirectory
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogMapperTest {
    private val catalog = CatalogBuilder().build(SeedDirectory.read(File(System.getProperty("spajza.seedDir")))).getOrThrow()

    @Test
    fun `bundled catalog survives the round trip through Room rows`() {
        val restored = CatalogMapper.toCatalog(CatalogMapper.toTables(catalog))

        assertEquals(catalog.ingredients, restored.ingredients)
        assertEquals(catalog.recipes, restored.recipes)
        assertEquals(catalog.substitutions, restored.substitutions)
    }

    @Test
    fun `rows have unique primary keys`() {
        val tables = CatalogMapper.toTables(catalog)

        assertUnique("ingredient", tables.ingredients.map { it.id })
        assertUnique("recipe", tables.recipes.map { it.id })
        assertUnique("recipe_ingredient", tables.recipeIngredients.map { it.recipeId to it.position })
        assertUnique("recipe_step", tables.recipeSteps.map { it.recipeId to it.stepIndex })
        assertUnique("substitution", tables.substitutions.map { it.ingredientId to it.substituteId })
    }

    @Test
    fun `separators never occur inside stored values`() {
        val tables = CatalogMapper.toTables(catalog)

        val synonymsWithNewline = catalog.ingredients.flatMap { it.synonyms }.filter { '\n' in it }
        assertTrue(synonymsWithNewline.isEmpty(), "Synonyms with a newline: $synonymsWithNewline")
        val pairsWithComma = catalog.recipes.flatMap { it.pairsWith }.filter { ',' in it }
        assertTrue(pairsWithComma.isEmpty(), "pairsWith ids with a comma: $pairsWithComma")
        assertTrue(tables.ingredients.isNotEmpty() && tables.recipes.isNotEmpty())
    }

    private fun assertUnique(table: String, keys: List<Any>) {
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(duplicates.isEmpty(), "Duplicate $table keys: $duplicates")
    }
}
