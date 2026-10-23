package sk.matej.spajza.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.repository.CatalogRepository

/** Emits nothing until [catalogState] holds a catalog, like the real one before its import ends. */
class FakeCatalogRepository(initial: Catalog? = null) : CatalogRepository {
    val catalogState = MutableStateFlow(initial)

    override val catalog: Flow<Catalog> = catalogState.filterNotNull()
}
