package sk.matej.spajza.data.catalog

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        IngredientEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeStepEntity::class,
        SubstitutionEntity::class,
        CatalogMetaEntity::class,
    ],
    version = 1,
    // The catalog is rebuilt from the bundled seed, so there is no schema history to keep.
    exportSchema = false,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
