package sk.matej.spajza.ui.cook

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.matej.spajza.R
import sk.matej.spajza.ui.components.EmptyState
import sk.matej.spajza.ui.components.SpajzaIcons

@Composable
fun CookRoute(viewModel: CookViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CookScreen(state)
}

@Composable
fun CookScreen(state: CookUiState) {
    EmptyState(
        icon = SpajzaIcons.Restaurant,
        title = stringResource(R.string.cook_placeholder_title),
        body = stringResource(R.string.cook_placeholder_body),
        action = {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.padding(top = 8.dp))
            } else {
                Text(
                    pluralStringResource(R.plurals.catalog_recipe_count, state.recipeCount, state.recipeCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}
