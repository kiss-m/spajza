package sk.matej.spajza.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sk.matej.spajza.R
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.ui.common.labelRes

// Generated recipe pictures (no stock photos): a warm gradient chosen by cuisine with a cutlery icon.

private val LIGHT_HUES = listOf(
    Color(0xFFE8A87C), Color(0xFFD9B26F), Color(0xFFB9C98B), Color(0xFFE59A8C),
    Color(0xFFC9A27E), Color(0xFFA9C2A4), Color(0xFFE3B98F), Color(0xFFD08F70),
)
private val DARK_HUES = listOf(
    Color(0xFF7A4A2E), Color(0xFF6E5A2A), Color(0xFF4F5E35), Color(0xFF7A3E34),
    Color(0xFF5E4630), Color(0xFF3F5640), Color(0xFF6E5235), Color(0xFF6A3B27),
)

@Composable
fun RecipePlaceholder(cuisine: Cuisine, modifier: Modifier = Modifier, iconSize: Dp = 40.dp) {
    val hues = if (isSystemInDarkTheme()) DARK_HUES else LIGHT_HUES
    val base = hues[cuisine.ordinal % hues.size]
    val second = hues[(cuisine.ordinal + 3) % hues.size]
    Box(
        modifier.background(Brush.linearGradient(listOf(base, second))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SpajzaIcons.Restaurant,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(iconSize),
        )
        Text(
            stringResource(cuisine.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.25f), MaterialTheme.shapes.small)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** "520 kcal · B 30 g · S 60 g · T 15 g" style nutrition line for one serving. */
@Composable
fun MacroLine(kcal: Int, protein: Int, carbs: Int, fat: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.kcal_value, kcal),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.macros_short, protein, carbs, fat),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
