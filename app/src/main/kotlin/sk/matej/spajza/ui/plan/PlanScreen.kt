package sk.matej.spajza.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.matej.spajza.R
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.plan.PlanMeal
import sk.matej.spajza.ui.common.currentLocale
import sk.matej.spajza.ui.common.labelRes
import sk.matej.spajza.ui.common.text
import sk.matej.spajza.ui.components.EmptyState
import sk.matej.spajza.ui.components.RecipePlaceholder
import sk.matej.spajza.ui.components.Stepper
import java.time.LocalDate

@Composable
fun PlanRoute(
    onOpenRecipe: (recipeId: String, servings: Int) -> Unit,
    onAddForDay: (LocalDate) -> Unit,
    onOpenShopping: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)
    val openShopping by rememberUpdatedState(onOpenShopping)
    LaunchedEffect(viewModel) {
        viewModel.screenEvents.collect { event ->
            when (event) {
                is PlanEvent.AddedToShopping -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getQuantityString(R.plurals.plan_added_to_shopping, event.count, event.count),
                        actionLabel = resources.getString(R.string.plan_open_shopping),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) openShopping()
                }
                PlanEvent.NothingToBuy -> snackbarHostState.showSnackbar(resources.getString(R.string.plan_nothing_to_buy))
                is PlanEvent.Removed -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.plan_removed),
                        actionLabel = resources.getString(R.string.action_undo),
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove(event.entry)
                }
            }
        }
    }
    PlanScreen(
        state = state,
        today = viewModel.today(),
        snackbarHostState = snackbarHostState,
        actions = PlanActions(
            onPreviousWeek = viewModel::onPreviousWeek,
            onNextWeek = viewModel::onNextWeek,
            onThisWeek = viewModel::onThisWeek,
            onShop = viewModel::onShop,
            onAddForDay = onAddForDay,
            onOpen = { entry -> onOpenRecipe(entry.recipeId, entry.servings) },
            onServingsChange = viewModel::onServingsChange,
            onMove = viewModel::onMove,
            onRemove = viewModel::onRemove,
        ),
    )
}

class PlanActions(
    val onPreviousWeek: () -> Unit,
    val onNextWeek: () -> Unit,
    val onThisWeek: () -> Unit,
    val onShop: () -> Unit,
    val onAddForDay: (LocalDate) -> Unit,
    val onOpen: (PlanEntry) -> Unit,
    val onServingsChange: (PlanEntry, Int) -> Unit,
    val onMove: (PlanEntry, LocalDate, PlanMeal) -> Unit,
    val onRemove: (PlanEntry) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(state: PlanUiState, today: LocalDate, snackbarHostState: SnackbarHostState, actions: PlanActions) {
    var moving by remember { mutableStateOf<PlanEntry?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_plan)) },
                actions = {
                    TextButton(onClick = actions.onShop, enabled = state.canShop) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.plan_to_shopping))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val week = state.week
        if (state.isLoading || week == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "week") {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    WeekNavigator(week, today, onPrevious = actions.onPreviousWeek, onNext = actions.onNextWeek)
                    if (state.relativeWeek != 0) {
                        TextButton(onClick = actions.onThisWeek, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(stringResource(R.string.plan_go_this_week))
                        }
                    }
                }
            }
            if (state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.DateRange,
                        title = stringResource(R.string.plan_empty_title),
                        body = stringResource(R.string.plan_empty_body),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date}") { DayHeader(day, onAdd = { actions.onAddForDay(day.date) }) }
                items(day.entries, key = { it.entry.id }) { model ->
                    PlanEntryCard(
                        model = model,
                        onOpen = { actions.onOpen(model.entry) },
                        onServingsChange = { actions.onServingsChange(model.entry, it) },
                        onMove = { moving = model.entry },
                        onRemove = { actions.onRemove(model.entry) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
    moving?.let { entry ->
        MoveEntryDialog(
            entry = entry,
            today = today,
            onDismiss = { moving = null },
            onMove = { date, meal ->
                moving = null
                actions.onMove(entry, date, meal)
            },
        )
    }
}

@Composable
private fun DayHeader(day: PlanDayModel, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            PlanDates.dayTitle(day.date, currentLocale()),
            style = MaterialTheme.typography.titleMedium,
            color = if (day.isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (day.isToday) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    stringResource(R.string.plan_today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (!day.isPast) {
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.plan_add_for_day))
            }
        }
    }
}

@Composable
private fun PlanEntryCard(
    model: PlanEntryModel,
    onOpen: () -> Unit,
    onServingsChange: (Int) -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = model.title != null
    Card(
        onClick = onOpen,
        enabled = available,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            val cuisine = model.cuisine
            if (cuisine != null) {
                RecipePlaceholder(cuisine, Modifier.size(56.dp), iconSize = 24.dp)
            } else {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(56.dp)) {}
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(model.entry.meal.labelRes()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    model.title?.text() ?: stringResource(R.string.plan_recipe_gone),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                model.kcalPerServing?.let { kcal ->
                    val status = if (model.missing == 0) {
                        stringResource(R.string.plan_have_all)
                    } else {
                        pluralStringResource(R.plurals.cook_status_missing, model.missing, model.missing)
                    }
                    Text(
                        "${stringResource(R.string.plan_kcal_per_serving, kcal)} · $status",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (model.missing == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            EntryMenu(canMove = available, onMove = onMove, onRemove = onRemove)
        }
        if (available) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.plan_servings), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Stepper(
                    value = model.entry.servings,
                    onValueChange = onServingsChange,
                    range = PlanEntry.MIN_SERVINGS..PlanEntry.MAX_SERVINGS,
                    decreaseLabel = stringResource(R.string.detail_servings_decrease),
                    increaseLabel = stringResource(R.string.detail_servings_increase),
                )
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun EntryMenu(canMove: Boolean, onMove: () -> Unit, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.plan_entry_menu)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canMove) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_move)) },
                    onClick = {
                        expanded = false
                        onMove()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}
