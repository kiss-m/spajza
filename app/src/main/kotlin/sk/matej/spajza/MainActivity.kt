package sk.matej.spajza

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import sk.matej.spajza.ui.SpajzaRoot
import sk.matej.spajza.ui.theme.SpajzaTheme

// AppCompatActivity (not ComponentActivity) so the per-app language API applies to this activity.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SpajzaTheme {
                SpajzaRoot()
            }
        }
    }
}
