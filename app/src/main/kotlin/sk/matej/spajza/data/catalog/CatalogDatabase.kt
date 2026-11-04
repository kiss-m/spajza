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
    // 2: recipes have a recipe book chapter (course). The catalog is rebuilt from the bundled seed,
    // so a new version just drops the tables and imports again; there is no schema history to keep.
    version = 2,
    exportSchema = false,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
