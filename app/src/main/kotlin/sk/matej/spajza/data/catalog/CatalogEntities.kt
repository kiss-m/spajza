package sk.matej.spajza.data.catalog

import androidx.room3.Entity
import androidx.room3.PrimaryKey

// Flat tables for the read-only catalog. Collections are stored as delimited strings: the catalog is
// always read whole and rebuilt from the bundled seed, so it never needs queries into them or
// migrations.

@Entity(tableName = "ingredient")
data class IngredientEntity(
    @PrimaryKey val id: String,
    val nameSk: String,
    val nameEn: String,
    val synonyms: String,
    val category: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val salt: Double,
    val nutritionSource: String,
    val pantryUnit: String,
    val gramsPerPiece: Double?,
    val densityGPerMl: Double?,
    val allergens: String,
    val dietFlags: String,
    val isDefaultStaple: Boolean,
    val typicalShelfLifeDays: Int?,
    val priceTier: String,
    val spicy: Boolean,
)

@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val titleSk: String,
    val titleEn: String,
    val descriptionSk: String,
    val descriptionEn: String,
    val cuisine: String,
    val mealTypes: String,
    val totalTimeMin: Int,
    val activeTimeMin: Int,
    val difficulty: Int,
    val servings: Int,
    val equipment: String,
    val tags: String,
    val computedTags: String,
    val pairsWith: String,
)

@Entity(tableName = "recipe_ingredient", primaryKeys = ["recipeId", "position"])
data class RecipeIngredientEntity(
    val recipeId: String,
    val position: Int,
    val ingredientId: String,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val role: String,
    val optional: Boolean,
    val noteSk: String?,
    val noteEn: String?,
)

@Entity(tableName = "recipe_step", primaryKeys = ["recipeId", "stepIndex"])
data class RecipeStepEntity(
    val recipeId: String,
    val stepIndex: Int,
    val textSk: String,
    val textEn: String,
    val timerSeconds: Int?,
)

@Entity(tableName = "substitution", primaryKeys = ["ingredientId", "substituteId"])
data class SubstitutionEntity(
    val ingredientId: String,
    val substituteId: String,
    val ratio: Double,
    val noteSk: String?,
    val noteEn: String?,
)

@Entity(tableName = "catalog_meta")
data class CatalogMetaEntity(
    @PrimaryKey val metaKey: String,
    val metaValue: String,
)
