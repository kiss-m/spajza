package sk.matej.spajza.domain.search

import sk.matej.spajza.domain.unit.MeasureUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IngredientLineParserTest {
    private val parser = IngredientLineParser(IngredientSearch(SearchFixture.ingredients))

    private fun check(line: String, amount: Double?, unit: MeasureUnit?, ingredientId: String?) {
        val parsed = parser.parse(line)
        assertEquals(amount, parsed.amount, "amount of '$line'")
        assertEquals(unit, parsed.unit, "unit of '$line'")
        assertEquals(ingredientId, parsed.ingredient?.id, "ingredient of '$line'")
    }

    @Test
    fun `amount unit and inflected ingredient`() {
        check("200 g kuracích pŕs", 200.0, MeasureUnit.G, "chicken-breast")
        check("1,5 kg zemiakov", 1.5, MeasureUnit.KG, "potato")
        check("500ml mlieka", 500.0, MeasureUnit.ML, "milk")
    }

    @Test
    fun `deciliters become milliliters`() {
        check("2 dl mlieka", 200.0, MeasureUnit.ML, "milk")
    }

    @Test
    fun `spoons cups and pinches in Slovak, Czech and English`() {
        check("1/2 lyžičky škorice", 0.5, MeasureUnit.TSP, "cinnamon")
        check("3 lyžice hladkej múky", 3.0, MeasureUnit.TBSP, "flour")
        check("2 lžíce mouky", 2.0, MeasureUnit.TBSP, "flour")
        check("1 cup flour", 1.0, MeasureUnit.CUP, "flour")
        check("štipka soli", 1.0, MeasureUnit.PINCH, "salt")
    }

    @Test
    fun `pieces, cloves and words as numbers`() {
        check("3 ks vajec", 3.0, MeasureUnit.PCS, "egg")
        check("2 strúčiky cesnaku", 2.0, MeasureUnit.PCS, "garlic")
        check("pol cibule", 0.5, MeasureUnit.PCS, "onion")
        check("2-3 mrkvy", 2.5, MeasureUnit.PCS, "carrot")
        check("½ cibule", 0.5, MeasureUnit.PCS, "onion")
    }

    @Test
    fun `descriptive words around the ingredient are ignored`() {
        check("1 nadrobno nakrájaná cibuľa", 1.0, MeasureUnit.PCS, "onion")
        check("1 konzerva krájaných paradajok", 1.0, MeasureUnit.PCS, "canned-tomatoes")
    }

    @Test
    fun `to taste lines`() {
        check("soľ podľa chuti", null, MeasureUnit.TO_TASTE, "salt")
        check("salt to taste", null, MeasureUnit.TO_TASTE, "salt")
    }

    @Test
    fun `confidence reflects how much was understood`() {
        val exact = parser.parse("200 g kuracích pŕs").confidence
        val noAmount = parser.parse("kuracie prsia").confidence
        val unknown = parser.parse("200 g dračieho ovocia").confidence
        assertTrue(exact > 0.9, "exact was $exact")
        assertTrue(noAmount in 0.5..exact, "no amount was $noAmount")
        assertTrue(unknown < 0.3, "unknown was $unknown")
        assertNull(parser.parse("200 g dračieho ovocia").ingredient)
    }
}
