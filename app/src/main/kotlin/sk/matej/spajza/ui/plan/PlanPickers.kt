package sk.matej.spajza.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sk.matej.spajza.R
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.plan.PlanMeal
import sk.matej.spajza.domain.plan.WeekId
import sk.matej.spajza.ui.common.currentLocale
import sk.matej.spajza.ui.common.labelRes
import sk.matej.spajza.ui.components.Stepper
import java.time.LocalDate

/** "Tento týždeň", "Budúci týždeň", "47. týždeň" … */
@Composable
fun weekName(week: WeekId, today: LocalDate): String {
    val current = WeekId.of(today)
    return when (week) {
        current -> stringResource(R.string.plan_this_week)
        current.next() -> stringResource(R.string.plan_next_week)
        current.previous() -> stringResource(R.string.plan_last_week)
        else -> stringResource(R.string.plan_week_number, week.week)
    }
}

/** ‹ week name and dates › */
@Composable
fun WeekNavigator(
    week: WeekId,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canGoBack: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.plan_previous_week_action))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(weekName(week, today), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                PlanDates.weekRange(week, currentLocale()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.plan_next_week_action))
        }
    }
}

/** The days of [week] as chips; past days cannot be picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChips(week: WeekId, selected: Set<LocalDate>, today: LocalDate, onToggle: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val english = locale.language == "en"
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        week.days.forEach { date ->
            FilterChip(
                selected = date in selected,
                onClick = { onToggle(date) },
                enabled = !date.isBefore(today),
                label = { Text("${PlanDates.shortDay(date, locale)} ${date.dayOfMonth}${if (english) "" else "."}") },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealChips(selected: PlanMeal, onSelect: (PlanMeal) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PlanMeal.entries.forEach { meal ->
            FilterChip(selected = meal == selected, onClick = { onSelect(meal) }, label = { Text(stringResource(meal.labelRes())) })
        }
    }
}

/**
 * "Pridať do plánu": days (several for meal prep, also in later weeks), the meal and servings for
 * the recipe called [recipeTitle].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlanSheet(
    recipeTitle: String,
    initial: AddToPlanDraft,
    today: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (AddToPlanDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.plan_add_title), style = MaterialTheme.typography.titleLarge)
            Text(recipeTitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WeekNavigator(
                week = draft.week,
                today = today,
                onPrevious = { draft = draft.showWeek(draft.week.previous()) },
                onNext = { draft = draft.showWeek(draft.week.next()) },
                canGoBack = draft.week > WeekId.of(today),
            )
            Text(stringResource(R.string.plan_add_days), style = MaterialTheme.typography.titleSmall)
            DayChips(draft.week, draft.dates, today, onToggle = { draft = draft.toggle(it) })
            Text(stringResource(R.string.plan_add_meal), style = MaterialTheme.typography.titleSmall)
            MealChips(draft.meal, onSelect = { draft = draft.copy(meal = it) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.plan_servings), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Stepper(
                    value = draft.servings,
                    onValueChange = { draft = draft.withServings(it) },
                    range = PlanEntry.MIN_SERVINGS..PlanEntry.MAX_SERVINGS,
                    decreaseLabel = stringResource(R.string.detail_servings_decrease),
                    increaseLabel = stringResource(R.string.detail_servings_increase),
                )
            }
            Button(onClick = { onAdd(draft) }, enabled = draft.dates.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                val days = draft.dates.size
                Text(if (days <= 1) stringResource(R.string.plan_add_title) else pluralStringResource(R.plurals.plan_add_action_days, days, days))
            }
        }
    }
}

/** Moving a planned meal to another day (this or a later week) or meal. */
@Composable
fun MoveEntryDialog(entry: PlanEntry, today: LocalDate, onDismiss: () -> Unit, onMove: (LocalDate, PlanMeal) -> Unit) {
    val start = maxOf(entry.date, today)
    var week by remember { mutableStateOf(WeekId.of(start)) }
    var date by remember { mutableStateOf(start) }
    var meal by remember { mutableStateOf(entry.meal) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_move_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WeekNavigator(
                    week = week,
                    today = today,
                    onPrevious = { week = week.previous() },
                    onNext = { week = week.next() },
                    canGoBack = week > WeekId.of(today),
                )
                DayChips(week, setOf(date), today, onToggle = { date = it })
                MealChips(meal, onSelect = { meal = it })
            }
        },
        confirmButton = { TextButton(onClick = { onMove(date, meal) }) { Text(stringResource(R.string.plan_move)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
