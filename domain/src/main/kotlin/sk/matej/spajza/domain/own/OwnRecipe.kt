package sk.matej.spajza.domain.own

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.catalog.CatalogTagger
import sk.matej.spajza.domain.model.ComputedTag
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.model.RecipeIngredient
import sk.matej.spajza.domain.model.RecipeStep
import sk.matej.spajza.domain.model.RecipeTag
import sk.matej.spajza.domain.unit.MeasureUnit
import sk.matej.spajza.domain.unit.MissingConversionException
import sk.matej.spajza.domain.unit.UnitConverter
import java.util.UUID

/** How an own recipe came into the app. */
enum class RecipeSource { MANUAL, URL, PHOTO }

/** One ingredient line of an own recipe: the text as written and what it was matched to. */
data class OwnIngredientLine(
    val text: String,
    /** The catalog ingredient; null when nothing matched, so the line is shown but not counted. */
    val ingredientId: String? = null,
    val amount: Double? = null,
    val unit: MeasureUnit? = null,
    val optional: Boolean = false,
)

data class OwnStep(val text: String, val timerSeconds: Int? = null)

/**
 * A household's own recipe, typed in or imported (`households/{hid}/recipes/{id}`, D12). Text is in
 * one language, as written. It joins the catalog as a [Recipe] with the id `own:<id>`.
 */
data class OwnRecipe(
    val id: String,
    val title: String,
    val description: String = "",
    val servings: Int = DEFAULT_SERVINGS,
    val totalTimeMin: Int = DEFAULT_MINUTES,
    val activeTimeMin: Int? = null,
    /** 1 = easy, 3 = demanding. */
    val difficulty: Int = 1,
    val cuisine: Cuisine = Cuisine.INTERNATIONAL,
    val mealTypes: Set<MealType> = DEFAULT_MEAL_TYPES,
    val equipment: Set<Equipment> = emptySet(),
    val tags: Set<RecipeTag> = emptySet(),
    val ingredients: List<OwnIngredientLine> = emptyList(),
    val steps: List<OwnStep> = emptyList(),
    val source: RecipeSource = RecipeSource.MANUAL,
    /** The page an imported recipe came from. */
    val sourceUrl: String? = null,
) {
    val recipeId: String get() = OwnRecipes.recipeId(id)

    companion object {
        const val DEFAULT_SERVINGS = 2
        const val DEFAULT_MINUTES = 30
        val DEFAULT_MEAL_TYPES = setOf(MealType.LUNCH, MealType.DINNER)

        fun newId(): String = UUID.randomUUID().toString().replace("-", "")
    }
}

/** Own recipes as catalog recipes, so matching, search, detail and cooking treat them alike. */
object OwnRecipes {
    const val ID_PREFIX = "own:"

    fun recipeId(ownId: String): String = "$ID_PREFIX$ownId"

    fun isOwn(recipeId: String): Boolean = recipeId.startsWith(ID_PREFIX)

    /** The Firestore id behind a catalog recipe id, or null for a bundled recipe. */
    fun ownId(recipeId: String): String? = if (isOwn(recipeId)) recipeId.removePrefix(ID_PREFIX) else null

    /** The bundled catalog with [own] recipes added; without any it is returned unchanged. */
    fun merge(catalog: Catalog, own: List<OwnRecipe>): Catalog =
        if (own.isEmpty()) catalog else catalog.copy(recipes = catalog.recipes + own.map { toRecipe(it, catalog) })

    fun toRecipe(own: OwnRecipe, catalog: Catalog): Recipe {
        val recipe = Recipe(
            id = own.recipeId,
            title = LocalizedText.same(own.title),
            description = LocalizedText.same(own.description),
            cuisine = own.cuisine,
            mealTypes = own.mealTypes,
            totalTimeMin = own.totalTimeMin,
            activeTimeMin = own.activeTimeMin ?: own.totalTimeMin,
            difficulty = own.difficulty,
            servings = own.servings.coerceAtLeast(1),
            equipment = own.equipment,
            tags = own.tags,
            computedTags = emptySet(),
            pairsWith = emptyList(),
            ingredients = own.ingredients.mapNotNull { line -> ingredientOf(line, catalog)?.let { weigh(line, it) } },
            steps = own.steps.mapIndexed { index, step -> RecipeStep(index, LocalizedText.same(step.text), step.timerSeconds) },
        )
        val tags = CatalogTagger(catalog.ingredientsById::get).computeTags(recipe)
        // A line the app cannot see might be meat or sugar, so only the time-based tag stays safe.
        val computed = if (uncounted(own, catalog).isEmpty()) tags else tags intersect setOf(ComputedTag.QUICK)
        return recipe.copy(computedTags = computed)
    }

    /** Lines that match no catalog ingredient: shown as written, left out of matching and nutrition. */
    fun unmatched(own: OwnRecipe, catalog: Catalog): List<OwnIngredientLine> = own.ingredients.filter { ingredientOf(it, catalog) == null }

    /** Lines missing from the nutrition: unmatched ones and amounts that cannot be weighed. */
    fun uncounted(own: OwnRecipe, catalog: Catalog): List<OwnIngredientLine> = own.ingredients.filter { line ->
        val ingredient = ingredientOf(line, catalog) ?: return@filter true
        val amount = line.amount
        val unit = unitOf(line, ingredient)
        amount != null && line.unit != MeasureUnit.TO_TASTE && (unit == MeasureUnit.TO_TASTE || grams(amount, unit, ingredient) == null)
    }

    private fun ingredientOf(line: OwnIngredientLine, catalog: Catalog): Ingredient? = line.ingredientId?.let { catalog.ingredientsById[it] }

    private fun weigh(line: OwnIngredientLine, ingredient: Ingredient): RecipeIngredient {
        val unit = unitOf(line, ingredient)
        val amount = if (unit == MeasureUnit.TO_TASTE) 0.0 else line.amount ?: 0.0
        return RecipeIngredient(
            ingredientId = ingredient.id,
            amount = amount,
            unit = unit,
            // Unweighable amounts still show as written; they count as needed but add nothing.
            grams = grams(amount, unit, ingredient) ?: 0.0,
            role = roleOf(ingredient, unit),
            optional = line.optional,
        )
    }

    /** No amount means "to taste"; a bare number counts pieces when the ingredient has a piece weight. */
    private fun unitOf(line: OwnIngredientLine, ingredient: Ingredient): MeasureUnit = when {
        line.amount == null -> MeasureUnit.TO_TASTE
        line.unit != null -> line.unit
        ingredient.gramsPerPiece != null -> MeasureUnit.PCS
        else -> MeasureUnit.TO_TASTE
    }

    private fun grams(amount: Double, unit: MeasureUnit, ingredient: Ingredient): Double? = try {
        UnitConverter.toGrams(amount, unit, ingredient)
    } catch (e: MissingConversionException) {
        null
    }

    private fun roleOf(ingredient: Ingredient, unit: MeasureUnit): IngredientRole = when {
        unit == MeasureUnit.TO_TASTE || unit == MeasureUnit.PINCH || ingredient.category == IngredientCategory.SPICES -> IngredientRole.SEASONING
        ingredient.category in SECONDARY_CATEGORIES -> IngredientRole.SECONDARY
        else -> IngredientRole.MAIN
    }

    private val SECONDARY_CATEGORIES = setOf(IngredientCategory.OILS_FATS, IngredientCategory.SAUCES, IngredientCategory.BAKING)
}
