package sk.matej.spajza.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.NumberFormat
import sk.matej.spajza.ui.common.currentLanguage
import sk.matej.spajza.ui.common.labelRes
import sk.matej.spajza.ui.components.ChipGroup
import sk.matej.spajza.ui.components.ExpiryField
import sk.matej.spajza.ui.components.FormSection
import sk.matej.spajza.ui.components.NumberField
import sk.matej.spajza.ui.profile.IngredientLookup
import sk.matej.spajza.ui.profile.IngredientPicker
import java.time.LocalDate

@Composable
fun ProductRoute(onBack: () -> Unit, onDone: () -> Unit, viewModel: ProductViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = currentLanguage()
    LaunchedEffect(viewModel) { viewModel.start(language) }
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    ProductScreen(
        state = state,
        onBack = onBack,
        actions = ProductActions(
            onChange = viewModel::onChange,
            onShowDetails = viewModel::onShowDetails,
            onCategory = viewModel::onCategory,
            onUnit = viewModel::onUnit,
            onExpiry = viewModel::onExpiry,
            onLink = viewModel::onLink,
            lookup = viewModel::searchIngredients,
            onSave = viewModel::onSave,
        ),
    )
}

class ProductActions(
    val onChange: ((ProductForm) -> ProductForm) -> Unit,
    val onShowDetails: () -> Unit,
    val onCategory: (IngredientCategory) -> Unit,
    val onUnit: (PantryUnit) -> Unit,
    val onExpiry: (LocalDate?) -> Unit,
    val onLink: (IngredientOption?) -> Unit,
    val lookup: IngredientLookup,
    val onSave: () -> Unit,
)

/** A scanned or typed-in food: what it is, its label values and the amount going into the pantry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(state: ProductUiState, onBack: () -> Unit, actions: ProductActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.status == ProductStatus.MANUAL) R.string.product_title_new else R.string.product_title_scanned)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val form = state.form
        if (state.isLoading || form == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.product_loading), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StatusCard(state.status, form)
                if (state.showDetails) {
                    ProductDetails(form, actions)
                } else {
                    OutlinedButton(onClick = actions.onShowDetails) { Text(stringResource(R.string.product_edit_details)) }
                }
                FormSection(stringResource(R.string.product_pantry_title)) {
                    NumberField(
                        value = form.quantityText,
                        onValueChange = { text -> actions.onChange { it.copy(quantityText = text) } },
                        label = stringResource(R.string.pantry_editor_quantity, stringResource(form.unit.labelRes())),
                    )
                    ExpiryField(form.expiresAt, actions.onExpiry)
                }
                Button(onClick = actions.onSave, enabled = form.canSave && !state.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.product_save))
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: ProductStatus, form: ProductForm) {
    val message = when (status) {
        ProductStatus.KNOWN -> R.string.product_status_known
        ProductStatus.FOUND -> R.string.product_status_found
        ProductStatus.UNKNOWN -> R.string.product_status_unknown
        ProductStatus.OFFLINE -> R.string.product_status_offline
        ProductStatus.MANUAL -> R.string.product_status_manual
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (status == ProductStatus.KNOWN) Text(form.base.displayName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
            form.base.barcode?.let { Text(stringResource(R.string.product_barcode, it), style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun ProductDetails(form: ProductForm, actions: ProductActions) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(form.name, { text -> actions.onChange { it.copy(name = text) } }, stringResource(R.string.product_name))
            TextInput(form.brand, { text -> actions.onChange { it.copy(brand = text) } }, stringResource(R.string.product_brand))
            CategoryRow(form.category, actions.onCategory)
        }
        FormSection(stringResource(R.string.product_link_title)) {
            Text(stringResource(R.string.product_link_body), style = MaterialTheme.typography.bodySmall)
            IngredientPicker(
                title = null,
                selected = listOfNotNull(form.link),
                lookup = actions.lookup,
                exclude = setOfNotNull(form.link?.ingredientId),
                onAdd = { actions.onLink(it) },
                onRemove = { actions.onLink(null) },
                placeholder = stringResource(R.string.product_link_hint),
            )
        }
        FormSection(stringResource(R.string.product_unit)) {
            ChipGroup(PantryUnit.entries, setOf(form.unit), { stringResource(it.labelRes()) }, actions.onUnit)
            val unit = stringResource(form.unit.labelRes())
            NumberField(form.packageText, { text -> actions.onChange { it.copy(packageText = text) } }, stringResource(R.string.product_package, unit))
            if (form.unit == PantryUnit.PCS) {
                NumberField(form.pieceText, { text -> actions.onChange { it.copy(pieceText = text) } }, stringResource(R.string.product_piece_weight))
            }
        }
        NutritionFields(form, actions)
    }
}

@Composable
private fun NutritionFields(form: ProductForm, actions: ProductActions) {
    val title = if (form.unit == PantryUnit.ML) R.string.product_nutrition_title_ml else R.string.product_nutrition_title
    FormSection(stringResource(title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(form.protein, { text -> actions.onChange { it.copy(protein = text) } }, stringResource(R.string.product_protein), Modifier.weight(1f))
            NumberField(form.fat, { text -> actions.onChange { it.copy(fat = text) } }, stringResource(R.string.product_fat), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(form.carbs, { text -> actions.onChange { it.copy(carbs = text) } }, stringResource(R.string.product_carbs), Modifier.weight(1f))
            NumberField(form.sugar, { text -> actions.onChange { it.copy(sugar = text) } }, stringResource(R.string.product_sugar), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(form.fiber, { text -> actions.onChange { it.copy(fiber = text) } }, stringResource(R.string.product_fiber), Modifier.weight(1f))
            NumberField(form.salt, { text -> actions.onChange { it.copy(salt = text) } }, stringResource(R.string.product_salt), Modifier.weight(1f))
        }
        val kcal = form.kcal
        Text(
            if (kcal == null) {
                stringResource(R.string.product_energy_missing)
            } else {
                stringResource(R.string.product_energy, NumberFormat.whole(kcal))
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.product_energy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryRow(category: IngredientCategory, onPick: (IngredientCategory) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${stringResource(R.string.product_category)}: ${stringResource(category.labelRes())}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.product_change)) }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.product_category)) },
            text = {
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    ChipGroup(IngredientCategory.entries, setOf(category), { stringResource(it.labelRes()) }, { picked ->
                        onPick(picked)
                        picking = false
                    })
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TextInput(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}
