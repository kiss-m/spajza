package sk.matej.spajza.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import sk.matej.spajza.data.photos.RecipePhotoPaths
import sk.matej.spajza.domain.model.Cuisine
import java.io.File

/** The newest own photo of each recipe on this phone (D14), by [RecipePhotoPaths.keyOf]. */
val LocalRecipeCovers = compositionLocalOf<Map<String, File>> { emptyMap() }

/** The recipe's newest own photo, or the generated picture when it has none. */
@Composable
fun RecipeImage(recipeId: String, cuisine: Cuisine, modifier: Modifier = Modifier, iconSize: Dp = 40.dp) {
    val cover = LocalRecipeCovers.current[RecipePhotoPaths.keyOf(recipeId)]
    if (cover == null) {
        RecipePlaceholder(cuisine, modifier, iconSize)
    } else {
        // The generated picture shows until the photo is loaded, or if it cannot be.
        Box(modifier) {
            RecipePlaceholder(cuisine, Modifier.matchParentSize(), iconSize)
            RecipePhoto(cover, Modifier.matchParentSize())
        }
    }
}

/** An own photo, cropped to fill [modifier]'s bounds unless [contentScale] says otherwise. */
@Composable
fun RecipePhoto(
    photo: File,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(model = photo, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
}
