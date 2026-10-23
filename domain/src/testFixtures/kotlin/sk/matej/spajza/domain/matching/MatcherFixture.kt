package sk.matej.spajza.domain.matching

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.household.Household
import sk.matej.spajza.domain.model.Allergen
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.model.Substitution
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.profile.Goal
import sk.matej.spajza.domain.profile.Preferences
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.line
import sk.matej.spajza.domain.testing.nutrition
import sk.matej.spajza.domain.testing.recipe
import java.time.LocalDate

/** A tiny catalog shared by the matcher, suggester and deduction tests. */
object MatcherFixture {
    val MONDAY: LocalDate = LocalDate.of(2026, 10, 19)
    val SATURDAY: LocalDate = LocalDate.of(2026, 10, 24)

    val ingredients = listOf(
        ingredient("pasta", nutrition(kcal = 360.0, protein = 13.0, carbs = 72.0, fat = 1.5), priceTier = PriceTier.CHEAP),
        ingredient("rice", nutrition(kcal = 360.0, protein = 7.0, carbs = 80.0), priceTier = PriceTier.CHEAP),
        ingredient("tomato", nutrition(kcal = 18.0, protein = 0.9, carbs = 3.9), pantryUnit = PantryUnit.PCS, gramsPerPiece = 120.0, priceTier = PriceTier.MID),
        ingredient("onion", nutrition(kcal = 40.0, protein = 1.1, carbs = 9.0), pantryUnit = PantryUnit.PCS, gramsPerPiece = 110.0, priceTier = PriceTier.CHEAP),
        ingredient("garlic", nutrition(kcal = 149.0, protein = 6.4, carbs = 33.0), priceTier = PriceTier.CHEAP),
        ingredient("chicken", nutrition(kcal = 120.0, protein = 22.5, fat = 2.6), dietFlags = setOf(DietFlag.MEAT, DietFlag.ANIMAL_PRODUCT)),
        ingredient("salmon", nutrition(kcal = 208.0, protein = 20.0, fat = 13.0), dietFlags = setOf(DietFlag.FISH, DietFlag.ANIMAL_PRODUCT), priceTier = PriceTier.EXPENSIVE),
        ingredient("butter", nutrition(kcal = 717.0, protein = 0.9, fat = 81.0), dietFlags = setOf(DietFlag.DAIRY, DietFlag.ANIMAL_PRODUCT)),
        ingredient("oil", nutrition(kcal = 884.0, fat = 100.0), pantryUnit = PantryUnit.ML, densityGPerMl = 0.92, isDefaultStaple = true),
        ingredient("salt", nutrition(kcal = 0.0, salt = 100.0), isDefaultStaple = true),
        ingredient("chili", nutrition(kcal = 40.0, protein = 2.0, carbs = 9.0), spicy = true),
        ingredient("peanuts", nutrition(kcal = 567.0, protein = 26.0, fat = 49.0, carbs = 16.0)).copy(allergens = setOf(Allergen.PEANUTS)),
        ingredient("cream", nutrition(kcal = 340.0, protein = 2.0, fat = 36.0, carbs = 3.0), dietFlags = setOf(DietFlag.DAIRY, DietFlag.ANIMAL_PRODUCT)),
        ingredient("basil", nutrition(kcal = 23.0, protein = 3.0, carbs = 3.0)),
    )

    private fun r(
        id: String,
        vararg lines: sk.matej.spajza.domain.model.RecipeIngredient,
        servings: Int = 2,
        minutes: Int = 20,
        equipment: Set<Equipment> = setOf(Equipment.STOVE),
        cuisine: sk.matej.spajza.domain.model.Cuisine = sk.matej.spajza.domain.model.Cuisine.ITALIAN,
        difficulty: Int = 1,
        tags: Set<sk.matej.spajza.domain.model.RecipeTag> = emptySet(),
    ): Recipe = recipe(id = id, ingredients = lines.toList(), servings = servings, totalTimeMin = minutes, equipment = equipment, cuisine = cuisine, tags = tags)
        .copy(difficulty = difficulty)

    /** pasta 200 + tomato 240 + garlic 10 + oil 14 + salt 3 (+ optional basil). */
    val tomatoPasta = r(
        "tomato-pasta",
        line("pasta", 200.0), line("tomato", 240.0), line("garlic", 10.0, IngredientRole.SEASONING),
        line("oil", 14.0, IngredientRole.SECONDARY), line("salt", 3.0, IngredientRole.SEASONING),
        line("basil", 5.0, IngredientRole.SEASONING, optional = true),
    )
    val garlicPasta = r("garlic-pasta", line("pasta", 200.0), line("garlic", 20.0), line("oil", 40.0, IngredientRole.SECONDARY))
    val chickenRice = r(
        "chicken-rice",
        line("chicken", 300.0), line("rice", 150.0), line("onion", 110.0, IngredientRole.SECONDARY),
        line("salt", 3.0, IngredientRole.SEASONING), cuisine = sk.matej.spajza.domain.model.Cuisine.CHINESE,
    )
    val creamyChicken = r(
        "creamy-chicken",
        line("chicken", 300.0), line("cream", 200.0, IngredientRole.SECONDARY), line("butter", 30.0, IngredientRole.SECONDARY),
        line("onion", 110.0, IngredientRole.SECONDARY), line("pasta", 200.0), cuisine = sk.matej.spajza.domain.model.Cuisine.FRENCH,
    )
    val salmonRice = r("salmon-rice", line("salmon", 250.0), line("rice", 150.0), line("garlic", 5.0, IngredientRole.SEASONING), cuisine = sk.matej.spajza.domain.model.Cuisine.JAPANESE)
    val spicyPasta = r("spicy-pasta", line("pasta", 200.0), line("chili", 10.0, IngredientRole.SEASONING), line("garlic", 10.0, IngredientRole.SEASONING), line("oil", 30.0, IngredientRole.SECONDARY))
    val ovenPasta = r("oven-pasta", line("pasta", 200.0), line("tomato", 240.0), equipment = setOf(Equipment.STOVE, Equipment.OVEN))
    val peanutRice = r("peanut-rice", line("rice", 150.0), line("peanuts", 60.0, IngredientRole.SECONDARY), cuisine = sk.matej.spajza.domain.model.Cuisine.THAI)
    val bigFeast = r(
        "big-feast",
        line("salmon", 250.0), line("chicken", 250.0), line("cream", 200.0), line("butter", 50.0), line("tomato", 120.0),
    )

    val substitutions = listOf(Substitution(ingredientId = "butter", substituteId = "oil", ratio = 0.8))

    fun catalog(vararg recipes: Recipe) = Catalog(ingredients, recipes.toList(), substitutions)

    fun pantry(vararg items: Pair<String, Double?>, expiring: Map<String, LocalDate> = emptyMap()) =
        items.map { (id, quantity) -> PantryItem(IngredientRef.Catalog(id), quantity = quantity, expiresAt = expiring[id]) }

    fun kitchen(
        pantry: List<PantryItem>,
        staples: Set<String> = setOf("oil", "salt"),
        equipment: Set<Equipment> = Household.DEFAULT_EQUIPMENT,
    ) = KitchenState(pantry = pantry, staples = staples, equipment = equipment)

    fun taste(
        preferences: Preferences = Preferences(),
        goal: Goal = Goal.MAINTAIN,
        ratings: Map<String, sk.matej.spajza.domain.rating.Rating> = emptyMap(),
        history: List<sk.matej.spajza.domain.rating.CookEvent> = emptyList(),
        restrictions: sk.matej.spajza.domain.diet.DietRestrictions = sk.matej.spajza.domain.diet.DietRestrictions(),
        dailyKcal: Int? = null,
    ) = Personalization(
        preferences = preferences,
        restrictions = restrictions,
        goal = goal,
        dailyKcal = dailyKcal,
        ratings = ratings,
        cookHistory = history,
    )
}
