package sk.matej.spajza.ui.recipe

import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.matching.LineStatus
import sk.matej.spajza.domain.matching.RecipeMatch
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.nutrition.Nutrition
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.own.OwnRecipes
import sk.matej.spajza.domain.rating.Rating
import sk.matej.spajza.domain.rating.Verdict
import sk.matej.spajza.domain.unit.MeasureUnit

/** One ingredient line, scaled to the chosen servings, with what the pantry says about it. */
data class DetailLine(
    val ingredientId: String,
    val name: LocalizedText,
    val amount: Double,
    val unit: MeasureUnit,
    val status: LineStatus,
    val optional: Boolean,
    val note: LocalizedText?,
    /** The pantry ingredient used instead (status SUBSTITUTE). */
    val substituteName: LocalizedText?,
    /** Known substitutes for a missing ingredient. */
    val alternatives: List<LocalizedText>,
)

data class PairedRecipe(val recipeId: String, val title: LocalizedText)

/** What an own recipe adds to the detail: lines outside the catalog and where it came from. */
data class OwnRecipeInfo(
    /** Lines that match no catalog ingredient, as written. */
    val unmatched: List<String>,
    /** Lines the nutrition leaves out: unmatched ones and amounts that cannot be weighed. */
    val uncounted: List<String>,
    val sourceUrl: String?,
)

data class RecipeDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val recipe: Recipe? = null,
    val servings: Int = 1,
    val perServing: Nutrition = Nutrition.ZERO,
    val lines: List<DetailLine> = emptyList(),
    /** Optional ingredients the pantry has: "Máš aj X — hodí sa sem". */
    val addOns: List<LocalizedText> = emptyList(),
    val pairs: List<PairedRecipe> = emptyList(),
    /** Missing or short ingredients that "Pridať chýbajúce do nákupu" adds. */
    val toBuyCount: Int = 0,
    val favorite: Boolean = false,
    val verdict: Verdict? = null,
    /** Set for the household's own recipes, which can be edited and deleted. */
    val own: OwnRecipeInfo? = null,
)

/** Turns a [RecipeMatch] into the detail screen's state. Pure, so it is unit-tested. */
object RecipeDetailBuilder {
    fun build(catalog: Catalog, match: RecipeMatch, rating: Rating?, own: OwnRecipe? = null): RecipeDetailUiState {
        val recipe = match.recipe
        val factor = match.servings.toDouble() / recipe.servings
        val lines = match.lines.map { lineMatch ->
            val line = lineMatch.line
            DetailLine(
                ingredientId = line.ingredientId,
                name = catalog.ingredient(line.ingredientId).name,
                amount = line.amount * factor,
                unit = line.unit,
                status = lineMatch.status,
                optional = line.optional,
                note = line.note,
                substituteName = lineMatch.substituteId?.let { catalog.ingredientsById[it]?.name },
                alternatives = if (lineMatch.status == LineStatus.MISSING) {
                    catalog.substitutesFor[line.ingredientId].orEmpty().mapNotNull { catalog.ingredientsById[it.substituteId]?.name }
                } else {
                    emptyList()
                },
            )
        }
        return RecipeDetailUiState(
            isLoading = false,
            recipe = recipe,
            servings = match.servings,
            perServing = match.perServing,
            lines = lines,
            addOns = lines.filter { it.optional && it.status in AVAILABLE }.map { it.name },
            pairs = recipe.pairsWith.mapNotNull { id -> catalog.recipesById[id]?.let { PairedRecipe(id, it.title) } },
            toBuyCount = lines.count { !it.optional && (it.status == LineStatus.MISSING || it.status == LineStatus.PARTIAL) },
            favorite = rating?.favorite == true,
            verdict = rating?.verdict,
            own = own?.let {
                OwnRecipeInfo(
                    unmatched = OwnRecipes.unmatched(it, catalog).map { line -> line.text },
                    uncounted = OwnRecipes.uncounted(it, catalog).map { line -> line.text },
                    sourceUrl = it.sourceUrl,
                )
            },
        )
    }

    private val AVAILABLE = setOf(LineStatus.HAVE, LineStatus.PARTIAL, LineStatus.STAPLE, LineStatus.SUBSTITUTE)
}
