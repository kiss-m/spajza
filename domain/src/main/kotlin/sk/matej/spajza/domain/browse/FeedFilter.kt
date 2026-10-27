package sk.matej.spajza.domain.browse

import sk.matej.spajza.domain.model.ComputedTag
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.model.RecipeTag

/** The type chips on "Čo uvariť", in display order; each stands for an authored or a computed tag. */
enum class RecipeType(val tag: RecipeTag? = null, val computedTag: ComputedTag? = null) {
    QUICK(computedTag = ComputedTag.QUICK),
    STUDENT(tag = RecipeTag.STUDENT),
    CHEAP(computedTag = ComputedTag.CHEAP),
    VEGAN(computedTag = ComputedTag.VEGAN),
    VEGETARIAN(computedTag = ComputedTag.VEGETARIAN),
    HIGH_PROTEIN(computedTag = ComputedTag.HIGH_PROTEIN),
    BULK(computedTag = ComputedTag.BULK),
    LIGHT(computedTag = ComputedTag.LIGHT),
    MEAL_PREP(tag = RecipeTag.MEAL_PREP),
    ONE_POT(tag = RecipeTag.ONE_POT),
    ;

    fun appliesTo(recipe: Recipe): Boolean =
        (tag == null || tag in recipe.tags) && (computedTag == null || computedTag in recipe.computedTags)
}

/**
 * The chips and meal-type filter of "Čo uvariť", applied to both sections. Selected cuisines are
 * alternatives; selected types must all apply.
 */
data class FeedFilter(
    val cuisines: Set<Cuisine> = emptySet(),
    val types: Set<RecipeType> = emptySet(),
    val mealType: MealType? = null,
) {
    val isEmpty: Boolean get() = cuisines.isEmpty() && types.isEmpty() && mealType == null

    /** Any cuisine or type chip is on (the meal type is a tab, not a chip). */
    val hasChips: Boolean get() = cuisines.isNotEmpty() || types.isNotEmpty()

    fun matches(recipe: Recipe): Boolean =
        (cuisines.isEmpty() || recipe.cuisine in cuisines) &&
            types.all { it.appliesTo(recipe) } &&
            (mealType == null || mealType in recipe.mealTypes)
}
