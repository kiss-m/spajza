package sk.matej.spajza.data.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.repository.CatalogRepository

/** Loads the catalog once per process and shares it with every collector. */
class RoomCatalogRepository(importer: CatalogImporter, scope: CoroutineScope) : CatalogRepository {
    override val catalog: Flow<Catalog> = flow { emit(importer.load()) }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)
}
