package sk.matej.spajza.ui.pantry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sk.matej.spajza.R
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.ui.common.Expiry
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.currentLanguage
import sk.matej.spajza.ui.common.labelRes
import sk.matej.spajza.ui.common.pantryAmountText
import sk.matej.spajza.ui.common.text
import sk.matej.spajza.ui.components.EmptyState
import sk.matej.spajza.ui.components.ExpiryBadge
import sk.matej.spajza.ui.components.ExpiryField
import sk.matej.spajza.ui.components.NumberField
import sk.matej.spajza.ui.components.PhotoSourceDialog
import sk.matej.spajza.ui.components.SearchField
import sk.matej.spajza.ui.components.SpajzaIcons
import sk.matej.spajza.ui.components.SuggestionRow
import sk.matej.spajza.ui.components.rememberPhotoPicker
import java.time.LocalDate

@Composable
fun PantryRoute(
    onOpenProduct: (barcode: String?, name: String?) -> Unit,
    onOpenReceipt: (imageUri: String) -> Unit,
    viewModel: PantryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)
    val language by rememberUpdatedState(currentLanguage())
    val scope = rememberCoroutineScope()
    val photos = rememberPhotoPicker(
        onPicked = { onOpenReceipt(it.toString()) },
        onNoCamera = { scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.receipt_no_camera)) } },
    )
    val scanner = rememberBarcodeScanner(
        onScanned = { onOpenProduct(it, null) },
        onFailed = { scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.scan_failed)) } },
    )
    var chooseReceiptSource by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.screenEvents.collect { event ->
            when (event) {
                is PantryEvent.Added -> snackbarHostState.showSnackbar(resources.getString(R.string.pantry_added, event.name[language]))
                is PantryEvent.AddedMany -> snackbarHostState.showSnackbar(resources.getQuantityString(R.plurals.pantry_added_many, event.count, event.count))
                is PantryEvent.Removed -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.pantry_removed, event.name[language]),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete(event.item)
                }
            }
        }
    }
    PantryScreen(
        state = state,
        query = query,
        snackbarHostState = snackbarHostState,
        actions = PantryActions(
            onQueryChange = viewModel::onQueryChange,
            onPickSuggestion = viewModel::onPickSuggestion,
            onQuickCategory = viewModel::onQuickCategory,
            onQuickAdd = viewModel::onQuickAdd,
            onEditRow = viewModel::onEditRow,
            onDelete = viewModel::onDelete,
            onEditorQuantity = viewModel::onEditorQuantity,
            onEditorExpiry = viewModel::onEditorExpiry,
            onEditorSave = viewModel::onEditorSave,
            onEditorDismiss = viewModel::onEditorDismiss,
            onPickCustom = viewModel::onPickCustom,
            onCreateCustom = { name -> onOpenProduct(null, name) },
            onScan = scanner,
            onReceipt = { chooseReceiptSource = true },
        ),
    )
    if (chooseReceiptSource) {
        PhotoSourceDialog(
            title = stringResource(R.string.receipt_source_title),
            body = stringResource(R.string.receipt_source_body),
            onCamera = {
                chooseReceiptSource = false
                photos.takePhoto()
            },
            onGallery = {
                chooseReceiptSource = false
                photos.pickPhoto()
            },
            onDismiss = { chooseReceiptSource = false },
        )
    }
}

class PantryActions(
    val onQueryChange: (String) -> Unit,
    val onPickSuggestion: (PantrySuggestion) -> Unit,
    val onQuickCategory: (IngredientCategory) -> Unit,
    val onQuickAdd: (IngredientOption) -> Unit,
    val onEditRow: (PantryRow) -> Unit,
    val onDelete: (PantryRow) -> Unit,
    val onEditorQuantity: (String) -> Unit,
    val onEditorExpiry: (LocalDate?) -> Unit,
    val onEditorSave: () -> Unit,
    val onEditorDismiss: () -> Unit,
    val onPickCustom: (CustomSuggestion) -> Unit,
    val onCreateCustom: (String) -> Unit,
    val onScan: () -> Unit,
    val onReceipt: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(state: PantryUiState, query: String, snackbarHostState: SnackbarHostState, actions: PantryActions) {
    val language = currentLanguage()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_pantry)) },
                actions = {
                    IconButton(onClick = actions.onScan) { Icon(SpajzaIcons.Barcode, contentDescription = stringResource(R.string.scan_action)) }
                    IconButton(onClick = actions.onReceipt) { Icon(SpajzaIcons.Receipt, contentDescription = stringResource(R.string.receipt_action)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            ) {
                item {
                    SearchField(query, actions.onQueryChange, stringResource(R.string.pantry_search_hint), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                if (query.isNotBlank()) {
                    items(state.suggestions, key = { it.option.ingredientId }) { suggestion ->
                        val detail = when {
                            suggestion.isStaple -> stringResource(R.string.detail_status_staple)
                            suggestion.inPantry -> stringResource(R.string.pantry_in_pantry)
                            else -> stringResource(suggestion.option.category.labelRes())
                        }
                        SuggestionRow(suggestion.option.name.text(), detail, onAdd = { actions.onPickSuggestion(suggestion) })
                    }
                    items(state.customSuggestions, key = { "custom-${it.id}" }) { suggestion ->
                        val detail = stringResource(if (suggestion.inPantry) R.string.pantry_in_pantry else R.string.pantry_own_food)
                        SuggestionRow(suggestion.name, detail, onAdd = { actions.onPickCustom(suggestion) })
                    }
                    item(key = "create-custom") {
                        TextButton(onClick = { actions.onCreateCustom(query.trim()) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(stringResource(R.string.pantry_create_custom, query.trim()))
                        }
                    }
                } else {
                    item { QuickAdd(state, actions) }
                    if (state.rows.isEmpty()) {
                        item {
                            EmptyState(
                                icon = SpajzaIcons.Kitchen,
                                title = stringResource(R.string.pantry_empty_title),
                                body = stringResource(R.string.pantry_empty_body) + "\n\n" + stringResource(R.string.pantry_staples_note),
                            )
                        }
                    } else {
                        pantryGroups(state, language, actions)
                    }
                }
            }
        }
    }
    state.editor?.let { editor -> PantryEditorDialog(editor, actions) }
}

private fun LazyListScope.pantryGroups(state: PantryUiState, language: sk.matej.spajza.domain.model.Language, actions: PantryActions) {
    state.rows.groupBy { it.category }.toSortedMap().forEach { (category, rows) ->
        item(key = "header-$category") {
            Text(
                stringResource(category.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        items(rows.sortedBy { it.name[language] }, key = { it.item.id }) { row ->
            PantryItemRow(row, state.today, onClick = { actions.onEditRow(row) }, onDelete = { actions.onDelete(row) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAdd(state: PantryUiState, actions: PantryActions) {
    val language = currentLanguage()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.pantry_quick_add), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.quickCategories) { category ->
                FilterChip(
                    selected = state.quickCategory == category,
                    onClick = { actions.onQuickCategory(category) },
                    label = { Text(stringResource(category.labelRes())) },
                )
            }
        }
        if (state.quickOptions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.quickOptions.sortedBy { it.name[language] }.forEach { option ->
                    AssistChip(onClick = { actions.onQuickAdd(option) }, label = { Text(option.name[language]) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantryItemRow(row: PantryRow, today: LocalDate, onClick: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            headlineContent = { Text(row.name.text()) },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.item.quantity?.let { Text(pantryAmountText(it, row.unit), style = MaterialTheme.typography.bodyMedium) }
                    Expiry.of(row.item.expiresAt, today)?.let { ExpiryBadge(it) }
                    row.addedByName?.let { name ->
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            trailingContent = {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.pantry_delete)) }
            },
        )
    }
}

@Composable
private fun PantryEditorDialog(editor: PantryEditor, actions: PantryActions) {
    AlertDialog(
        onDismissRequest = actions.onEditorDismiss,
        title = {
            Column {
                Text(stringResource(if (editor.existing == null) R.string.pantry_editor_add_title else R.string.pantry_editor_edit_title), style = MaterialTheme.typography.labelLarge)
                Text(editor.target.name.text())
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = editor.quantityText,
                    onValueChange = actions.onEditorQuantity,
                    label = stringResource(R.string.pantry_editor_quantity, stringResource(editor.target.unit.labelRes())),
                )
                ExpiryField(editor.expiresAt, actions.onEditorExpiry)
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onEditorSave) {
                Text(stringResource(if (editor.existing == null) R.string.action_add else R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = actions.onEditorDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
