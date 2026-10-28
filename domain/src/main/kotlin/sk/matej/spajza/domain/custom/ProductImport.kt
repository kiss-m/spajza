package sk.matej.spajza.domain.custom

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.nutrition.Nutrition
import sk.matej.spajza.domain.search.IngredientLineParser
import sk.matej.spajza.domain.search.IngredientSearch

/** Label values per 100 g (or 100 ml); null when the label or the database does not say. */
data class Nutriments(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val salt: Double? = null,
)

/** What a product database (Open Food Facts) knows about a barcode, before any judgement. */
data class ProductFacts(
    val barcode: String,
    /** Product names, preferred language first. */
    val names: List<String>,
    val brand: String? = null,
    /** Net quantity with its unit as written ("g", "ml", "kg", "l", "ks"). */
    val quantity: Double? = null,
    val quantityUnit: String? = null,
    /** The quantity as printed, e.g. "6 x 100 g", used when [quantity] is missing. */
    val quantityText: String? = null,
    val nutriments: Nutriments = Nutriments(),
    /** Tags such as `en:milk`. */
    val allergenTags: Set<String> = emptySet(),
    /** Tags such as `en:non-vegan`, `en:vegetarian`. */
    val analysisTags: Set<String> = emptySet(),
    /** Category tags, most general first (`en:dairies`, `en:milks`, `en:semi-skimmed-milks`). */
    val categoryTags: List<String> = emptyList(),
)

/**
 * Turns [ProductFacts] into a [CustomIngredient] draft for the user to check: the energy is
 * recomputed from the macros, impossible values are dropped, and a matching catalog ingredient is
 * suggested so that recipes find the product.
 */
class ProductImport(catalog: Catalog) {
    private val search = IngredientSearch(catalog.ingredients)
    private val parser = IngredientLineParser(search)

    fun draft(facts: ProductFacts): CustomIngredient {
        val name = facts.names.map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: facts.barcode
        val (amount, unit) = packageSize(facts)
        val nutrition = nutritionOf(facts.nutriments)
        val allergens = facts.allergenTags.mapNotNullTo(linkedSetOf()) { ALLERGENS[it] }
        return CustomIngredient(
            id = CustomIngredient.idForBarcode(facts.barcode),
            name = name,
            brand = facts.brand?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            barcode = facts.barcode,
            category = categoryOf(facts.categoryTags),
            nutrition = nutrition ?: Nutrition.ZERO,
            nutritionKnown = nutrition != null,
            pantryUnit = unit,
            densityGPerMl = if (unit == PantryUnit.ML) CustomIngredient.LIQUID_DENSITY else null,
            packageQuantity = amount,
            allergens = allergens,
            dietFlags = dietFlagsOf(facts.analysisTags, allergens),
            catalogId = suggestCatalogId(name, facts.categoryTags),
            source = CustomSource.OPEN_FOOD_FACTS,
        )
    }

    /** The catalog ingredient the product most likely is, or null when nothing fits well. */
    fun suggestCatalogId(name: String, categoryTags: List<String> = emptyList()): String? {
        val fromName = parser.parse(name).takeIf { it.confidence >= MIN_LINK_CONFIDENCE }?.ingredient
        if (fromName != null) return fromName.id
        return categoryTags.asReversed().asSequence()
            .map { it.substringAfter(':').replace('-', ' ') }
            .mapNotNull { search.bestMatch(it) }
            .firstOrNull { it.score >= MIN_CATEGORY_SCORE }
            ?.ingredient?.id
    }

    private fun nutritionOf(n: Nutriments): Nutrition? {
        val protein = n.protein ?: return null
        val carbs = n.carbs ?: return null
        val fat = n.fat ?: return null
        val values = listOf(protein, carbs, fat, n.fiber ?: 0.0, n.sugar ?: 0.0, n.salt ?: 0.0)
        if (values.any { it < 0 } || protein + carbs + fat > MAX_MACROS_G) return null
        val fiber = n.fiber ?: 0.0
        return Energy.recomputed(
            Nutrition(
                kcal = 0.0,
                protein = protein,
                carbs = carbs,
                fat = fat,
                fiber = fiber,
                sugar = (n.sugar ?: 0.0).coerceAtMost(carbs),
                salt = n.salt ?: 0.0,
            ),
        )
    }

    private fun packageSize(facts: ProductFacts): Pair<Double?, PantryUnit> {
        val parsed = facts.quantity?.let { amount -> facts.quantityUnit?.let { unitAmount(amount, it) } }
            ?: facts.quantityText?.let { parseQuantityText(it) }
        return parsed ?: (null to PantryUnit.G)
    }

    private fun parseQuantityText(text: String): Pair<Double?, PantryUnit>? {
        val normalized = text.lowercase().replace(',', '.')
        val multi = MULTIPACK.find(normalized)
        if (multi != null) {
            val (count, amount, unit) = multi.destructured
            return unitAmount(count.toDouble() * amount.toDouble(), unit)
        }
        val single = SINGLE.find(normalized) ?: return null
        val (amount, unit) = single.destructured
        return unitAmount(amount.toDouble(), unit)
    }

    private fun unitAmount(amount: Double, unit: String): Pair<Double, PantryUnit>? = when (unit.trim().lowercase()) {
        "g", "gr" -> amount to PantryUnit.G
        "kg" -> amount * 1000 to PantryUnit.G
        "mg" -> amount / 1000 to PantryUnit.G
        "ml" -> amount to PantryUnit.ML
        "cl" -> amount * 10 to PantryUnit.ML
        "dl" -> amount * 100 to PantryUnit.ML
        "l" -> amount * 1000 to PantryUnit.ML
        "ks", "pcs", "pc", "kusov", "kusy" -> amount to PantryUnit.PCS
        else -> null
    }?.takeIf { (value, _) -> value > 0 }

    private fun categoryOf(tags: List<String>): IngredientCategory =
        tags.asReversed().firstNotNullOfOrNull { tag -> CATEGORIES.entries.firstOrNull { (key, _) -> tag.contains(key) }?.value }
            ?: IngredientCategory.OTHER

    private fun dietFlagsOf(analysis: Set<String>, allergens: Set<Allergen>): Set<DietFlag> {
        val flags = linkedSetOf<DietFlag>()
        if ("en:non-vegetarian" in analysis) flags += setOf(DietFlag.MEAT, DietFlag.ANIMAL_PRODUCT)
        if ("en:non-vegan" in analysis) flags += DietFlag.ANIMAL_PRODUCT
        if (Allergen.MILK in allergens) flags += setOf(DietFlag.DAIRY, DietFlag.ANIMAL_PRODUCT)
        if (Allergen.EGGS in allergens) flags += setOf(DietFlag.EGG, DietFlag.ANIMAL_PRODUCT)
        if (Allergen.GLUTEN in allergens) flags += DietFlag.GLUTEN
        if (allergens.any { it == Allergen.FISH || it == Allergen.CRUSTACEANS || it == Allergen.MOLLUSCS }) {
            flags += setOf(DietFlag.FISH, DietFlag.ANIMAL_PRODUCT)
        }
        return flags
    }

    private companion object {
        /** Linking a product to a catalog ingredient needs a fairly sure match; the user can change it. */
        const val MIN_LINK_CONFIDENCE = 0.6
        const val MIN_CATEGORY_SCORE = 0.85
        const val MAX_MACROS_G = 101.0

        val MULTIPACK = Regex("""(\d+)\s*[x×*]\s*(\d+(?:\.\d+)?)\s*(kg|g|mg|ml|cl|dl|l)\b""")
        val SINGLE = Regex("""(\d+(?:\.\d+)?)\s*(kg|g|mg|ml|cl|dl|l|ks|pcs)\b""")

        val ALLERGENS = mapOf(
            "en:gluten" to Allergen.GLUTEN,
            "en:crustaceans" to Allergen.CRUSTACEANS,
            "en:eggs" to Allergen.EGGS,
            "en:fish" to Allergen.FISH,
            "en:peanuts" to Allergen.PEANUTS,
            "en:soybeans" to Allergen.SOY,
            "en:milk" to Allergen.MILK,
            "en:nuts" to Allergen.TREE_NUTS,
            "en:celery" to Allergen.CELERY,
            "en:mustard" to Allergen.MUSTARD,
            "en:sesame-seeds" to Allergen.SESAME,
            "en:sulphur-dioxide-and-sulphites" to Allergen.SULPHITES,
            "en:lupin" to Allergen.LUPIN,
            "en:molluscs" to Allergen.MOLLUSCS,
        )

        /** Category tag fragments, checked from the most specific tag; the first fragment that fits wins. */
        val CATEGORIES = linkedMapOf(
            "eggs" to IngredientCategory.EGGS,
            "cheese" to IngredientCategory.DAIRY,
            "yogurt" to IngredientCategory.DAIRY,
            "milk" to IngredientCategory.DAIRY,
            "dairies" to IngredientCategory.DAIRY,
            "fish" to IngredientCategory.FISH,
            "seafood" to IngredientCategory.FISH,
            "meat" to IngredientCategory.MEAT,
            "sausages" to IngredientCategory.MEAT,
            "frozen" to IngredientCategory.FROZEN,
            "canned" to IngredientCategory.CANNED,
            "breads" to IngredientCategory.BAKERY,
            "pastries" to IngredientCategory.BAKERY,
            "pastas" to IngredientCategory.GRAINS,
            "rices" to IngredientCategory.GRAINS,
            "cereals" to IngredientCategory.GRAINS,
            "flours" to IngredientCategory.BAKING,
            "sugars" to IngredientCategory.BAKING,
            "legumes" to IngredientCategory.LEGUMES,
            "nuts" to IngredientCategory.NUTS_SEEDS,
            "seeds" to IngredientCategory.NUTS_SEEDS,
            "oils" to IngredientCategory.OILS_FATS,
            "fats" to IngredientCategory.OILS_FATS,
            "spices" to IngredientCategory.SPICES,
            "condiments" to IngredientCategory.SAUCES,
            "sauces" to IngredientCategory.SAUCES,
            "fruits" to IngredientCategory.FRUIT,
            "vegetables" to IngredientCategory.VEGETABLES,
        )
    }
}
