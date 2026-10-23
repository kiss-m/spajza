package sk.matej.spajza.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import sk.matej.spajza.R
import sk.matej.spajza.ui.components.SpajzaIcons

// Type-safe navigation keys. Composables named `…Route` wire a ViewModel to a stateless `…Screen`.

@Serializable
data object CookDestination

@Serializable
data object PantryDestination

@Serializable
data object ShoppingDestination

@Serializable
data object ProfileDestination

@Serializable
data class RecipeDestination(val recipeId: String)

@Serializable
data class CookingDestination(val recipeId: String, val servings: Int)

@Serializable
data object AboutDestination

/** The bottom navigation tabs, in order. */
enum class TopLevelDestination(val destination: Any, @param:StringRes val label: Int, val icon: ImageVector) {
    COOK(CookDestination, R.string.nav_cook, SpajzaIcons.Restaurant),
    PANTRY(PantryDestination, R.string.nav_pantry, SpajzaIcons.Kitchen),
    SHOPPING(ShoppingDestination, R.string.nav_shopping, Icons.Filled.ShoppingCart),
    PROFILE(ProfileDestination, R.string.nav_profile, Icons.Filled.Person),
}
