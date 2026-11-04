package sk.matej.spajza.data.firestore

import sk.matej.spajza.domain.custom.CustomIngredient
import sk.matej.spajza.domain.custom.CustomSource
import sk.matej.spajza.domain.custom.Energy
import sk.matej.spajza.domain.diet.Diet
import sk.matej.spajza.domain.diet.DietRestrictions
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.model.RecipeTag
import sk.matej.spajza.domain.nutrition.Nutrition
import sk.matej.spajza.domain.own.OwnIngredientLine
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.own.OwnStep
import sk.matej.spajza.domain.own.RecipeSource
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.profile.ActivityLevel
import sk.matej.spajza.domain.profile.BodyProfile
import sk.matej.spajza.domain.profile.CookingSkill
import sk.matej.spajza.domain.profile.Goal
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.profile.Sex
import sk.matej.spajza.domain.profile.SpicyTolerance
import sk.matej.spajza.domain.profile.UserProfile
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.rating.Verdict
import sk.matej.spajza.domain.unit.MeasureUnit
import java.time.Instant
import java.time.LocalDate

/**
 * Converts Firestore document data to domain models and back. Reading is lenient: missing fields
 * take their defaults and unknown enum names (written by a newer app version) are skipped, so an
 * older app never crashes on newer data.
 */
object FirestoreMappers {
    // users/{uid}

    fun userFrom(uid: String, data: Map<String, Any?>): UserProfile {
        val defaults = Preferences()
        val profile = data.map("profile")
        val preferences = data.map("preferences")
        val restrictions = data.map("restrictions")
        return UserProfile(
            uid = uid,
            displayName = data["displayName"] as? String,
            body = BodyProfile(
                sex = profile.enum<Sex>("sex"),
                birthYear = profile.int("birthYear"),
                heightCm = profile.double("heightCm"),
                weightKg = profile.double("weightKg"),
                activityLevel = profile.enum<ActivityLevel>("activityLevel"),
                goal = profile.enum<Goal>("goal") ?: Goal.MAINTAIN,
                kcalOverride = profile.int("kcalOverride"),
            ),
            preferences = Preferences(
                likedIngredients = preferences.strings("likedIngredients"),
                dislikedIngredients = preferences.strings("dislikedIngredients"),
                likedCuisines = preferences.enums<Cuisine>("likedCuisines"),
                maxTimeWeekdayMin = preferences.int("maxTimeWeekdayMin") ?: defaults.maxTimeWeekdayMin,
                maxTimeWeekendMin = preferences.int("maxTimeWeekendMin") ?: defaults.maxTimeWeekendMin,
                cookingSkill = preferences.enum<CookingSkill>("cookingSkill") ?: defaults.cookingSkill,
                spicyTolerance = preferences.enum<SpicyTolerance>("spicyTolerance") ?: defaults.spicyTolerance,
                budget = preferences.enum<PriceTier>("budget") ?: defaults.budget,
                defaultServings = preferences.int("defaultServings") ?: defaults.defaultServings,
                mealPrep = preferences["mealPrep"] as? Boolean ?: defaults.mealPrep,
            ),
            restrictions = DietRestrictions(
                diets = restrictions.enums<Diet>("diets"),
                allergens = restrictions.enums<Allergen>("allergens"),
            ),
            householdId = data["householdId"] as? String,
            onboardingDone = data["onboardingDone"] as? Boolean ?: false,
        )
    }

    fun bodyTo(body: BodyProfile): Map<String, Any?> = mapOf(
        "sex" to body.sex?.name,
        "birthYear" to body.birthYear,
        "heightCm" to body.heightCm,
        "weightKg" to body.weightKg,
        "activityLevel" to body.activityLevel?.name,
        "goal" to body.goal.name,
        "kcalOverride" to body.kcalOverride,
    )

    fun preferencesTo(preferences: Preferences): Map<String, Any?> = mapOf(
        "likedIngredients" to preferences.likedIngredients.sorted(),
        "dislikedIngredients" to preferences.dislikedIngredients.sorted(),
        "likedCuisines" to preferences.likedCuisines.map { it.name }.sorted(),
        "maxTimeWeekdayMin" to preferences.maxTimeWeekdayMin,
        "maxTimeWeekendMin" to preferences.maxTimeWeekendMin,
        "cookingSkill" to preferences.cookingSkill.name,
        "spicyTolerance" to preferences.spicyTolerance.name,
        "budget" to preferences.budget.name,
        "defaultServings" to preferences.defaultServings,
        "mealPrep" to preferences.mealPrep,
    )

    // households/{hid}

    fun householdFrom(id: String, data: Map<String, Any?>, inviteExpiresAt: Instant? = null): Household = Household(
        id = id,
        name = data["name"] as? String ?: "",
        memberUids = data.strings("memberUids").toList(),
        memberNames = data.map("memberNames").mapNotNull { (uid, name) -> (name as? String)?.let { uid to it } }.toMap(),
        staples = data.strings("staples"),
        equipment = if (data.containsKey("equipment")) data.enums<Equipment>("equipment") else Household.DEFAULT_EQUIPMENT,
        inviteCode = data["inviteCode"] as? String,
        inviteExpiresAt = inviteExpiresAt,
    )

    fun newHousehold(
        name: String,
        ownerUid: String,
        ownerName: String?,
        staples: Set<String>,
        equipment: Set<Equipment>,
    ): Map<String, Any?> = mapOf(
        "name" to name,
        "memberUids" to listOf(ownerUid),
        "memberNames" to (ownerName?.let { mapOf(ownerUid to it) } ?: emptyMap()),
        "staples" to staples.sorted(),
        "equipment" to equipment.map { it.name }.sorted(),
    )

    // households/{hid}/pantry/{i_<slug> | c_<customId>}

    fun pantryItemFrom(data: Map<String, Any?>, updatedAt: Instant? = null): PantryItem? {
        val ingredient = ingredientRefFrom(data) ?: return null
        return PantryItem(
            ingredient = ingredient,
            quantity = data.double("quantity"),
            expiresAt = (data["expiresAt"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            addedBy = data["addedBy"] as? String,
            updatedAt = updatedAt,
        )
    }

    /** The full document of a pantry item, used to restore it (cooking undo). */
    fun pantryItemTo(item: PantryItem): Map<String, Any?> = ingredientRefTo(item.ingredient) + mapOf(
        "quantity" to item.quantity,
        "expiresAt" to item.expiresAt?.toString(),
        "addedBy" to item.addedBy,
    )

    /** Keeps the earlier of the stored and the new expiry date (null when nothing changes). */
    fun earlierExpiry(stored: Map<String, Any?>?, added: LocalDate?): LocalDate? {
        val current = (stored?.get("expiresAt") as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val earliest = listOfNotNull(current, added).minOrNull()
        return earliest?.takeIf { it != current }
    }

    // households/{hid}/shopping/{id}

    fun shoppingItemFrom(id: String, data: Map<String, Any?>): ShoppingItem? {
        val ingredient = ingredientRefFrom(data)
        val text = data["text"] as? String
        if (ingredient == null && text.isNullOrBlank()) return null
        return ShoppingItem(
            id = id,
            ingredient = ingredient,
            text = text,
            amount = data.double("amount"),
            checked = data["checked"] as? Boolean ?: false,
            reasonRecipeIds = data.strings("reasonRecipeIds"),
            addedBy = data["addedBy"] as? String,
        )
    }

    // households/{hid}/customIngredients/{id}

    fun customIngredientFrom(id: String, data: Map<String, Any?>): CustomIngredient? {
        val name = (data["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val n = data.map("nutrition")
        val nutrition = Nutrition(
            kcal = 0.0,
            protein = n.double("protein") ?: 0.0,
            carbs = n.double("carbs") ?: 0.0,
            fat = n.double("fat") ?: 0.0,
            fiber = n.double("fiber") ?: 0.0,
            sugar = n.double("sugar") ?: 0.0,
            salt = n.double("salt") ?: 0.0,
        )
        return CustomIngredient(
            id = id,
            name = name,
            brand = data["brand"] as? String,
            barcode = data["barcode"] as? String,
            category = data.enum<IngredientCategory>("category") ?: IngredientCategory.OTHER,
            // Stored energy is ignored: it is always computed from the macros.
            nutrition = Energy.recomputed(nutrition),
            nutritionKnown = data["nutritionKnown"] as? Boolean ?: false,
            pantryUnit = data.enum<PantryUnit>("pantryUnit") ?: PantryUnit.G,
            gramsPerPiece = data.double("gramsPerPiece"),
            densityGPerMl = data.double("densityGPerMl"),
            packageQuantity = data.double("packageQuantity"),
            allergens = data.enums<Allergen>("allergens"),
            dietFlags = data.enums<DietFlag>("dietFlags"),
            catalogId = data["catalogId"] as? String,
            source = data.enum<CustomSource>("source") ?: CustomSource.MANUAL,
        )
    }

    fun customIngredientTo(ingredient: CustomIngredient): Map<String, Any?> = mapOf(
        "name" to ingredient.name,
        "brand" to ingredient.brand,
        "barcode" to ingredient.barcode,
        "category" to ingredient.category.name,
        "nutrition" to with(ingredient.nutrition) {
            mapOf("kcal" to kcal, "protein" to protein, "carbs" to carbs, "fat" to fat, "fiber" to fiber, "sugar" to sugar, "salt" to salt)
        },
        "nutritionKnown" to ingredient.nutritionKnown,
        "pantryUnit" to ingredient.pantryUnit.name,
        "gramsPerPiece" to ingredient.gramsPerPiece,
        "densityGPerMl" to ingredient.densityGPerMl,
        "packageQuantity" to ingredient.packageQuantity,
        "allergens" to ingredient.allergens.map { it.name },
        "dietFlags" to ingredient.dietFlags.map { it.name },
        "catalogId" to ingredient.catalogId,
        "source" to ingredient.source.name,
    )

    // households/{hid}/recipes/{id}

    fun ownRecipeFrom(id: String, data: Map<String, Any?>): OwnRecipe? {
        val title = (data["title"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        return OwnRecipe(
            id = id,
            title = title,
            description = data["description"] as? String ?: "",
            servings = data.int("servings")?.takeIf { it > 0 } ?: OwnRecipe.DEFAULT_SERVINGS,
            totalTimeMin = data.int("totalTimeMin")?.takeIf { it > 0 } ?: OwnRecipe.DEFAULT_MINUTES,
            activeTimeMin = data.int("activeTimeMin")?.takeIf { it > 0 },
            difficulty = data.int("difficulty")?.coerceIn(1, 3) ?: 1,
            cuisine = data.enum<Cuisine>("cuisine") ?: Cuisine.INTERNATIONAL,
            mealTypes = data.enums<MealType>("mealTypes").ifEmpty { OwnRecipe.DEFAULT_MEAL_TYPES },
            equipment = data.enums<Equipment>("equipment"),
            tags = data.enums<RecipeTag>("tags"),
            ingredients = data.maps("ingredients").mapNotNull { line ->
                val text = (line["text"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                OwnIngredientLine(
                    text = text,
                    ingredientId = line["ingredientId"] as? String,
                    amount = line.double("amount"),
                    unit = line.enum<MeasureUnit>("unit"),
                    optional = line["optional"] as? Boolean ?: false,
                )
            },
            steps = data.maps("steps").mapNotNull { step ->
                val text = (step["text"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                OwnStep(text, step.int("timerSeconds")?.takeIf { it > 0 })
            },
            source = data.enum<RecipeSource>("source") ?: RecipeSource.MANUAL,
            sourceUrl = data["sourceUrl"] as? String,
        )
    }

    fun ownRecipeTo(recipe: OwnRecipe): Map<String, Any?> = mapOf(
        "title" to recipe.title,
        "description" to recipe.description,
        "servings" to recipe.servings,
        "totalTimeMin" to recipe.totalTimeMin,
        "activeTimeMin" to recipe.activeTimeMin,
        "difficulty" to recipe.difficulty,
        "cuisine" to recipe.cuisine.name,
        "mealTypes" to recipe.mealTypes.map { it.name },
        "equipment" to recipe.equipment.map { it.name },
        "tags" to recipe.tags.map { it.name },
        "ingredients" to recipe.ingredients.map { line ->
            mapOf(
                "text" to line.text,
                "ingredientId" to line.ingredientId,
                "amount" to line.amount,
                "unit" to line.unit?.name,
                "optional" to line.optional,
            )
        },
        "steps" to recipe.steps.map { mapOf("text" to it.text, "timerSeconds" to it.timerSeconds) },
        "source" to recipe.source.name,
        "sourceUrl" to recipe.sourceUrl,
    )

    // users/{uid}/ratings/{recipeId}

    fun ratingFrom(recipeId: String, data: Map<String, Any?>) = Rating(
        recipeId = recipeId,
        verdict = data.enum<Verdict>("verdict"),
        favorite = data["favorite"] as? Boolean ?: false,
        note = data["note"] as? String,
    )

    // Shared helpers

    fun ingredientRefTo(ref: IngredientRef): Map<String, Any?> = when (ref) {
        is IngredientRef.Catalog -> mapOf(FIELD_INGREDIENT_ID to ref.id)
        is IngredientRef.Custom -> mapOf(FIELD_CUSTOM_INGREDIENT_ID to ref.id)
    }

    private fun ingredientRefFrom(data: Map<String, Any?>): IngredientRef? =
        (data[FIELD_INGREDIENT_ID] as? String)?.let { IngredientRef.Catalog(it) }
            ?: (data[FIELD_CUSTOM_INGREDIENT_ID] as? String)?.let { IngredientRef.Custom(it) }

    private const val FIELD_INGREDIENT_ID = "ingredientId"
    private const val FIELD_CUSTOM_INGREDIENT_ID = "customIngredientId"

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String): Map<String, Any?> = this[key] as? Map<String, Any?> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.maps(key: String): List<Map<String, Any?>> =
        (this[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { it as Map<String, Any?> }

    private fun Map<String, Any?>.strings(key: String): Set<String> =
        (this[key] as? List<*>).orEmpty().filterIsInstance<String>().toCollection(linkedSetOf())

    private fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

    private inline fun <reified T : Enum<T>> Map<String, Any?>.enum(key: String): T? =
        (this[key] as? String)?.let { name -> enumValues<T>().firstOrNull { it.name == name } }

    private inline fun <reified T : Enum<T>> Map<String, Any?>.enums(key: String): Set<T> =
        strings(key).mapNotNullTo(linkedSetOf()) { name -> enumValues<T>().firstOrNull { it.name == name } }
}
