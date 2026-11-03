package sk.matej.spajza

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import sk.matej.spajza.data.photos.RecipePhotoStore
import sk.matej.spajza.ui.SpajzaRoot
import sk.matej.spajza.ui.components.LocalRecipeCovers
import sk.matej.spajza.ui.navigation.AppLink
import sk.matej.spajza.ui.navigation.AppLinks
import sk.matej.spajza.ui.theme.SpajzaTheme

// AppCompatActivity (not ComponentActivity) so the per-app language API applies to this activity.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var appLinks: AppLinks

    @Inject
    lateinit var recipePhotos: RecipePhotoStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity has handled its intent already.
        if (savedInstanceState == null) AppLink.from(intent)?.let(appLinks::open)
        setContent {
            val covers by recipePhotos.covers.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalRecipeCovers provides covers) {
                SpajzaTheme {
                    SpajzaRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppLink.from(intent)?.let(appLinks::open)
    }
}
