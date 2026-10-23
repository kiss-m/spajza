package sk.matej.spajza.ui.shopping

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.matej.spajza.R
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.currentLanguage
import sk.matej.spajza.ui.common.labelRes
import sk.matej.spajza.ui.common.pantryAmountText
import sk.matej.spajza.ui.common.text
import sk.matej.spajza.ui.components.EmptyState
import sk.matej.spajza.ui.components.ExpiryField
import sk.matej.spajza.ui.components.NumberField
import sk.matej.spajza.ui.components.SearchField
import sk.matej.spajza.ui.components.SuggestionRow
import java.time.LocalDate

@Composable
fun ShoppingRoute(viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.screenEvents.collect { event ->
            when (event) {
                is ShoppingEvent.Moved ->
                    snackbarHostState.showSnackbar(context.resources.getQuantityString(R.plurals.shopping_moved, event.count, event.count))
            }
        }
    }
    val shareTitle = stringResource(R.string.shopping_share_title)
    val shareText = shareText(state.rows, shareTitle)
    ShoppingScreen(
        state = state,
        query = query,
        snackbarHostState = snackbarHostState,
        actions = ShoppingActions(
            onQueryChange = viewModel::onQueryChange,
            onAddIngredient = viewModel::onAddIngredient,
            onAddText = viewModel::onAddText,
            onToggle = viewModel::onToggle,
            onRemove = viewModel::onRemove,
            onOpenBought = viewModel::onOpenBought,
            onDraftQuantity = viewModel::onDraftQuantity,
            onDraftExpiry = viewModel::onDraftExpiry,
            onConfirmBought = viewModel::onConfirmBought,
            onDismissBought = viewModel::onDismissBought,
            onShare = { share(context, shareTitle, shareText) },
        ),
    )
}

class ShoppingActions(
    val onQueryChange: (String) -> Unit,
    val onAddIngredient: (IngredientOption) -> Unit,
    val onAddText: () -> Unit,
    val onToggle: (ShoppingRow) -> Unit,
    val onRemove: (ShoppingRow) -> Unit,
    val onOpenBought: () -> Unit,
    val onDraftQuantity: (Int, String) -> Unit,
    val onDraftExpiry: (Int, LocalDate?) -> Unit,
    val onConfirmBought: () -> Unit,
    val onDismissBought: () -> Unit,
    val onShare: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(state: ShoppingUiState, query: String, snackbarHostState: SnackbarHostState, actions: ShoppingActions) {
    val language = currentLanguage()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_shopping)) },
                actions = {
                    if (state.rows.any { !it.item.checked }) {
                        IconButton(onClick = actions.onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.shopping_share)) }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.checkedCount > 0) {
                ExtendedFloatingActionButton(onClick = actions.onOpenBought) { Text(stringResource(R.string.shopping_bought_to_pantry)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
            ) {
                item {
                    SearchField(query, actions.onQueryChange, stringResource(R.string.shopping_search_hint), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                when {
                    query.isNotBlank() -> {
                        items(state.suggestions, key = { it.ingredientId }) { option ->
                            SuggestionRow(option.name.text(), stringResource(option.category.labelRes()), onAdd = { actions.onAddIngredient(option) })
                        }
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.shopping_add_text, query.trim())) },
                                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                                modifier = Modifier.clickable(onClick = actions.onAddText),
                            )
                        }
                    }
                    state.rows.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Filled.ShoppingCart,
                            title = stringResource(R.string.shopping_empty_title),
                            body = stringResource(R.string.shopping_empty_body),
                        )
                    }
                    else -> shoppingGroups(state.rows, language, actions)
                }
            }
        }
    }
    state.bought?.let { drafts -> BoughtDialog(drafts, actions) }
}

private fun LazyListScope.shoppingGroups(rows: List<ShoppingRow>, language: Language, actions: ShoppingActions) {
    // Catalog categories in aisle order, free-text items last.
    val groups = rows.groupBy { it.category }.toList().sortedBy { (category, _) -> category?.ordinal ?: Int.MAX_VALUE }
    groups.forEach { (category, groupRows) ->
        item(key = "header-${category ?: "text"}") {
            Text(
                category?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.shopping_free_text),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        val ordered = groupRows.sortedWith(compareBy<ShoppingRow> { it.item.checked }.thenBy { rowTitle(it, language) })
        items(ordered, key = { it.item.id }) { row -> ShoppingItemRow(row, actions) }
    }
}

private fun rowTitle(row: ShoppingRow, language: Language): String = row.name?.get(language) ?: row.item.text.orEmpty()

@Composable
private fun ShoppingItemRow(row: ShoppingRow, actions: ShoppingActions) {
    val language = currentLanguage()
    val checked = row.item.checked
    ListItem(
        modifier = Modifier.clickable { actions.onToggle(row) },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = { actions.onToggle(row) }) },
        headlineContent = {
            Text(
                rowTitle(row, language),
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            val amount = row.item.amount
            val unit = row.unit
            val parts = buildList {
                if (amount != null && unit != null) add(pantryAmountText(amount, unit))
                if (row.reasons.isNotEmpty()) add(stringResource(R.string.shopping_for_recipes, row.reasons.joinToString { it[language] }))
                row.addedByName?.let { add(it) }
            }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        },
        trailingContent = {
            IconButton(onClick = { actions.onRemove(row) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.shopping_remove)) }
        },
    )
}

@Composable
private fun BoughtDialog(drafts: List<BoughtDraft>, actions: ShoppingActions) {
    val language = currentLanguage()
    AlertDialog(
        onDismissRequest = actions.onDismissBought,
        title = { Text(stringResource(R.string.shopping_bought_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.shopping_bought_body), style = MaterialTheme.typography.bodySmall)
                drafts.forEachIndexed { index, draft ->
                    HorizontalDivider()
                    Text(rowTitle(draft.row, language), style = MaterialTheme.typography.titleSmall)
                    val unit = draft.row.unit
                    if (unit != null) {
                        NumberField(
                            value = draft.quantityText,
                            onValueChange = { actions.onDraftQuantity(index, it) },
                            label = stringResource(R.string.pantry_editor_quantity, stringResource(unit.labelRes())),
                        )
                        ExpiryField(draft.expiresAt, { actions.onDraftExpiry(index, it) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = actions.onConfirmBought) { Text(stringResource(R.string.shopping_bought_confirm)) } },
        dismissButton = { TextButton(onClick = actions.onDismissBought) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The unchecked items as plain text for sharing, grouped like the list. */
@Composable
private fun shareText(rows: List<ShoppingRow>, title: String): String {
    val language = currentLanguage()
    val lines = rows.filter { !it.item.checked }
        .sortedWith(compareBy<ShoppingRow> { it.category?.ordinal ?: Int.MAX_VALUE }.thenBy { rowTitle(it, language) })
        .map { row ->
            val amount = row.item.amount
            val unit = row.unit
            val amountText = if (amount != null && unit != null) " – ${pantryAmountText(amount, unit)}" else ""
            "• ${rowTitle(row, language)}$amountText"
        }
    return (listOf("$title:") + lines).joinToString("\n")
}

private fun share(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
}
