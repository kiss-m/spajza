package sk.matej.spajza.ui.cook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.matej.spajza.R
import sk.matej.spajza.domain.browse.RecipeType
import sk.matej.spajza.domain.model.Cuisine
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.ui.common.currentLanguage
import sk.matej.spajza.ui.common.labelRes
import sk.matej.spajza.ui.components.SearchField
import sk.matej.spajza.ui.components.SectionHeader

@Composable
fun CookRoute(onOpenRecipe: (String) -> Unit, onOpenPantry: () -> Unit, viewModel: CookViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val addedMessage = stringResource(R.string.cook_added_to_shopping)
    LaunchedEffect(viewModel) {
        viewModel.screenEvents.collect { event ->
            when (event) {
                CookScreenEvent.AddedToShopping -> snackbarHostState.showSnackbar(addedMessage)
            }
        }
    }
    CookScreen(
        state = state,
        query = query,
        snackbarHostState = snackbarHostState,
        actions = CookActions(
            onQueryChange = viewModel::onQueryChange,
            onToggleCuisine = viewModel::onToggleCuisine,
            onToggleType = viewModel::onToggleType,
            onMealType = viewModel::onMealType,
            onClearFilters = viewModel::onClearFilters,
            onAddPurchase = viewModel::onAddPurchase,
            onOpenRecipe = onOpenRecipe,
            onOpenPantry = onOpenPantry,
        ),
    )
}

class CookActions(
    val onQueryChange: (String) -> Unit,
    val onToggleCuisine: (Cuisine) -> Unit,
    val onToggleType: (RecipeType) -> Unit,
    val onMealType: (MealType?) -> Unit,
    val onClearFilters: () -> Unit,
    val onAddPurchase: () -> Unit,
    val onOpenRecipe: (String) -> Unit,
    val onOpenPantry: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookScreen(state: CookUiState, query: String, snackbarHostState: SnackbarHostState, actions: CookActions) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_cook)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SearchField(query, actions.onQueryChange, stringResource(R.string.cook_search_hint), Modifier.padding(horizontal = 16.dp))
            }
            item { FilterRows(state, actions) }
            when {
                state.isLoading -> item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.catalog_loading), modifier = Modifier.padding(top = 12.dp))
                    }
                }
                state.searchResults != null -> searchResults(state.searchResults, actions)
                else -> sections(state, actions)
            }
        }
    }
}

@Composable
private fun FilterRows(state: CookUiState, actions: CookActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.filter.mealType == null,
                    onClick = { actions.onMealType(null) },
                    label = { Text(stringResource(R.string.meal_all)) },
                )
            }
            items(MealType.entries) { meal ->
                FilterChip(
                    selected = state.filter.mealType == meal,
                    onClick = { actions.onMealType(if (state.filter.mealType == meal) null else meal) },
                    label = { Text(stringResource(meal.labelRes())) },
                )
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RecipeType.entries) { type ->
                FilterChip(
                    selected = type in state.filter.types,
                    onClick = { actions.onToggleType(type) },
                    label = { Text(stringResource(type.labelRes())) },
                )
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.cuisines) { cuisine ->
                FilterChip(
                    selected = cuisine in state.filter.cuisines,
                    onClick = { actions.onToggleCuisine(cuisine) },
                    label = { Text(stringResource(cuisine.labelRes())) },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchResults(results: List<RecipeCardModel>, actions: CookActions) {
    item { SectionHeader(stringResource(R.string.cook_search_results), Modifier.padding(horizontal = 16.dp)) }
    if (results.isEmpty()) {
        item { HintText(stringResource(R.string.cook_no_results)) }
    }
    items(results, key = { it.recipeId }) { model ->
        RecipeListItem(model, onClick = { actions.onOpenRecipe(model.recipeId) }, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sections(state: CookUiState, actions: CookActions) {
    if (state.pantryEmpty) {
        item { EmptyPantryCard(actions.onOpenPantry) }
    }
    if (!state.filter.isEmpty && state.ready.isEmpty() && state.toBuy.isEmpty()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                HintText(stringResource(R.string.cook_filtered_empty), Modifier)
                TextButton(onClick = actions.onClearFilters) { Text(stringResource(R.string.cook_clear_filters)) }
            }
        }
        return
    }
    item { SectionHeader(stringResource(R.string.cook_section_ready), Modifier.padding(horizontal = 16.dp)) }
    item { RecipeRow(state.ready, stringResource(R.string.cook_ready_empty), actions) }
    state.purchase?.let { purchase -> item { PurchaseCard(purchase, actions.onAddPurchase) } }
    item { SectionHeader(stringResource(R.string.cook_section_to_buy), Modifier.padding(horizontal = 16.dp)) }
    item { RecipeRow(state.toBuy, stringResource(R.string.cook_to_buy_empty), actions) }
}

@Composable
private fun RecipeRow(recipes: List<RecipeCardModel>, emptyText: String, actions: CookActions) {
    if (recipes.isEmpty()) {
        HintText(emptyText)
    } else {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recipes, key = { it.recipeId }) { model -> RecipeCard(model, onClick = { actions.onOpenRecipe(model.recipeId) }) }
        }
    }
}

@Composable
private fun PurchaseCard(purchase: PurchaseModel, onAdd: () -> Unit) {
    val language = currentLanguage()
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.cook_purchase_title), style = MaterialTheme.typography.titleMedium)
            purchase.items.forEach { item ->
                Text("• ${item.name[language]} (${stringResource(item.priceTier.labelRes())})", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(R.string.cook_purchase_unlocks, purchase.unlockedIds.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                purchase.unlockedTitles.joinToString { it[language] },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 3,
            )
            Button(onClick = onAdd) { Text(stringResource(R.string.cook_purchase_add)) }
        }
    }
}

@Composable
private fun EmptyPantryCard(onOpenPantry: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cook_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.cook_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onOpenPantry) { Text(stringResource(R.string.cook_open_pantry)) }
        }
    }
}

@Composable
private fun HintText(text: String, modifier: Modifier = Modifier.padding(horizontal = 16.dp)) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
