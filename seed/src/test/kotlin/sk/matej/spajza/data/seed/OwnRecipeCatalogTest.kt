package sk.matej.spajza.data.seed

import sk.matej.spajza.domain.own.RecipeLineMatcher
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lines as people type them or recipe sites list them must find the right catalog ingredient. */
class OwnRecipeCatalogTest {
    private val catalog = CatalogBuilder().build(SeedDirectory.read(File(System.getProperty("spajza.seedDir")))).catalog
    private val matcher = RecipeLineMatcher(catalog)

    @Test
    fun `typical recipe lines match the catalog`() {
        val expected = mapOf(
            "1 balenie lístkového cesta" to "puff-pastry",
            "▢ 2 lyžice sójovej omáčky" to "soy-sauce",
            "1 lyžička rybacej omáčky" to "fish-sauce",
            "400 g cíceru" to "chickpeas",
            "100 g parmezánu, nastrúhaného" to "parmesan",
            "1 plechovka kokosového mlieka" to "coconut-milk",
            "2 PL medu" to "honey",
            "štipka muškátového orieška" to "nutmeg",
            "1 cuketa" to "zucchini",
            "kúsok zázvoru" to "ginger",
            "1 lyžica paradajkového pretlaku" to "tomato-paste",
            "na ozdobu petržlenová vňať (nepovinné)" to "parsley",
            "2 cups all-purpose flour" to "wheat-flour",
        )
        val found = expected.keys.associateWith { matcher.match(it).ingredientId }
        assertEquals(expected, found)
    }
}
