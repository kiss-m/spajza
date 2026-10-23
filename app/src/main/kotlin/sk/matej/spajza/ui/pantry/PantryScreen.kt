package sk.matej.spajza.ui.pantry

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import sk.matej.spajza.R
import sk.matej.spajza.ui.components.EmptyState
import sk.matej.spajza.ui.components.SpajzaIcons

@Composable
fun PantryScreen() {
    EmptyState(
        icon = SpajzaIcons.Kitchen,
        title = stringResource(R.string.pantry_placeholder_title),
        body = stringResource(R.string.pantry_placeholder_body),
    )
}
