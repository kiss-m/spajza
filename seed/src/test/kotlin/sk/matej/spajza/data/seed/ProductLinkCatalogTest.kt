package sk.matej.spajza.data.seed

import sk.matej.spajza.domain.custom.ProductImport
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/** Shop product names (as Open Food Facts has them) must link to the right catalog ingredient. */
class ProductLinkCatalogTest {
    private val catalog = CatalogBuilder().build(SeedDirectory.read(File(System.getProperty("spajza.seedDir")))).catalog
    private val import = ProductImport(catalog)

    private val cases = listOf(
        "Rajo Mlieko polotučné 1,5 %" to "milk",
        "Polotučné mlieko trvanlivé" to "milk",
        "Penne rigate" to "pasta",
        "Spaghetti n. 5" to "spaghetti",
        "Čerstvé vajcia M" to "egg",
        "Krájané paradajky" to "canned-tomatoes",
        "Maslo 82 %" to "butter",
        "Biely jogurt" to "yogurt",
        "Horalky" to null,
    )

    @Test
    fun `product names link to catalog ingredients`() {
        val problems = cases.mapNotNull { (name, expected) ->
            val actual = import.suggestCatalogId(name)
            "'$name' → $actual, expected $expected".takeIf { actual != expected }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
