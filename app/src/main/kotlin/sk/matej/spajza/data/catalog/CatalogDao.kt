package sk.matej.spajza.data.catalog

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
interface CatalogDao {
    @Query("SELECT * FROM ingredient")
    suspend fun ingredients(): List<IngredientEntity>

    @Query("SELECT * FROM recipe")
    suspend fun recipes(): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredient ORDER BY recipeId, position")
    suspend fun recipeIngredients(): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_step ORDER BY recipeId, stepIndex")
    suspend fun recipeSteps(): List<RecipeStepEntity>

    @Query("SELECT * FROM substitution")
    suspend fun substitutions(): List<SubstitutionEntity>

    @Query("SELECT metaValue FROM catalog_meta WHERE metaKey = :key")
    suspend fun meta(key: String): String?

    @Insert
    suspend fun insertIngredients(items: List<IngredientEntity>)

    @Insert
    suspend fun insertRecipes(items: List<RecipeEntity>)

    @Insert
    suspend fun insertRecipeIngredients(items: List<RecipeIngredientEntity>)

    @Insert
    suspend fun insertRecipeSteps(items: List<RecipeStepEntity>)

    @Insert
    suspend fun insertSubstitutions(items: List<SubstitutionEntity>)

    @Insert
    suspend fun insertMeta(items: List<CatalogMetaEntity>)

    @Query("DELETE FROM ingredient")
    suspend fun clearIngredients()

    @Query("DELETE FROM recipe")
    suspend fun clearRecipes()

    @Query("DELETE FROM recipe_ingredient")
    suspend fun clearRecipeIngredients()

    @Query("DELETE FROM recipe_step")
    suspend fun clearRecipeSteps()

    @Query("DELETE FROM substitution")
    suspend fun clearSubstitutions()

    @Query("DELETE FROM catalog_meta")
    suspend fun clearMeta()

    /** Replaces the whole catalog in one transaction. */
    @Transaction
    suspend fun replaceAll(tables: CatalogTables, meta: List<CatalogMetaEntity>) {
        clearRecipeIngredients()
        clearRecipeSteps()
        clearSubstitutions()
        clearRecipes()
        clearIngredients()
        clearMeta()
        insertIngredients(tables.ingredients)
        insertRecipes(tables.recipes)
        insertRecipeIngredients(tables.recipeIngredients)
        insertRecipeSteps(tables.recipeSteps)
        insertSubstitutions(tables.substitutions)
        insertMeta(meta)
    }
}
