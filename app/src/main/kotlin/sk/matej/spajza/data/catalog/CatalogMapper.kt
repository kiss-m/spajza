package sk.matej.spajza.data.catalog

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.ComputedTag
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.model.NutritionSource
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.model.RecipeIngredient
import sk.matej.spajza.domain.model.RecipeStep
import sk.matej.spajza.domain.model.RecipeTag
import sk.matej.spajza.domain.model.Substitution
import sk.matej.spajza.domain.nutrition.Nutrition
import sk.matej.spajza.domain.unit.MeasureUnit

data class CatalogTables(
    val ingredients: List<IngredientEntity>,
    val recipes: List<RecipeEntity>,
    val recipeIngredients: List<RecipeIngredientEntity>,
    val recipeSteps: List<RecipeStepEntity>,
    val substitutions: List<SubstitutionEntity>,
)

/** Converts the domain catalog to Room rows and back. */
object CatalogMapper {
    private const val LIST_SEPARATOR = "\n"
    private const val SET_SEPARATOR = ","

    fun toTables(catalog: Catalog) = CatalogTables(
        ingredients = catalog.ingredients.map { it.toEntity() },
        recipes = catalog.recipes.map { it.toEntity() },
        recipeIngredients = catalog.recipes.flatMap { recipe ->
            recipe.ingredients.mapIndexed { position, line -> line.toEntity(recipe.id, position) }
        },
        recipeSteps = catalog.recipes.flatMap { recipe -> recipe.steps.map { it.toEntity(recipe.id) } },
        substitutions = catalog.substitutions.map { it.toEntity() },
    )

    fun toCatalog(tables: CatalogTables): Catalog {
        val lines = tables.recipeIngredients.groupBy { it.recipeId }
        val steps = tables.recipeSteps.groupBy { it.recipeId }
        return Catalog(
            ingredients = tables.ingredients.map { it.toDomain() },
            recipes = tables.recipes.map { recipe ->
                recipe.toDomain(
                    lines = lines[recipe.id].orEmpty().sortedBy { it.position }.map { it.toDomain() },
                    steps = steps[recipe.id].orEmpty().sortedBy { it.stepIndex }.map { it.toDomain() },
                )
            },
            substitutions = tables.substitutions.map { it.toDomain() },
        )
    }

    private fun Ingredient.toEntity() = IngredientEntity(
        id = id,
        nameSk = name.sk,
        nameEn = name.en,
        synonyms = synonyms.joinToString(LIST_SEPARATOR),
        category = category.name,
        kcal = nutrition.kcal,
        protein = nutrition.protein,
        carbs = nutrition.carbs,
        fat = nutrition.fat,
        fiber = nutrition.fiber,
        sugar = nutrition.sugar,
        salt = nutrition.salt,
        nutritionSource = when (val source = nutritionSource) {
            is NutritionSource.Usda -> "USDA FDC ${source.fdcId}"
            NutritionSource.Estimate -> "estimate"
        },
        pantryUnit = pantryUnit.name,
        gramsPerPiece = gramsPerPiece,
        densityGPerMl = densityGPerMl,
        allergens = allergens.joinNames(),
        dietFlags = dietFlags.joinNames(),
        isDefaultStaple = isDefaultStaple,
        typicalShelfLifeDays = typicalShelfLifeDays,
        priceTier = priceTier.name,
        spicy = spicy,
    )

    private fun IngredientEntity.toDomain() = Ingredient(
        id = id,
        name = LocalizedText(nameSk, nameEn),
        synonyms = synonyms.split(LIST_SEPARATOR).filter { it.isNotEmpty() },
        category = IngredientCategory.valueOf(category),
        nutrition = Nutrition(kcal, protein, carbs, fat, fiber, sugar, salt),
        nutritionSource = NutritionSource.parse(nutritionSource) ?: NutritionSource.Estimate,
        pantryUnit = PantryUnit.valueOf(pantryUnit),
        gramsPerPiece = gramsPerPiece,
        densityGPerMl = densityGPerMl,
        allergens = allergens.parseNames(Allergen::valueOf),
        dietFlags = dietFlags.parseNames(DietFlag::valueOf),
        isDefaultStaple = isDefaultStaple,
        typicalShelfLifeDays = typicalShelfLifeDays,
        priceTier = PriceTier.valueOf(priceTier),
        spicy = spicy,
    )

    private fun Recipe.toEntity() = RecipeEntity(
        id = id,
        titleSk = title.sk,
        titleEn = title.en,
        descriptionSk = description.sk,
        descriptionEn = description.en,
        cuisine = cuisine.name,
        mealTypes = mealTypes.joinNames(),
        totalTimeMin = totalTimeMin,
        activeTimeMin = activeTimeMin,
        difficulty = difficulty,
        servings = servings,
        equipment = equipment.joinNames(),
        tags = tags.joinNames(),
        computedTags = computedTags.joinNames(),
        pairsWith = pairsWith.joinToString(SET_SEPARATOR),
    )

    private fun RecipeEntity.toDomain(lines: List<RecipeIngredient>, steps: List<RecipeStep>) = Recipe(
        id = id,
        title = LocalizedText(titleSk, titleEn),
        description = LocalizedText(descriptionSk, descriptionEn),
        cuisine = Cuisine.valueOf(cuisine),
        mealTypes = mealTypes.parseNames(MealType::valueOf),
        totalTimeMin = totalTimeMin,
        activeTimeMin = activeTimeMin,
        difficulty = difficulty,
        servings = servings,
        equipment = equipment.parseNames(Equipment::valueOf),
        tags = tags.parseNames(RecipeTag::valueOf),
        computedTags = computedTags.parseNames(ComputedTag::valueOf),
        pairsWith = pairsWith.split(SET_SEPARATOR).filter { it.isNotEmpty() },
        ingredients = lines,
        steps = steps,
    )

    private fun RecipeIngredient.toEntity(recipeId: String, position: Int) = RecipeIngredientEntity(
        recipeId = recipeId,
        position = position,
        ingredientId = ingredientId,
        amount = amount,
        unit = unit.name,
        grams = grams,
        role = role.name,
        optional = optional,
        noteSk = note?.sk,
        noteEn = note?.en,
    )

    private fun RecipeIngredientEntity.toDomain() = RecipeIngredient(
        ingredientId = ingredientId,
        amount = amount,
        unit = MeasureUnit.valueOf(unit),
        grams = grams,
        role = IngredientRole.valueOf(role),
        optional = optional,
        note = localizedOrNull(noteSk, noteEn),
    )

    private fun RecipeStep.toEntity(recipeId: String) = RecipeStepEntity(recipeId, index, text.sk, text.en, timerSeconds)

    private fun RecipeStepEntity.toDomain() = RecipeStep(stepIndex, LocalizedText(textSk, textEn), timerSeconds)

    private fun Substitution.toEntity() = SubstitutionEntity(ingredientId, substituteId, ratio, note?.sk, note?.en)

    private fun SubstitutionEntity.toDomain() = Substitution(ingredientId, substituteId, ratio, localizedOrNull(noteSk, noteEn))

    private fun localizedOrNull(sk: String?, en: String?) = if (sk != null && en != null) LocalizedText(sk, en) else null

    private fun Set<Enum<*>>.joinNames() = joinToString(SET_SEPARATOR) { it.name }

    private fun <T : Enum<T>> String.parseNames(parse: (String) -> T): Set<T> =
        split(SET_SEPARATOR).filter { it.isNotEmpty() }.mapTo(linkedSetOf()) { parse(it) }
}
