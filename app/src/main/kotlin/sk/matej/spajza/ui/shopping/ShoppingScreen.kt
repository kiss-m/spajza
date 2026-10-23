package sk.matej.spajza.ui.shopping

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import sk.matej.spajza.R
import sk.matej.spajza.ui.components.EmptyState

@Composable
fun ShoppingScreen() {
    EmptyState(
        icon = Icons.Filled.ShoppingCart,
        title = stringResource(R.string.shopping_placeholder_title),
        body = stringResource(R.string.shopping_placeholder_body),
    )
}
