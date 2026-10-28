package sk.matej.spajza.ui.ownrecipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.matej.spajza.R
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.own.PhotoLineKind
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.amountText
import sk.matej.spajza.ui.common.labelRes
import sk.matej.spajza.ui.common.text
import sk.matej.spajza.ui.components.ChipGroup
import sk.matej.spajza.ui.components.FormSection
import sk.matej.spajza.ui.components.NumberField
import sk.matej.spajza.ui.components.SearchField
import sk.matej.spajza.ui.components.Stepper
import sk.matej.spajza.ui.components.SuggestionRow
import sk.matej.spajza.ui.profile.IngredientLookup

/** [onDone] gets the saved recipe's id and whether it was new. */
@Composable
fun RecipeEditorRoute(onBack: () -> Unit, onDone: (recipeId: String, isNew: Boolean) -> Unit, viewModel: RecipeEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone(it, viewModel.state.value.isNew) } }
    RecipeEditorScreen(
        state = state,
        onBack = onBack,
        actions = EditorActions(
            onChange = viewModel::onChange,
            onToggleMeal = viewModel::onToggleMeal,
            onPick = viewModel::onPick,
            onPicked = viewModel::onPicked,
            onPickDismiss = viewModel::onPickDismiss,
            lookup = viewModel::searchIngredients,
            onPhotoKind = viewModel::onPhotoKind,
            onPhotoDone = viewModel::onPhotoDone,
            onRetryImport = viewModel::onRetryImport,
            onSave = viewModel::onSave,
        ),
    )
}

class EditorActions(
    val onChange: ((RecipeForm) -> RecipeForm) -> Unit,
    val onToggleMeal: (MealType) -> Unit,
    val onPick: (String) -> Unit,
    val onPicked: (IngredientOption?) -> Unit,
    val onPickDismiss: () -> Unit,
    val lookup: IngredientLookup,
    val onPhotoKind: (Int, PhotoLineKind) -> Unit,
    val onPhotoDone: () -> Unit,
    val onRetryImport: () -> Unit,
    val onSave: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(state: RecipeEditorUiState, onBack: () -> Unit, actions: EditorActions) {
    val form = state.form
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.stage == EditorStage.FORM && form != null) {
                        TextButton(onClick = actions.onSave, enabled = form.canSave && !state.saving) { Text(stringResource(R.string.action_save)) }
                    }
                },
            )
        },
        bottomBar = {
            if (state.stage == EditorStage.PHOTO_LINES) {
                Surface(tonalElevation = 3.dp) {
                    Button(onClick = actions.onPhotoDone, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        Text(stringResource(R.string.editor_photo_done))
                    }
                }
            }
        },
    ) { padding ->
        when (state.stage) {
            EditorStage.LOADING -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.editor_loading), style = MaterialTheme.typography.bodyMedium)
            }
            EditorStage.NOT_FOUND -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.detail_not_found), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
            EditorStage.PHOTO_LINES -> PhotoLines(state, padding, actions)
            EditorStage.FORM -> if (form != null) EditorForm(state, form, padding, actions)
        }
    }
    state.picking?.let { line -> PickIngredientDialog(line, actions) }
}

@Composable
private fun PhotoLines(state: RecipeEditorUiState, padding: PaddingValues, actions: EditorActions) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(stringResource(R.string.editor_photo_intro), style = MaterialTheme.typography.bodyMedium) }
        itemsIndexed(state.photoLines) { index, line ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(line.text, style = MaterialTheme.typography.bodyMedium)
                    ChipGroup(PhotoLineKind.entries, setOf(line.kind), { stringResource(it.labelRes()) }, { kind -> actions.onPhotoKind(index, kind) })
                }
            }
        }
    }
}

@Composable
private fun EditorForm(state: RecipeEditorUiState, form: RecipeForm, padding: PaddingValues, actions: EditorActions) {
    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        state.problem?.let { ProblemCard(it, actions.onRetryImport) }
        form.sourceUrl?.let { url ->
            Text(stringResource(R.string.editor_source, hostOf(url)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { text -> actions.onChange { it.copy(title = text) } },
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { text -> actions.onChange { it.copy(description = text) } },
                label = { Text(stringResource(R.string.editor_description)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.detail_servings), style = MaterialTheme.typography.titleSmall)
                Stepper(
                    value = form.servings,
                    onValueChange = { value -> actions.onChange { it.copy(servings = value) } },
                    range = RecipeForm.MIN_SERVINGS..RecipeForm.MAX_SERVINGS,
                    decreaseLabel = stringResource(R.string.detail_servings_decrease),
                    increaseLabel = stringResource(R.string.detail_servings_increase),
                )
            }
            NumberField(
                value = form.minutesText,
                onValueChange = { text -> actions.onChange { it.copy(minutesText = text.take(4)) } },
                label = stringResource(R.string.editor_minutes),
                modifier = Modifier.weight(1f),
                decimals = false,
            )
        }
        FormSection(stringResource(R.string.editor_meals)) {
            ChipGroup(MealType.entries, form.mealTypes, { stringResource(it.labelRes()) }, actions.onToggleMeal)
        }
        FormSection(stringResource(R.string.detail_ingredients)) {
            OutlinedTextField(
                value = form.ingredientsText,
                onValueChange = { text -> actions.onChange { it.copy(ingredientsText = text) } },
                placeholder = { Text(stringResource(R.string.editor_ingredients_hint)) },
                supportingText = { Text(stringResource(R.string.editor_ingredients_help)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            state.preview.forEach { line -> PreviewRow(line, onClick = { actions.onPick(line.text) }) }
        }
        FormSection(stringResource(R.string.detail_steps)) {
            OutlinedTextField(
                value = form.stepsText,
                onValueChange = { text -> actions.onChange { it.copy(stepsText = text) } },
                placeholder = { Text(stringResource(R.string.editor_steps_hint)) },
                supportingText = { Text(stringResource(R.string.editor_steps_help)) },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = actions.onSave, enabled = form.canSave && !state.saving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.editor_save))
        }
    }
}

@Composable
private fun ProblemCard(problem: ImportProblem, onRetry: () -> Unit) {
    val message = when (problem) {
        ImportProblem.PAGE_FAILED -> R.string.editor_problem_page
        ImportProblem.NO_RECIPE_ON_PAGE -> R.string.editor_problem_no_recipe
        ImportProblem.PHOTO_FAILED -> R.string.editor_problem_photo
        ImportProblem.NO_TEXT_ON_PHOTO -> R.string.editor_problem_no_text
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
            if (problem == ImportProblem.PAGE_FAILED) {
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.editor_retry)) }
            }
        }
    }
}

/** A typed line and what the app counts it as; tapping picks the ingredient by hand. */
@Composable
private fun PreviewRow(line: LinePreview, onClick: () -> Unit) {
    val option = line.option
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.text, style = MaterialTheme.typography.bodyMedium)
            val detail = when {
                option == null -> stringResource(R.string.editor_line_unmatched)
                !line.counted -> stringResource(R.string.editor_line_unweighed, option.name.text())
                else -> option.name.text()
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (option == null || !line.counted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val amount = line.amount
        val unit = line.unit
        if (option != null && line.counted && amount != null && unit != null) {
            Text(amountText(amount, unit), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PickIngredientDialog(line: String, actions: EditorActions) {
    var query by rememberSaveable(line) { mutableStateOf("") }
    val suggestions = remember(query) { if (query.isBlank()) emptyList() else actions.lookup(query, emptySet()).take(SUGGESTIONS) }
    AlertDialog(
        onDismissRequest = actions.onPickDismiss,
        title = {
            Column {
                Text(stringResource(R.string.editor_pick_title), style = MaterialTheme.typography.labelLarge)
                Text(line)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, { query = it }, stringResource(R.string.product_link_hint))
                suggestions.forEach { option ->
                    SuggestionRow(option.name.text(), stringResource(option.category.labelRes()), onAdd = { actions.onPicked(option) })
                }
            }
        },
        confirmButton = { TextButton(onClick = actions.onPickDismiss) { Text(stringResource(R.string.action_cancel)) } },
        dismissButton = { TextButton(onClick = { actions.onPicked(null) }) { Text(stringResource(R.string.editor_pick_none)) } },
    )
}

/** "example.com" from a page address. */
internal fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').removePrefix("www.")

private const val SUGGESTIONS = 5
