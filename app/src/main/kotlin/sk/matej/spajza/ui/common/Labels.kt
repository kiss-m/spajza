package sk.matej.spajza.ui.common

import androidx.annotation.StringRes
import sk.matej.spajza.R
import sk.matej.spajza.domain.browse.RecipeType
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.Equipment
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.model.PriceTier
import sk.matej.spajza.domain.profile.ActivityLevel
import sk.matej.spajza.domain.profile.CookingSkill
import sk.matej.spajza.domain.profile.Goal
import sk.matej.spajza.domain.profile.Sex
import sk.matej.spajza.domain.profile.SpicyTolerance

// String resources for domain enums. Exhaustive `when`s make a new enum value a compile error here.

@StringRes
fun Cuisine.labelRes(): Int = when (this) {
    Cuisine.SLOVAK_CZECH -> R.string.cuisine_slovak_czech
    Cuisine.CENTRAL_EUROPEAN -> R.string.cuisine_central_european
    Cuisine.ITALIAN -> R.string.cuisine_italian
    Cuisine.FRENCH -> R.string.cuisine_french
    Cuisine.SPANISH -> R.string.cuisine_spanish
    Cuisine.GREEK_MEDITERRANEAN -> R.string.cuisine_greek_mediterranean
    Cuisine.MIDDLE_EASTERN -> R.string.cuisine_middle_eastern
    Cuisine.NORTH_AFRICAN -> R.string.cuisine_north_african
    Cuisine.INDIAN -> R.string.cuisine_indian
    Cuisine.CHINESE -> R.string.cuisine_chinese
    Cuisine.JAPANESE -> R.string.cuisine_japanese
    Cuisine.KOREAN -> R.string.cuisine_korean
    Cuisine.THAI -> R.string.cuisine_thai
    Cuisine.VIETNAMESE -> R.string.cuisine_vietnamese
    Cuisine.MEXICAN -> R.string.cuisine_mexican
    Cuisine.LATIN_AMERICAN -> R.string.cuisine_latin_american
    Cuisine.AMERICAN -> R.string.cuisine_american
    Cuisine.BRITISH -> R.string.cuisine_british
    Cuisine.INTERNATIONAL -> R.string.cuisine_international
}

@StringRes
fun RecipeType.labelRes(): Int = when (this) {
    RecipeType.QUICK -> R.string.type_quick
    RecipeType.STUDENT -> R.string.type_student
    RecipeType.CHEAP -> R.string.type_cheap
    RecipeType.VEGAN -> R.string.type_vegan
    RecipeType.VEGETARIAN -> R.string.type_vegetarian
    RecipeType.HIGH_PROTEIN -> R.string.type_high_protein
    RecipeType.BULK -> R.string.type_bulk
    RecipeType.LIGHT -> R.string.type_light
    RecipeType.MEAL_PREP -> R.string.type_meal_prep
    RecipeType.ONE_POT -> R.string.type_one_pot
}

@StringRes
fun MealType.labelRes(): Int = when (this) {
    MealType.BREAKFAST -> R.string.meal_breakfast
    MealType.LUNCH -> R.string.meal_lunch
    MealType.DINNER -> R.string.meal_dinner
    MealType.SNACK -> R.string.meal_snack
    MealType.DESSERT -> R.string.meal_dessert
}

@StringRes
fun IngredientCategory.labelRes(): Int = when (this) {
    IngredientCategory.VEGETABLES -> R.string.category_vegetables
    IngredientCategory.FRUIT -> R.string.category_fruit
    IngredientCategory.MEAT -> R.string.category_meat
    IngredientCategory.FISH -> R.string.category_fish
    IngredientCategory.DAIRY -> R.string.category_dairy
    IngredientCategory.EGGS -> R.string.category_eggs
    IngredientCategory.GRAINS -> R.string.category_grains
    IngredientCategory.BAKERY -> R.string.category_bakery
    IngredientCategory.LEGUMES -> R.string.category_legumes
    IngredientCategory.NUTS_SEEDS -> R.string.category_nuts_seeds
    IngredientCategory.OILS_FATS -> R.string.category_oils_fats
    IngredientCategory.SPICES -> R.string.category_spices
    IngredientCategory.SAUCES -> R.string.category_sauces
    IngredientCategory.CANNED -> R.string.category_canned
    IngredientCategory.BAKING -> R.string.category_baking
    IngredientCategory.FROZEN -> R.string.category_frozen
    IngredientCategory.OTHER -> R.string.category_other
}

@StringRes
fun Equipment.labelRes(): Int = when (this) {
    Equipment.STOVE -> R.string.equipment_stove
    Equipment.OVEN -> R.string.equipment_oven
    Equipment.MICROWAVE -> R.string.equipment_microwave
    Equipment.BLENDER -> R.string.equipment_blender
    Equipment.FOOD_PROCESSOR -> R.string.equipment_food_processor
    Equipment.HAND_MIXER -> R.string.equipment_hand_mixer
    Equipment.AIR_FRYER -> R.string.equipment_air_fryer
    Equipment.SLOW_COOKER -> R.string.equipment_slow_cooker
    Equipment.PRESSURE_COOKER -> R.string.equipment_pressure_cooker
    Equipment.TOASTER -> R.string.equipment_toaster
}

@StringRes
fun PriceTier.labelRes(): Int = when (this) {
    PriceTier.CHEAP -> R.string.price_cheap
    PriceTier.MID -> R.string.price_mid
    PriceTier.EXPENSIVE -> R.string.price_expensive
}

/** The same tiers worded as a personal budget. */
@StringRes
fun PriceTier.budgetLabelRes(): Int = when (this) {
    PriceTier.CHEAP -> R.string.budget_cheap
    PriceTier.MID -> R.string.budget_mid
    PriceTier.EXPENSIVE -> R.string.budget_expensive
}

@StringRes
fun PantryUnit.labelRes(): Int = when (this) {
    PantryUnit.G -> R.string.unit_g
    PantryUnit.ML -> R.string.unit_ml
    PantryUnit.PCS -> R.string.unit_pcs
}

@StringRes
fun Sex.labelRes(): Int = when (this) {
    Sex.FEMALE -> R.string.sex_female
    Sex.MALE -> R.string.sex_male
    Sex.UNSPECIFIED -> R.string.sex_unspecified
}

@StringRes
fun ActivityLevel.labelRes(): Int = when (this) {
    ActivityLevel.SEDENTARY -> R.string.activity_sedentary
    ActivityLevel.LIGHT -> R.string.activity_light
    ActivityLevel.MODERATE -> R.string.activity_moderate
    ActivityLevel.ACTIVE -> R.string.activity_active
    ActivityLevel.VERY_ACTIVE -> R.string.activity_very_active
}

@StringRes
fun Goal.labelRes(): Int = when (this) {
    Goal.LOSE -> R.string.goal_lose
    Goal.MAINTAIN -> R.string.goal_maintain
    Goal.GAIN -> R.string.goal_gain
}

@StringRes
fun CookingSkill.labelRes(): Int = when (this) {
    CookingSkill.BEGINNER -> R.string.skill_beginner
    CookingSkill.INTERMEDIATE -> R.string.skill_intermediate
    CookingSkill.ADVANCED -> R.string.skill_advanced
}

@StringRes
fun SpicyTolerance.labelRes(): Int = when (this) {
    SpicyTolerance.NONE -> R.string.spicy_none
    SpicyTolerance.MILD -> R.string.spicy_mild
    SpicyTolerance.HOT -> R.string.spicy_hot
}

@StringRes
fun difficultyLabelRes(difficulty: Int): Int = when {
    difficulty <= 1 -> R.string.difficulty_1
    difficulty == 2 -> R.string.difficulty_2
    else -> R.string.difficulty_3
}
