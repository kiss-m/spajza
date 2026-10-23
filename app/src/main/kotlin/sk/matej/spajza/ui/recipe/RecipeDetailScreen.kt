package sk.matej.spajza.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.matej.spajza.R
import sk.matej.spajza.domain.matching.LineStatus
import sk.matej.spajza.domain.rating.Verdict
import sk.matej.spajza.ui.common.amountText
import sk.matej.spajza.ui.common.currentLanguage
import sk.matej.spajza.ui.common.difficultyLabelRes
import sk.matej.spajza.ui.common.durationText
import sk.matej.spajza.ui.common.text
import sk.matej.spajza.ui.components.RecipePlaceholder
import sk.matej.spajza.ui.components.SectionHeader
import sk.matej.spajza.ui.components.SpajzaIcons
import sk.matej.spajza.ui.components.Stepper
import kotlin.math.roundToInt

@Composable
fun RecipeDetailRoute(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onStartCooking: (recipeId: String, servings: Int) -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.screenEvents.collect { event ->
            when (event) {
                is DetailEvent.AddedToShopping -> snackbarHostState.showSnackbar(resources.getString(R.string.cook_added_to_shopping))
                is DetailEvent.Cooked -> {
                    val message = if (event.pantryChanges == 0) {
                        resources.getString(R.string.detail_cooked_logged)
                    } else {
                        resources.getQuantityString(R.plurals.detail_cooked_deducted, event.pantryChanges, event.pantryChanges)
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoCooked()
                }
                DetailEvent.CookUndone -> snackbarHostState.showSnackbar(resources.getString(R.string.detail_cooked_undone))
            }
        }
    }
    RecipeDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = DetailActions(
            onBack = onBack,
            onOpenRecipe = onOpenRecipe,
            onStartCooking = { state.recipe?.let { onStartCooking(it.id, state.servings) } },
            onServingsChange = viewModel::onServingsChange,
            onToggleFavorite = viewModel::onToggleFavorite,
            onVerdict = viewModel::onVerdict,
            onAddMissing = viewModel::onAddMissing,
            onCooked = viewModel::onCooked,
        ),
    )
}

class DetailActions(
    val onBack: () -> Unit,
    val onOpenRecipe: (String) -> Unit,
    val onStartCooking: () -> Unit,
    val onServingsChange: (Int) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onVerdict: (Verdict) -> Unit,
    val onAddMissing: () -> Unit,
    val onCooked: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(state: RecipeDetailUiState, snackbarHostState: SnackbarHostState, actions: DetailActions) {
    val recipe = state.recipe
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.title?.text().orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (recipe != null) {
                        IconButton(onClick = actions.onToggleFavorite) {
                            Icon(
                                if (state.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(R.string.detail_favorite),
                                tint = if (state.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (recipe != null) {
                BottomAppBar {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = actions.onStartCooking, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.detail_start_cooking))
                        }
                        Button(onClick = actions.onCooked, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.detail_cooked)) }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.notFound || recipe == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_not_found))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { RecipePlaceholder(recipe.cuisine, Modifier.fillMaxWidth().height(160.dp), iconSize = 56.dp) }
                item { Header(state) }
                item { NutritionCard(state) }
                item { ServingsAndRating(state, actions) }
                item {
                    SectionHeader(stringResource(R.string.detail_ingredients), Modifier.padding(horizontal = 16.dp)) {
                        if (state.toBuyCount > 0) {
                            TextButton(onClick = actions.onAddMissing) { Text(stringResource(R.string.detail_add_missing)) }
                        }
                    }
                }
                // No keys: a recipe may list one ingredient twice (dough and filling).
                items(state.lines) { line -> IngredientRow(line) }
                if (state.addOns.isNotEmpty() || state.pairs.isNotEmpty()) {
                    item { GoesWell(state, actions.onOpenRecipe) }
                }
                item { SectionHeader(stringResource(R.string.detail_steps), Modifier.padding(horizontal = 16.dp)) }
                itemsIndexed(recipe.steps) { index, step ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${index + 1}.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(step.text.text(), style = MaterialTheme.typography.bodyLarge)
                            step.timerSeconds?.let { seconds ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(SpajzaIcons.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Text(durationText((seconds + 59) / 60), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: RecipeDetailUiState) {
    val recipe = state.recipe ?: return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(recipe.title.text(), style = MaterialTheme.typography.headlineSmall)
        Text(recipe.description.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.detail_time, durationText(recipe.totalTimeMin), durationText(recipe.activeTimeMin)) +
                " · " + stringResource(difficultyLabelRes(recipe.difficulty)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NutritionCard(state: RecipeDetailUiState) {
    val n = state.perServing
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_per_serving), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.kcal_value, n.kcal.roundToInt()), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Macro(stringResource(R.string.macro_protein), n.protein)
                Macro(stringResource(R.string.macro_carbs), n.carbs)
                Macro(stringResource(R.string.macro_fat), n.fat)
                Macro(stringResource(R.string.macro_fiber), n.fiber)
            }
        }
    }
}

@Composable
private fun Macro(label: String, grams: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.macro_grams, grams.roundToInt()), style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ServingsAndRating(state: RecipeDetailUiState, actions: DetailActions) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.detail_servings), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Stepper(
                value = state.servings,
                onValueChange = actions.onServingsChange,
                range = RecipeDetailViewModel.MIN_SERVINGS..RecipeDetailViewModel.MAX_SERVINGS,
                decreaseLabel = stringResource(R.string.detail_servings_decrease),
                increaseLabel = stringResource(R.string.detail_servings_increase),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.verdict == Verdict.LIKE,
                onClick = { actions.onVerdict(Verdict.LIKE) },
                label = { Text(stringResource(R.string.detail_like)) },
                leadingIcon = { Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = state.verdict == Verdict.DISLIKE,
                onClick = { actions.onVerdict(Verdict.DISLIKE) },
                label = { Text(stringResource(R.string.detail_dislike)) },
                leadingIcon = { Icon(SpajzaIcons.ThumbDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun IngredientRow(line: DetailLine) {
    val language = currentLanguage()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusPill(line.status)
        Column(Modifier.weight(1f)) {
            val optional = if (line.optional) " (${stringResource(R.string.detail_optional)})" else ""
            Text(line.name.text() + optional, style = MaterialTheme.typography.bodyLarge)
            line.note?.let { Text(it.text(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (line.status == LineStatus.SUBSTITUTE && line.substituteName != null) {
                Text(
                    stringResource(R.string.detail_status_substitute, line.substituteName[language]),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (line.alternatives.isNotEmpty()) {
                Text(
                    stringResource(R.string.detail_or_use, line.alternatives.joinToString { it[language] }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(amountText(line.amount, line.unit), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StatusPill(status: LineStatus) {
    val (label, container) = when (status) {
        LineStatus.HAVE -> R.string.detail_status_have to MaterialTheme.colorScheme.tertiaryContainer
        LineStatus.STAPLE -> R.string.detail_status_staple to MaterialTheme.colorScheme.surfaceContainerHighest
        LineStatus.PARTIAL -> R.string.detail_status_partial to MaterialTheme.colorScheme.secondaryContainer
        LineStatus.SUBSTITUTE -> R.string.detail_status_have to MaterialTheme.colorScheme.secondaryContainer
        LineStatus.MISSING -> R.string.detail_status_missing to MaterialTheme.colorScheme.errorContainer
    }
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = Modifier.width(76.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun GoesWell(state: RecipeDetailUiState, onOpenRecipe: (String) -> Unit) {
    val language = currentLanguage()
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.detail_goes_well), style = MaterialTheme.typography.titleSmall)
            state.addOns.forEach { name -> Text(stringResource(R.string.detail_addon_have, name[language]), style = MaterialTheme.typography.bodyMedium) }
            state.pairs.forEach { pair ->
                TextButton(onClick = { onOpenRecipe(pair.recipeId) }) { Text(stringResource(R.string.detail_pairs_with, pair.title[language])) }
            }
            Spacer(Modifier.height(0.dp))
        }
    }
}
