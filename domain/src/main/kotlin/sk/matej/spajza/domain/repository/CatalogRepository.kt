package sk.matej.spajza.domain.repository

import kotlinx.coroutines.flow.Flow
import sk.matej.spajza.domain.catalog.Catalog

/** The bundled read-only recipe catalog. */
interface CatalogRepository {
    /** Emits the catalog once it is imported and loaded. */
    val catalog: Flow<Catalog>
}
