package sk.matej.spajza.data.seed

import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.Recipe
import sk.matej.spajza.domain.model.Substitution

/** The fully normalized catalog, ready to be written to the database. */
data class Catalog(
    val ingredients: List<Ingredient>,
    val recipes: List<Recipe>,
    val substitutions: List<Substitution>,
) {
    val ingredientsById: Map<String, Ingredient> by lazy { ingredients.associateBy { it.id } }
}

data class CatalogBuildResult(val catalog: Catalog, val issues: List<SeedIssue>) {
    val isValid: Boolean get() = issues.isEmpty()

    /** The catalog, or a [SeedValidationException] listing every issue. */
    fun getOrThrow(): Catalog = if (isValid) catalog else throw SeedValidationException(issues)
}
