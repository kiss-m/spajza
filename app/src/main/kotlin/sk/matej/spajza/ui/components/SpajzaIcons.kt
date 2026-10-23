package sk.matej.spajza.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The few icons that material-icons-core lacks; shapes follow Material Symbols (Apache 2.0). */
object SpajzaIcons {
    val Restaurant: ImageVector by lazy {
        icon(
            "Restaurant",
            "M11,9H9V2H7v7H5V2H3v7c0,2.12 1.66,3.84 3.75,3.97V22h2.5v-9.03C11.34,12.84 13,11.12 13,9V2h-2V9z" +
                "M16,6v8h2.5v8H21V2C18.24,2 16,4.24 16,6z",
        )
    }

    val Kitchen: ImageVector by lazy {
        icon(
            "Kitchen",
            "M18,2.01L6,2c-1.1,0 -2,0.89 -2,2v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.11 -0.9,-1.99 -2,-1.99z" +
                "M18,20H6v-9.02h12V20zM18,9H6V4h12v5zM8,5h2v3H8zM8,12h2v5H8z",
        )
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()
}
