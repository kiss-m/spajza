package sk.matej.spajza.data.off

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.model.Language
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenFoodFactsParserTest {
    // Shaped like an Open Food Facts v2 answer; the values are made up.
    private val body = """
        {
          "code": "8580000000012",
          "status": 1,
          "status_verbose": "product found",
          "product": {
            "product_name": "Mlieko polotučné",
            "product_name_en": "Semi-skimmed milk",
            "product_name_sk": "",
            "brands": "Mliekareň, Iná značka",
            "quantity": "1 l",
            "product_quantity": "1000",
            "product_quantity_unit": "ml",
            "allergens_tags": ["en:milk"],
            "ingredients_analysis_tags": ["en:palm-oil-free", "en:non-vegan", "en:vegetarian"],
            "categories_hierarchy": ["en:dairies", "en:milks", "en:semi-skimmed-milks"],
            "nutriments": {
              "energy-kcal_100g": 46,
              "proteins_100g": "3,4",
              "carbohydrates_100g": 4.8,
              "fat_100g": 1.5,
              "sugars_100g": 4.8,
              "salt_100g": 0.1
            }
          }
        }
    """.trimIndent()

    @Test
    fun `reads names, quantity, tags and per-100 g values`() {
        val facts = assertNotNull(OpenFoodFactsParser.parse("8580000000012", body, Language.SK))
        assertEquals(listOf("Mlieko polotučné", "Semi-skimmed milk"), facts.names, "empty Slovak name skipped")
        assertEquals("Mliekareň, Iná značka", facts.brand)
        assertEquals(1000.0, facts.quantity)
        assertEquals("ml", facts.quantityUnit)
        assertEquals(3.4, facts.nutriments.protein, "comma decimals in strings")
        assertEquals(4.8, facts.nutriments.carbs)
        assertNull(facts.nutriments.fiber)
        assertEquals(setOf("en:milk"), facts.allergenTags)
        assertEquals(listOf("en:dairies", "en:milks", "en:semi-skimmed-milks"), facts.categoryTags)
    }

    @Test
    fun `english names come first for english users`() {
        val facts = assertNotNull(OpenFoodFactsParser.parse("8580000000012", body, Language.EN))
        assertEquals("Semi-skimmed milk", facts.names.first())
    }

    @Test
    fun `unknown products and broken answers are null`() {
        assertNull(OpenFoodFactsParser.parse("1", """{"status": 0, "status_verbose": "product not found"}""", Language.SK))
        assertNull(OpenFoodFactsParser.parse("1", "<html>", Language.SK))
        assertNull(OpenFoodFactsParser.parse("1", """{"status": 1, "product": {"brands": "X"}}""", Language.SK), "no name")
    }
}
