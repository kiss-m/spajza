package sk.matej.spajza.domain.book

import sk.matej.spajza.domain.model.Course
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.own.OwnRecipes

/**
 * The chapters of the recipe book in reading order (D15): two shortcuts, then the cookbook. Mains are
 * split by their meat or fish, from [POULTRY] to [MEATLESS].
 */
enum class BookChapter {
    FAVORITES,
    OWN,
    BREAKFAST,
    SOUP,
    SALAD,
    POULTRY,
    PORK,
    BEEF_LAMB,
    FISH,
    MEATLESS,
    SIDE,
    SNACK,
    DESSERT,
    DRINK,
}

data class BookSection(val chapter: BookChapter, val recipes: List<Recipe>)

/** The recipe book's tunables. */
object RecipeBookConfig {
    /**
     * Meat that counts as poultry. Other meat is pork when flagged so, otherwise beef and lamb; the
     * seed content tests make sure every meat ingredient is one of these on purpose.
     */
    val POULTRY_IDS: Set<String> = setOf("chicken-breast", "chicken-thigh", "chicken-liver", "turkey-breast", "ground-turkey")
}

/** Sorts recipes into the chapters of the recipe book. */
class RecipeBook(private val ingredients: (String) -> Ingredient?) {

    fun chapterOf(recipe: Recipe): BookChapter = when (recipe.course) {
        Course.BREAKFAST -> BookChapter.BREAKFAST
        Course.SOUP -> BookChapter.SOUP
        Course.SALAD -> BookChapter.SALAD
        Course.MAIN -> mainChapter(recipe)
        Course.SIDE -> BookChapter.SIDE
        Course.SNACK -> BookChapter.SNACK
        Course.DESSERT -> BookChapter.DESSERT
        Course.DRINK -> BookChapter.DRINK
    }

    /**
     * Favorites and own recipes first, then every chapter that has recipes; a recipe in a shortcut
     * stays in its own chapter as well. Recipes keep the given [order] inside each section.
     */
    fun sections(recipes: List<Recipe>, favorites: Set<String>, order: Comparator<Recipe>): List<BookSection> {
        val sorted = recipes.sortedWith(order)
        val byChapter = sorted.groupBy(::chapterOf)
        val shortcuts = listOf(
            BookSection(BookChapter.FAVORITES, sorted.filter { it.id in favorites }),
            BookSection(BookChapter.OWN, sorted.filter { OwnRecipes.isOwn(it.id) }),
        )
        val chapters = BookChapter.entries.filter { it !in SHORTCUTS }.map { BookSection(it, byChapter[it].orEmpty()) }
        return (shortcuts + chapters).filter { it.recipes.isNotEmpty() }
    }

    /**
     * The heaviest meat or fish among the main ingredients decides, then among the secondary ones;
     * a main without either is meatless. Seasonings, stock, sauces and fats do not count even when
     * they are flagged as meat or fish (fish sauce, lard).
     */
    private fun mainChapter(recipe: Recipe): BookChapter {
        for (role in listOf(IngredientRole.MAIN, IngredientRole.SECONDARY)) {
            val heaviest = recipe.ingredients
                .filter { it.role == role }
                .mapNotNull { line -> ingredients(line.ingredientId)?.let { ingredient -> kindOf(ingredient)?.let { line.grams to it } } }
                .maxByOrNull { it.first }
            if (heaviest != null) return heaviest.second
        }
        return BookChapter.MEATLESS
    }

    private fun kindOf(ingredient: Ingredient): BookChapter? = when (ingredient.category) {
        IngredientCategory.FISH -> BookChapter.FISH
        IngredientCategory.MEAT -> when {
            DietFlag.PORK in ingredient.dietFlags -> BookChapter.PORK
            ingredient.id in RecipeBookConfig.POULTRY_IDS -> BookChapter.POULTRY
            else -> BookChapter.BEEF_LAMB
        }
        else -> null
    }

    private companion object {
        val SHORTCUTS = setOf(BookChapter.FAVORITES, BookChapter.OWN)
    }
}
