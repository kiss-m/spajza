package sk.matej.spajza.data.seed

import sk.matej.spajza.domain.catalog.Catalog

data class CatalogBuildResult(val catalog: Catalog, val issues: List<SeedIssue>) {
    val isValid: Boolean get() = issues.isEmpty()

    /** The catalog, or a [SeedValidationException] listing every issue. */
    fun getOrThrow(): Catalog = if (isValid) catalog else throw SeedValidationException(issues)
}
