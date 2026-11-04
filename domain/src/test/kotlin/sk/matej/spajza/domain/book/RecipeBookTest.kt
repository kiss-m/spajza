package sk.matej.spajza.domain.book

import sk.matej.spajza.domain.model.Course
import sk.matej.spajza.domain.model.DietFlag
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.IngredientRole
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.testing.ingredient
import sk.matej.spajza.domain.testing.line
import sk.matej.spajza.domain.testing.recipe
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeBookTest {
    private val meat = setOf(DietFlag.MEAT, DietFlag.ANIMAL_PRODUCT)
    private val ingredients = listOf(
        ingredient("chicken-thigh", category = IngredientCategory.MEAT, dietFlags = meat),
        ingredient("pork-shoulder", category = IngredientCategory.MEAT, dietFlags = meat + DietFlag.PORK),
        ingredient("bacon", category = IngredientCategory.MEAT, dietFlags = meat + DietFlag.PORK),
        ingredient("sausage", category = IngredientCategory.MEAT, dietFlags = meat + DietFlag.PORK),
        ingredient("beef-chuck", category = IngredientCategory.MEAT, dietFlags = meat),
        ingredient("salmon", category = IngredientCategory.FISH, dietFlags = setOf(DietFlag.FISH, DietFlag.ANIMAL_PRODUCT)),
        // Flagged as fish or pork, but none of them makes a fish or pork dish.
        ingredient("fish-sauce", category = IngredientCategory.SAUCES, dietFlags = setOf(DietFlag.FISH, DietFlag.ANIMAL_PRODUCT)),
        ingredient("kimchi", category = IngredientCategory.VEGETABLES, dietFlags = setOf(DietFlag.FISH, DietFlag.ANIMAL_PRODUCT)),
        ingredient("lard", category = IngredientCategory.OILS_FATS, dietFlags = meat + DietFlag.PORK),
        ingredient("tofu", category = IngredientCategory.LEGUMES),
        ingredient("beans", category = IngredientCategory.LEGUMES),
        ingredient("rice", category = IngredientCategory.GRAINS),
    ).associateBy { it.id }
    private val book = RecipeBook(ingredients::get)

    private fun main(vararg lines: Pair<String, Pair<Double, IngredientRole>>) =
        recipe(ingredients = lines.map { (id, amount) -> line(id, amount.first, amount.second) }, course = Course.MAIN)

    private val m = IngredientRole.MAIN
    private val s = IngredientRole.SECONDARY
    private val z = IngredientRole.SEASONING

    @Test
    fun `a recipe without a chapter gets one from its meal types`() {
        assertEquals(Course.MAIN, Course.forMealTypes(setOf(MealType.BREAKFAST, MealType.LUNCH)))
        assertEquals(Course.BREAKFAST, Course.forMealTypes(setOf(MealType.BREAKFAST, MealType.SNACK)))
        assertEquals(Course.DESSERT, Course.forMealTypes(setOf(MealType.SNACK, MealType.DESSERT)))
        assertEquals(Course.SNACK, Course.forMealTypes(setOf(MealType.SNACK)))
        assertEquals(Course.MAIN, Course.forMealTypes(emptySet()))
    }

    @Test
    fun `chapters other than mains follow the course`() {
        val soup = recipe(ingredients = listOf(line("chicken-thigh", 400.0)), course = Course.SOUP)
        assertEquals(BookChapter.SOUP, book.chapterOf(soup), "a chicken soup stays a soup")
        assertEquals(BookChapter.DRINK, book.chapterOf(recipe(ingredients = emptyList(), course = Course.DRINK)))
        assertEquals(BookChapter.SIDE, book.chapterOf(recipe(ingredients = listOf(line("rice", 200.0)), course = Course.SIDE)))
    }

    @Test
    fun `a main goes to the chapter of its meat or fish`() {
        assertEquals(BookChapter.POULTRY, book.chapterOf(main("chicken-thigh" to (500.0 to m), "rice" to (300.0 to m))))
        assertEquals(BookChapter.PORK, book.chapterOf(main("pork-shoulder" to (600.0 to m))))
        assertEquals(BookChapter.BEEF_LAMB, book.chapterOf(main("beef-chuck" to (800.0 to m))))
        assertEquals(BookChapter.FISH, book.chapterOf(main("salmon" to (500.0 to m))))
        assertEquals(BookChapter.MEATLESS, book.chapterOf(main("tofu" to (400.0 to m), "rice" to (300.0 to m))))
    }

    @Test
    fun `the heaviest main meat decides, and main lines come before secondary ones`() {
        assertEquals(BookChapter.POULTRY, book.chapterOf(main("chicken-thigh" to (600.0 to m), "sausage" to (200.0 to m))))
        assertEquals(BookChapter.POULTRY, book.chapterOf(main("chicken-thigh" to (200.0 to m), "bacon" to (300.0 to s))))
        assertEquals(BookChapter.PORK, book.chapterOf(main("beans" to (700.0 to m), "bacon" to (100.0 to s))), "beans with bacon are pork")
    }

    @Test
    fun `seasonings, stock-like sauces and fats flagged as fish or pork leave a main meatless`() {
        assertEquals(BookChapter.MEATLESS, book.chapterOf(main("tofu" to (400.0 to m), "fish-sauce" to (30.0 to z))))
        assertEquals(BookChapter.MEATLESS, book.chapterOf(main("kimchi" to (300.0 to m), "rice" to (300.0 to m))))
        assertEquals(BookChapter.MEATLESS, book.chapterOf(main("beans" to (500.0 to m), "lard" to (40.0 to s))))
        assertEquals(BookChapter.MEATLESS, book.chapterOf(main("rice" to (300.0 to m), "bacon" to (20.0 to z))), "a seasoning does not count")
    }

    @Test
    fun `favorites and own recipes come first and stay in their chapters too`() {
        fun named(id: String, title: String, course: Course, vararg lines: Pair<String, Double>): Recipe =
            recipe(id = id, ingredients = lines.map { (i, g) -> line(i, g) }, course = course)
                .let { it.copy(title = it.title.copy(sk = title, en = title)) }
        val recipes = listOf(
            named("pho", "Pho", Course.SOUP, "beef-chuck" to 300.0),
            named("own:abc", "Babkin guláš", Course.MAIN, "beef-chuck" to 500.0),
            named("curry", "Curry", Course.MAIN, "chicken-thigh" to 400.0),
            named("adobo", "Adobo", Course.MAIN, "chicken-thigh" to 600.0),
            named("cake", "Bábovka", Course.DESSERT),
        )

        val sections = book.sections(recipes, favorites = setOf("curry", "pho"), order = compareBy { it.title.sk })

        assertEquals(
            listOf(BookChapter.FAVORITES, BookChapter.OWN, BookChapter.SOUP, BookChapter.POULTRY, BookChapter.BEEF_LAMB, BookChapter.DESSERT),
            sections.map { it.chapter },
            "empty chapters are left out",
        )
        assertEquals(listOf("curry", "pho"), sections[0].recipes.map { it.id })
        assertEquals(listOf("own:abc"), sections[1].recipes.map { it.id })
        assertEquals(listOf("adobo", "curry"), sections.single { it.chapter == BookChapter.POULTRY }.recipes.map { it.id }, "sorted inside a chapter")
        assertEquals(listOf("own:abc"), sections.single { it.chapter == BookChapter.BEEF_LAMB }.recipes.map { it.id })
    }
}
