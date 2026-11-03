package sk.matej.spajza.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.matej.spajza.R
import sk.matej.spajza.ui.common.currentLocale
import sk.matej.spajza.ui.common.text
import sk.matej.spajza.ui.components.SearchField
import sk.matej.spajza.ui.cook.CookUiState
import sk.matej.spajza.ui.cook.RecipeCardModel
import sk.matej.spajza.ui.cook.RecipeListItem
import java.time.LocalDate

@Composable
fun PlanPickerRoute(onBack: () -> Unit, onAdded: () -> Unit, viewModel: PlanPickerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<Pair<RecipeCardModel, AddToPlanDraft>?>(null) }
    val added by rememberUpdatedState(onAdded)
    LaunchedEffect(viewModel) { viewModel.added.collect { added() } }
    PlanPickerScreen(
        state = state,
        query = query,
        date = viewModel.date,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onPick = { card -> viewModel.draftFor(card.recipeId)?.let { picked = card to it } },
    )
    picked?.let { (card, draft) ->
        AddToPlanSheet(
            recipeTitle = card.title.text(),
            initial = draft,
            today = viewModel.today(),
            onDismiss = { picked = null },
            onAdd = { result ->
                picked = null
                viewModel.onAdd(card.recipeId, result)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanPickerScreen(
    state: CookUiState,
    query: String,
    date: LocalDate,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onPick: (RecipeCardModel) -> Unit,
) {
    val locale = currentLocale()
    val day = PlanDates.dayTitle(date, locale).let { title ->
        if (locale.language == "en") title else title.replaceFirstChar { it.lowercase(locale) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.plan_pick_title))
                        Text(
                            stringResource(R.string.plan_pick_for_day, day),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.cook_search_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val results = state.searchResults
                if (results != null) {
                    if (results.isEmpty()) item(key = "none") { Hint(stringResource(R.string.cook_no_results)) }
                    items(results, key = { "s-${it.recipeId}" }) { card -> RecipeListItem(card, onClick = { onPick(card) }) }
                } else {
                    item(key = "ready") { SectionTitle(stringResource(R.string.cook_section_ready)) }
                    if (state.ready.isEmpty()) item(key = "ready-empty") { Hint(stringResource(R.string.cook_ready_empty)) }
                    items(state.ready, key = { "r-${it.recipeId}" }) { card -> RecipeListItem(card, onClick = { onPick(card) }) }
                    item(key = "to-buy") { SectionTitle(stringResource(R.string.cook_section_to_buy)) }
                    if (state.toBuy.isEmpty()) item(key = "to-buy-empty") { Hint(stringResource(R.string.cook_to_buy_empty)) }
                    items(state.toBuy, key = { "b-${it.recipeId}" }) { card -> RecipeListItem(card, onClick = { onPick(card) }) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
