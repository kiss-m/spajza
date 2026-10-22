package sk.matej.spajza.data.seed

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.catalog.CatalogTagger
import sk.matej.spajza.domain.catalog.TagThresholds
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.NutritionSource
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.model.RecipeIngredient
import sk.matej.spajza.domain.model.RecipeStep
import sk.matej.spajza.domain.model.Substitution
import sk.matej.spajza.domain.nutrition.Nutrition
import sk.matej.spajza.domain.unit.MeasureUnit
import sk.matej.spajza.domain.unit.MissingConversionException
import sk.matej.spajza.domain.unit.UnitConverter

/**
 * Turns the seed files into a normalized [Catalog]: parses JSON, validates references and
 * translations, converts every amount to grams and derives computed tags. It never stops at the
 * first problem; every issue is reported so the content can be fixed in one pass.
 */
class CatalogBuilder(private val thresholds: TagThresholds = TagThresholds()) {

    private val json = Json { ignoreUnknownKeys = false }

    fun build(sources: SeedSources): CatalogBuildResult {
        val issues = mutableListOf<SeedIssue>()
        val ingredients = parse(sources.ingredients, IngredientDto.serializer(), issues)
            .mapNotNull { (file, dto) -> buildIngredient(file, dto, issues) }
            .let { dedupe(it, issues) { ingredient -> ingredient.id } }
        val byId = ingredients.associateBy { it.id }

        val tagger = CatalogTagger(byId::get, thresholds)
        val recipes = parse(sources.recipes, RecipeDto.serializer(), issues)
            .mapNotNull { (file, dto) -> buildRecipe(file, dto, byId, issues) }
            .let { dedupe(it, issues) { recipe -> recipe.id } }
            .map { recipe -> recipe.copy(computedTags = tagger.computeTags(recipe)) }
        checkPairings(recipes, issues)

        val substitutions = parse(sources.substitutions, SubstitutionDto.serializer(), issues)
            .mapNotNull { (file, dto) -> buildSubstitution(file, dto, byId, issues) }
            .let { dedupe(it, issues) { sub -> "${sub.ingredientId}->${sub.substituteId}" } }

        return CatalogBuildResult(Catalog(ingredients, recipes, substitutions), issues)
    }

    private fun <T> parse(
        files: List<SeedFile>,
        serializer: KSerializer<T>,
        issues: MutableList<SeedIssue>,
    ): List<Pair<SeedFile, T>> = files.sortedBy { it.path }.flatMap { file ->
        try {
            json.decodeFromString(ListSerializer(serializer), file.content).map { file to it }
        } catch (e: SerializationException) {
            issues += SeedIssue(SeedIssueCode.PARSE_ERROR, file.path, e.message.orEmpty())
            emptyList()
        } catch (e: IllegalArgumentException) {
            issues += SeedIssue(SeedIssueCode.PARSE_ERROR, file.path, e.message.orEmpty())
            emptyList()
        }
    }

    private fun buildIngredient(file: SeedFile, dto: IngredientDto, issues: MutableList<SeedIssue>): Ingredient? {
        val at = "${file.path}#${dto.id}"
        fun issue(code: SeedIssueCode, message: String) {
            issues += SeedIssue(code, at, message)
        }
        if (!SLUG.matches(dto.id)) issue(SeedIssueCode.INVALID_ID, "id must be a lowercase slug")
        checkTranslated(dto.name, at, "name", issues)
        if (dto.synonyms.any { it.isBlank() }) issue(SeedIssueCode.MISSING_TRANSLATION, "blank synonym")

        val nutrition = dto.nutrition?.toDomain()
        when {
            nutrition == null -> issue(SeedIssueCode.MISSING_NUTRITION, "nutrition per 100 g is missing")
            nutrition.hasNegative() -> issue(SeedIssueCode.INVALID_NUTRITION, "negative nutrition value")
        }
        val source = dto.source?.let(NutritionSource::parse)
        if (source == null) issue(SeedIssueCode.INVALID_NUTRITION_SOURCE, "source must be 'USDA FDC <id>' or 'estimate', was ${dto.source}")

        if (dto.density != null && dto.density <= 0) issue(SeedIssueCode.INVALID_VALUE, "density must be positive")
        if (dto.gramsPerPiece != null && dto.gramsPerPiece <= 0) issue(SeedIssueCode.INVALID_VALUE, "gramsPerPiece must be positive")
        if (dto.pantryUnit == PantryUnit.ML && dto.density == null) {
            issue(SeedIssueCode.MISSING_PANTRY_CONVERSION, "pantry unit ML needs a density")
        }
        if (dto.pantryUnit == PantryUnit.PCS && dto.gramsPerPiece == null) {
            issue(SeedIssueCode.MISSING_PANTRY_CONVERSION, "pantry unit PCS needs gramsPerPiece")
        }
        if (dto.shelfLifeDays != null && dto.shelfLifeDays <= 0) issue(SeedIssueCode.INVALID_VALUE, "shelfLifeDays must be positive")

        return Ingredient(
            id = dto.id,
            name = dto.name.toDomain(),
            synonyms = dto.synonyms,
            category = dto.category,
            nutrition = nutrition ?: Nutrition.ZERO,
            nutritionSource = source ?: NutritionSource.Estimate,
            pantryUnit = dto.pantryUnit,
            gramsPerPiece = dto.gramsPerPiece,
            densityGPerMl = dto.density,
            allergens = dto.allergens,
            dietFlags = dto.flags,
            isDefaultStaple = dto.staple,
            typicalShelfLifeDays = dto.shelfLifeDays,
            priceTier = dto.price,
            spicy = dto.spicy,
        )
    }

    private fun buildRecipe(
        file: SeedFile,
        dto: RecipeDto,
        ingredients: Map<String, Ingredient>,
        issues: MutableList<SeedIssue>,
    ): Recipe? {
        val at = "${file.path}#${dto.id}"
        fun issue(code: SeedIssueCode, message: String) {
            issues += SeedIssue(code, at, message)
        }
        if (!SLUG.matches(dto.id)) issue(SeedIssueCode.INVALID_ID, "id must be a lowercase slug")
        checkTranslated(dto.title, at, "title", issues)
        checkTranslated(dto.description, at, "description", issues)
        if (dto.servings < 1) issue(SeedIssueCode.INVALID_RECIPE, "servings must be at least 1")
        if (dto.difficulty !in 1..3) issue(SeedIssueCode.INVALID_RECIPE, "difficulty must be 1..3")
        if (dto.totalTimeMin <= 0) issue(SeedIssueCode.INVALID_RECIPE, "totalTimeMin must be positive")
        if (dto.activeTimeMin !in 1..dto.totalTimeMin) issue(SeedIssueCode.INVALID_RECIPE, "activeTimeMin must be 1..totalTimeMin")
        if (dto.mealTypes.isEmpty()) issue(SeedIssueCode.INVALID_RECIPE, "at least one meal type is required")
        if (dto.ingredients.isEmpty()) issue(SeedIssueCode.INVALID_RECIPE, "no ingredients")
        if (dto.steps.isEmpty()) issue(SeedIssueCode.INVALID_RECIPE, "no steps")

        val seen = mutableSetOf<String>()
        val lines = dto.ingredients.mapNotNull { line ->
            if (!seen.add(line.id)) issue(SeedIssueCode.DUPLICATE_LINE, "'${line.id}' is listed twice; merge the amounts")
            line.note?.let { checkTranslated(it, at, "note of ${line.id}", issues) }
            if (line.unit != MeasureUnit.TO_TASTE && line.amount <= 0) {
                issue(SeedIssueCode.INVALID_RECIPE, "'${line.id}' needs a positive amount")
            }
            val ingredient = ingredients[line.id]
            if (ingredient == null) {
                issue(SeedIssueCode.UNKNOWN_INGREDIENT, "unknown ingredient '${line.id}'")
                return@mapNotNull null
            }
            val grams = try {
                UnitConverter.toGrams(line.amount, line.unit, ingredient)
            } catch (e: MissingConversionException) {
                issue(SeedIssueCode.MISSING_CONVERSION, e.message.orEmpty())
                return@mapNotNull null
            }
            RecipeIngredient(
                ingredientId = line.id,
                amount = line.amount,
                unit = line.unit,
                grams = grams,
                role = line.role,
                optional = line.optional,
                note = line.note?.toDomain(),
            )
        }
        val steps = dto.steps.mapIndexed { index, step ->
            checkTranslated(LocalizedDto(step.sk, step.en), at, "step ${index + 1}", issues)
            if (step.timerSeconds != null && step.timerSeconds <= 0) {
                issue(SeedIssueCode.INVALID_RECIPE, "step ${index + 1} timer must be positive")
            }
            RecipeStep(index = index, text = LocalizedText(step.sk, step.en), timerSeconds = step.timerSeconds)
        }
        if (dto.servings < 1) return null

        return Recipe(
            id = dto.id,
            title = dto.title.toDomain(),
            description = dto.description.toDomain(),
            cuisine = dto.cuisine,
            mealTypes = dto.mealTypes,
            totalTimeMin = dto.totalTimeMin,
            activeTimeMin = dto.activeTimeMin,
            difficulty = dto.difficulty,
            servings = dto.servings,
            equipment = dto.equipment,
            tags = dto.tags,
            computedTags = emptySet(),
            pairsWith = dto.pairsWith,
            ingredients = lines,
            steps = steps,
        )
    }

    private fun checkPairings(recipes: List<Recipe>, issues: MutableList<SeedIssue>) {
        val ids = recipes.mapTo(mutableSetOf()) { it.id }
        recipes.forEach { recipe ->
            recipe.pairsWith.forEach { other ->
                if (other !in ids || other == recipe.id) {
                    issues += SeedIssue(SeedIssueCode.UNKNOWN_RECIPE, "recipe#${recipe.id}", "pairsWith '$other' is not another recipe")
                }
            }
        }
    }

    private fun buildSubstitution(
        file: SeedFile,
        dto: SubstitutionDto,
        ingredients: Map<String, Ingredient>,
        issues: MutableList<SeedIssue>,
    ): Substitution? {
        val at = "${file.path}#${dto.from}->${dto.to}"
        var valid = true
        listOf(dto.from, dto.to).filter { it !in ingredients }.forEach {
            issues += SeedIssue(SeedIssueCode.UNKNOWN_INGREDIENT, at, "unknown ingredient '$it'")
            valid = false
        }
        if (dto.from == dto.to || dto.ratio <= 0) {
            issues += SeedIssue(SeedIssueCode.INVALID_SUBSTITUTION, at, "needs two different ingredients and a positive ratio")
            valid = false
        }
        dto.note?.let { checkTranslated(it, at, "note", issues) }
        return if (valid) Substitution(dto.from, dto.to, dto.ratio, dto.note?.toDomain()) else null
    }

    private fun <T> dedupe(items: List<T>, issues: MutableList<SeedIssue>, key: (T) -> String): List<T> {
        val seen = mutableSetOf<String>()
        return items.filter { item ->
            seen.add(key(item)).also { unique ->
                if (!unique) issues += SeedIssue(SeedIssueCode.DUPLICATE_ID, key(item), "duplicate id")
            }
        }
    }

    private fun checkTranslated(text: LocalizedDto, at: String, field: String, issues: MutableList<SeedIssue>) {
        if (text.sk.isBlank()) issues += SeedIssue(SeedIssueCode.MISSING_TRANSLATION, at, "$field has no Slovak text")
        if (text.en.isBlank()) issues += SeedIssue(SeedIssueCode.MISSING_TRANSLATION, at, "$field has no English text")
    }

    private fun LocalizedDto.toDomain() = LocalizedText(sk = sk.trim(), en = en.trim())

    private fun NutritionDto.toDomain() = Nutrition(kcal, protein, carbs, fat, fiber, sugar, salt)

    private fun Nutrition.hasNegative() = listOf(kcal, protein, carbs, fat, fiber, sugar, salt).any { it < 0 }

    private companion object {
        val SLUG = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    }
}
