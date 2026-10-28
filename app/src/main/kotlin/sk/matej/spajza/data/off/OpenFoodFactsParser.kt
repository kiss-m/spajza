package sk.matej.spajza.data.off

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import sk.matej.spajza.domain.custom.Nutriments
import sk.matej.spajza.domain.custom.ProductFacts
import sk.matej.spajza.domain.model.Language

/** Reads the Open Food Facts product API (v2) answer; anything unexpected is simply missing. */
object OpenFoodFactsParser {
    /** The fields the app asks for, to keep answers small. */
    val FIELDS = listOf(
        "code", "product_name", "product_name_sk", "product_name_cs", "product_name_en", "generic_name",
        "generic_name_sk", "brands", "quantity", "product_quantity", "product_quantity_unit", "nutriments",
        "allergens_tags", "ingredients_analysis_tags", "categories_hierarchy",
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(barcode: String, body: String, language: Language): ProductFacts? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        if ((root["status"] as? JsonPrimitive)?.intOrNull == 0) return null
        val product = root["product"] as? JsonObject ?: return null
        val local = listOf("product_name_sk", "product_name_cs", "generic_name_sk", "product_name", "generic_name")
        val english = listOf("product_name_en")
        val nameFields = if (language == Language.EN) english + local else local + english
        val names = nameFields.mapNotNull { product.string(it) }.distinct()
        if (names.isEmpty()) return null
        val nutriments = product["nutriments"] as? JsonObject
        return ProductFacts(
            barcode = barcode,
            names = names,
            brand = product.string("brands"),
            quantity = product.number("product_quantity"),
            quantityUnit = product.string("product_quantity_unit") ?: product.number("product_quantity")?.let { "g" },
            quantityText = product.string("quantity"),
            nutriments = Nutriments(
                kcal = nutriments?.number("energy-kcal_100g"),
                protein = nutriments?.number("proteins_100g"),
                carbs = nutriments?.number("carbohydrates_100g"),
                fat = nutriments?.number("fat_100g"),
                fiber = nutriments?.number("fiber_100g"),
                sugar = nutriments?.number("sugars_100g"),
                salt = nutriments?.number("salt_100g"),
            ),
            allergenTags = product.strings("allergens_tags").toSet(),
            analysisTags = product.strings("ingredients_analysis_tags").toSet(),
            categoryTags = product.strings("categories_hierarchy"),
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** Numbers come as numbers or as strings, with a dot or a comma. */
    private fun JsonObject.number(key: String): Double? {
        val value = this[key] as? JsonPrimitive ?: return null
        return value.doubleOrNull ?: value.contentOrNull?.replace(',', '.')?.trim()?.toDoubleOrNull()
    }

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
