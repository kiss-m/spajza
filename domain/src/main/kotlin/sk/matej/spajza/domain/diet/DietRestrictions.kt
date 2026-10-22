package sk.matej.spajza.domain.diet

import sk.matej.spajza.domain.model.Allergen

enum class Diet { VEGAN, VEGETARIAN, PESCATARIAN, GLUTEN_FREE, DAIRY_FREE, EGG_FREE, PORK_FREE }

data class DietRestrictions(
    val diets: Set<Diet> = emptySet(),
    val allergens: Set<Allergen> = emptySet(),
) {
    val isEmpty: Boolean get() = diets.isEmpty() && allergens.isEmpty()
}
