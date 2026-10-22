package sk.matej.spajza.data.seed

import sk.matej.spajza.domain.search.IngredientLineParser
import sk.matej.spajza.domain.search.IngredientSearch
import sk.matej.spajza.domain.unit.MeasureUnit
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/** Realistic recipe and receipt lines must resolve against the real catalog synonyms. */
class LineParserCatalogTest {
    private val catalog = CatalogBuilder().build(SeedDirectory.read(File(System.getProperty("spajza.seedDir")))).catalog
    private val parser = IngredientLineParser(IngredientSearch(catalog.ingredients))

    private val cases = listOf(
        Triple("200 g kuracích pŕs", "chicken-breast", MeasureUnit.G),
        Triple("2 dl mlieka", "milk", MeasureUnit.ML),
        Triple("1 nadrobno nakrájaná cibuľa", "onion", MeasureUnit.PCS),
        Triple("2 strúčiky cesnaku", "garlic", MeasureUnit.PCS),
        Triple("3 lyžice olivového oleja", "olive-oil", MeasureUnit.TBSP),
        Triple("1 lyžica oleja", "sunflower-oil", MeasureUnit.TBSP),
        Triple("500 g zemiakov", "potato", MeasureUnit.G),
        Triple("250 g bryndze", "bryndza", MeasureUnit.G),
        Triple("1 konzerva krájaných paradajok", "canned-tomatoes", MeasureUnit.PCS),
        Triple("štipka soli", "salt", MeasureUnit.PINCH),
        Triple("1 ČL mletej papriky", "sweet-paprika", MeasureUnit.TSP),
        Triple("100 g kyslej smotany", "sour-cream", MeasureUnit.G),
        Triple("1 kg hladkej múky", "wheat-flour", MeasureUnit.KG),
        Triple("2 vajcia", "egg", MeasureUnit.PCS),
        Triple("200 g údenej slaniny", "bacon", MeasureUnit.G),
        Triple("300 g mletého hovädzieho mäsa", "ground-beef", MeasureUnit.G),
        Triple("1 kocka bujónu", "stock-vegetable", MeasureUnit.PCS),
        Triple("soľ podľa chuti", "salt", MeasureUnit.TO_TASTE),
        Triple("400 g kuřecích prsou", "chicken-breast", MeasureUnit.G),
        Triple("2 lžíce zakysané smetany", "sour-cream", MeasureUnit.TBSP),
        Triple("1 cup rice", "rice", MeasureUnit.CUP),
        Triple("2 tbsp soy sauce", "soy-sauce", MeasureUnit.TBSP),
        Triple("3 cloves garlic", "garlic", MeasureUnit.PCS),
        Triple("200 g penne", "pasta", MeasureUnit.G),
    )

    @Test
    fun `realistic lines resolve to the right ingredient and unit`() {
        val problems = cases.mapNotNull { (line, ingredient, unit) ->
            val parsed = parser.parse(line)
            if (parsed.ingredient?.id == ingredient && parsed.unit == unit && parsed.confidence >= 0.6) {
                null
            } else {
                "'$line' -> ${parsed.ingredient?.id} ${parsed.unit} (confidence %.2f)".format(parsed.confidence)
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
