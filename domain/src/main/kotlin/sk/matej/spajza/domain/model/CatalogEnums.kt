package sk.matej.spajza.domain.model

/** Store-aisle style grouping used by the pantry and the shopping list. */
enum class IngredientCategory {
    VEGETABLES,
    FRUIT,
    MEAT,
    FISH,
    DAIRY,
    EGGS,
    GRAINS,
    BAKERY,
    LEGUMES,
    NUTS_SEEDS,
    OILS_FATS,
    SPICES,
    SAUCES,
    CANNED,
    BAKING,
    FROZEN,
    OTHER,
}

/** The 14 allergens that EU Regulation 1169/2011 requires to be declared. */
enum class Allergen {
    GLUTEN,
    CRUSTACEANS,
    EGGS,
    FISH,
    PEANUTS,
    SOY,
    MILK,
    TREE_NUTS,
    CELERY,
    MUSTARD,
    SESAME,
    SULPHITES,
    LUPIN,
    MOLLUSCS,
}

/**
 * Facts about an ingredient that diet filters and computed tags are derived from.
 * [FISH] covers fish and seafood; [ANIMAL_PRODUCT] covers anything of animal origin
 * (meat, fish, dairy, eggs, honey, gelatine).
 */
enum class DietFlag { MEAT, FISH, ANIMAL_PRODUCT, DAIRY, EGG, GLUTEN, PORK }

enum class PriceTier { CHEAP, MID, EXPENSIVE }

/** The single unit a pantry quantity of an ingredient is stored in (see docs/DECISIONS.md D3). */
enum class PantryUnit { G, ML, PCS }

enum class Cuisine {
    SLOVAK_CZECH,
    CENTRAL_EUROPEAN,
    ITALIAN,
    FRENCH,
    SPANISH,
    GREEK_MEDITERRANEAN,
    MIDDLE_EASTERN,
    NORTH_AFRICAN,
    INDIAN,
    CHINESE,
    JAPANESE,
    KOREAN,
    THAI,
    VIETNAMESE,
    MEXICAN,
    LATIN_AMERICAN,
    AMERICAN,
    BRITISH,
    INTERNATIONAL,
}

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK, DESSERT }

/**
 * The chapter of the recipe book a recipe belongs to (D15), in reading order. Catalog recipes name
 * one in the seed; an own recipe may leave it out and gets [forMealTypes].
 */
enum class Course {
    BREAKFAST,
    SOUP,
    SALAD,
    MAIN,
    SIDE,
    SNACK,
    DESSERT,
    DRINK,
    ;

    companion object {
        /** The chapter for a recipe that names none: lunch or dinner make a main, then breakfast, dessert, snack. */
        fun forMealTypes(mealTypes: Set<MealType>): Course = when {
            MealType.LUNCH in mealTypes || MealType.DINNER in mealTypes -> MAIN
            MealType.BREAKFAST in mealTypes -> BREAKFAST
            MealType.DESSERT in mealTypes -> DESSERT
            MealType.SNACK in mealTypes -> SNACK
            else -> MAIN
        }
    }
}

/** Kitchen equipment a recipe may require. Pots, pans and knives are assumed. */
enum class Equipment {
    STOVE,
    OVEN,
    MICROWAVE,
    BLENDER,
    FOOD_PROCESSOR,
    HAND_MIXER,
    AIR_FRYER,
    SLOW_COOKER,
    PRESSURE_COOKER,
    TOASTER,
}

/** Tags written by the recipe author. */
enum class RecipeTag { STUDENT, MEAL_PREP, ONE_POT, NO_COOK, FREEZER_FRIENDLY, COMFORT }

/** Tags derived at import time from ingredients, nutrition and time; never authored. */
enum class ComputedTag { VEGAN, VEGETARIAN, HIGH_PROTEIN, BULK, LIGHT, CHEAP, QUICK }

enum class IngredientRole(val coverageWeight: Int) {
    MAIN(3),
    SECONDARY(2),
    SEASONING(1),
}
