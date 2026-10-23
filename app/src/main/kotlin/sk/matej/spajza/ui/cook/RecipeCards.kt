package sk.matej.spajza.ui.cook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sk.matej.spajza.R
import sk.matej.spajza.ui.common.currentLanguage
import sk.matej.spajza.ui.common.durationText
import sk.matej.spajza.ui.common.text
import sk.matej.spajza.ui.components.MacroLine
import sk.matej.spajza.ui.components.RecipePlaceholder
import sk.matej.spajza.ui.components.SpajzaIcons

/** A card in the horizontal "Môžeš uvariť hneď" and "Treba dokúpiť" rows. */
@Composable
fun RecipeCard(model: RecipeCardModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val language = currentLanguage()
    Card(onClick = onClick, modifier = modifier.width(244.dp)) {
        Box {
            RecipePlaceholder(model.cuisine, Modifier.fillMaxWidth().height(96.dp))
            if (model.favorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.cook_favorite),
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                model.title.text(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MacroLine(model.kcal, model.protein, model.carbs, model.fat)
            TimeLabel(model.totalTimeMin)
            if (model.missing.isNotEmpty()) {
                Text(
                    stringResource(R.string.cook_missing, model.missing.joinToString { it.name[language] }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A full-width row for search results, with whether it can be cooked now. */
@Composable
fun RecipeListItem(model: RecipeCardModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecipePlaceholder(model.cuisine, Modifier.size(88.dp), iconSize = 28.dp)
            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(model.title.text(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                MacroLine(model.kcal, model.protein, model.carbs, model.fat)
                val missing = model.missing.size
                Text(
                    if (missing == 0) stringResource(R.string.cook_status_ready) else pluralStringResource(R.plurals.cook_status_missing, missing, missing),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (missing == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
fun TimeLabel(minutes: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(SpajzaIcons.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(durationText(minutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
