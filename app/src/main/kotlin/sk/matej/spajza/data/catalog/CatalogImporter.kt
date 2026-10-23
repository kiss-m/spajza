package sk.matej.spajza.data.catalog

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.matej.spajza.data.seed.CatalogBuilder
import sk.matej.spajza.data.seed.SeedFile
import sk.matej.spajza.data.seed.SeedSources
import sk.matej.spajza.domain.catalog.Catalog

/**
 * Loads the catalog from Room, importing the bundled seed first whenever its fingerprint differs
 * from the one stored with the last import (first launch or an app update with new content).
 */
class CatalogImporter(
    private val assets: AssetManager,
    private val dao: CatalogDao,
    private val builder: CatalogBuilder = CatalogBuilder(),
) {
    suspend fun load(): Catalog = withContext(Dispatchers.IO) {
        val sources = readSeed()
        val fingerprint = sources.fingerprint()
        if (dao.meta(KEY_FINGERPRINT) == fingerprint) {
            CatalogMapper.toCatalog(
                CatalogTables(
                    ingredients = dao.ingredients(),
                    recipes = dao.recipes(),
                    recipeIngredients = dao.recipeIngredients(),
                    recipeSteps = dao.recipeSteps(),
                    substitutions = dao.substitutions(),
                ),
            )
        } else {
            val catalog = builder.build(sources).getOrThrow()
            dao.replaceAll(CatalogMapper.toTables(catalog), listOf(CatalogMetaEntity(KEY_FINGERPRINT, fingerprint)))
            catalog
        }
    }

    private fun readSeed(): SeedSources {
        val files = listOf(SeedSources.INGREDIENTS_DIR, SeedSources.RECIPES_DIR, SeedSources.SUBSTITUTIONS_DIR)
            .flatMap { dir ->
                assets.list("$SEED_ROOT/$dir").orEmpty()
                    .filter { it.endsWith(".json") }
                    .map { name ->
                        val content = assets.open("$SEED_ROOT/$dir/$name").bufferedReader().use { it.readText() }
                        SeedFile("$dir/$name", content)
                    }
            }
        return SeedSources.of(files)
    }

    private companion object {
        const val SEED_ROOT = "seed"
        const val KEY_FINGERPRINT = "seed_fingerprint"
    }
}
