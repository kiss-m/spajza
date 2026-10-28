package sk.matej.spajza.ui.receipt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.matej.spajza.R
import sk.matej.spajza.ui.common.labelRes
import sk.matej.spajza.ui.common.pantryAmountText
import sk.matej.spajza.ui.common.text
import sk.matej.spajza.ui.components.EmptyState
import sk.matej.spajza.ui.components.NumberField
import sk.matej.spajza.ui.components.SearchField
import sk.matej.spajza.ui.components.SpajzaIcons
import sk.matej.spajza.ui.components.SuggestionRow
import sk.matej.spajza.ui.profile.IngredientLookup

@Composable
fun ReceiptRoute(onBack: () -> Unit, onDone: () -> Unit, viewModel: ReceiptViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    ReceiptScreen(
        state = state,
        onBack = onBack,
        actions = ReceiptActions(
            onToggle = viewModel::onToggle,
            onEdit = viewModel::onEdit,
            onEditSave = viewModel::onEditSave,
            onEditDismiss = viewModel::onEditDismiss,
            lookup = viewModel::searchIngredients,
            onAdd = viewModel::onAdd,
        ),
    )
}

class ReceiptActions(
    val onToggle: (ReceiptRow) -> Unit,
    val onEdit: (ReceiptRow) -> Unit,
    val onEditSave: (ReceiptRow) -> Unit,
    val onEditDismiss: () -> Unit,
    val lookup: IngredientLookup,
    val onAdd: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(state: ReceiptUiState, onBack: () -> Unit, actions: ReceiptActions) {
    val reviewing = !state.isLoading && state.problem == null
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receipt_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            if (reviewing) {
                Surface(tonalElevation = 3.dp) {
                    val count = state.selectedCount
                    Button(
                        onClick = actions.onAdd,
                        enabled = count > 0 && !state.saving,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) {
                        Text(pluralStringResource(R.plurals.receipt_add, count, count))
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.receipt_loading), style = MaterialTheme.typography.bodyMedium)
            }
            state.problem != null -> {
                val (title, body) = when (state.problem) {
                    ReceiptProblem.UNREADABLE -> R.string.receipt_unreadable_title to R.string.receipt_unreadable_body
                    ReceiptProblem.NO_TEXT -> R.string.receipt_no_text_title to R.string.receipt_no_text_body
                    ReceiptProblem.NO_ITEMS -> R.string.receipt_no_items_title to R.string.receipt_no_items_body
                }
                EmptyState(
                    icon = SpajzaIcons.Receipt,
                    title = stringResource(title),
                    body = stringResource(body),
                    modifier = Modifier.padding(padding),
                    action = { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.receipt_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.rows, key = { it.id }) { row -> ReceiptRowItem(row, actions) }
            }
        }
    }
    state.editing?.let { row -> ReceiptRowDialog(row, actions.lookup, actions.onEditSave, actions.onEditDismiss) }
}

@Composable
private fun ReceiptRowItem(row: ReceiptRow, actions: ReceiptActions) {
    val option = row.option
    ListItem(
        modifier = Modifier.clickable { actions.onEdit(row) },
        leadingContent = { Checkbox(checked = row.canAdd, onCheckedChange = { actions.onToggle(row) }) },
        headlineContent = {
            Text(
                option?.name?.text() ?: stringResource(R.string.receipt_unmatched),
                color = if (option == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            val amount = row.quantity?.let { quantity -> option?.let { pantryAmountText(quantity, it.pantryUnit) } }
            Text(listOfNotNull(row.text, amount).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        },
    )
}

/** Picks the ingredient a receipt row is and how much of it there is. */
@Composable
private fun ReceiptRowDialog(row: ReceiptRow, lookup: IngredientLookup, onSave: (ReceiptRow) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(row.id) { mutableStateOf(row) }
    var query by rememberSaveable(row.id) { mutableStateOf("") }
    val suggestions = remember(query) { if (query.isBlank()) emptyList() else lookup(query, emptySet()).take(SUGGESTIONS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.receipt_row_title), style = MaterialTheme.typography.labelLarge)
                Text(row.text)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val option = draft.option
                Text(
                    "${stringResource(R.string.receipt_row_ingredient)}: ${option?.name?.text() ?: stringResource(R.string.receipt_unmatched)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                SearchField(query, { query = it }, stringResource(R.string.product_link_hint))
                suggestions.forEach { suggestion ->
                    SuggestionRow(suggestion.name.text(), stringResource(suggestion.category.labelRes()), onAdd = {
                        draft = draft.matchedTo(suggestion)
                        query = ""
                    })
                }
                if (option != null) {
                    NumberField(
                        value = draft.quantityText,
                        onValueChange = { text -> draft = draft.copy(quantityText = text) },
                        label = stringResource(R.string.pantry_editor_quantity, stringResource(option.pantryUnit.labelRes())),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = draft.option != null) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val SUGGESTIONS = 5
