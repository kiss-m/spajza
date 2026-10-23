package sk.matej.spajza.ui.cooking

import android.content.Context
import android.media.RingtoneManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.matej.spajza.R
import sk.matej.spajza.ui.common.NumberFormat
import sk.matej.spajza.ui.common.amountText
import sk.matej.spajza.ui.common.text

@Composable
fun CookingModeRoute(onExit: () -> Unit, viewModel: CookingModeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources by rememberUpdatedState(LocalResources.current)
    val haptics = LocalHapticFeedback.current
    KeepScreenOn()
    LaunchedEffect(viewModel) {
        viewModel.screenEvents.collect { event ->
            when (event) {
                CookingEvent.TimerFinished -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    playAlert(context)
                    snackbarHostState.showSnackbar(resources.getString(R.string.cooking_timer_done))
                }
                is CookingEvent.Cooked -> {
                    val message = if (event.pantryChanges == 0) {
                        resources.getString(R.string.detail_cooked_logged)
                    } else {
                        resources.getQuantityString(R.plurals.detail_cooked_deducted, event.pantryChanges, event.pantryChanges)
                    }
                    val result = snackbarHostState.showSnackbar(message, resources.getString(R.string.action_undo), duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoCooked()
                }
                CookingEvent.CookUndone -> snackbarHostState.showSnackbar(resources.getString(R.string.detail_cooked_undone))
            }
        }
    }
    CookingModeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onExit = onExit,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onStartTimer = viewModel::onStartTimer,
        onPauseTimer = viewModel::onPauseTimer,
        onResetTimer = viewModel::onResetTimer,
        onCooked = viewModel::onCooked,
    )
}

/** Cooking mode keeps the display on while it is shown. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private fun playAlert(context: Context) {
    runCatching {
        RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingModeScreen(
    state: CookingUiState,
    snackbarHostState: SnackbarHostState,
    onExit: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onPauseTimer: (Int) -> Unit,
    onResetTimer: (Int) -> Unit,
    onCooked: () -> Unit,
) {
    val isLast = state.steps.isEmpty() || state.stepIndex == state.steps.lastIndex
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title?.text().orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onExit) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cooking_exit)) }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onPrevious, enabled = state.stepIndex > 0, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cooking_previous))
                    }
                    when {
                        !isLast -> Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cooking_next)) }
                        !state.cooked -> Button(onClick = onCooked, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.detail_cooked)) }
                        else -> Button(onClick = onExit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cooking_exit)) }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            StepContent(state, padding, onStartTimer, onPauseTimer, onResetTimer)
        }
    }
}

@Composable
private fun StepContent(
    state: CookingUiState,
    padding: PaddingValues,
    onStartTimer: (Int) -> Unit,
    onPauseTimer: (Int) -> Unit,
    onResetTimer: (Int) -> Unit,
) {
    var showIngredients by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val total = state.steps.size.coerceAtLeast(1)
        Text(stringResource(R.string.cooking_step, state.stepIndex + 1, total), style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { (state.stepIndex + 1f) / total }, modifier = Modifier.fillMaxWidth())
        state.steps.getOrNull(state.stepIndex)?.let { step ->
            Text(step.text.text(), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 36.sp))
        }
        state.timers[state.stepIndex]?.let { timer ->
            TimerCard(
                timer = timer,
                onStart = { onStartTimer(state.stepIndex) },
                onPause = { onPauseTimer(state.stepIndex) },
                onReset = { onResetTimer(state.stepIndex) },
            )
        }
        TextButton(onClick = { showIngredients = !showIngredients }) { Text(stringResource(R.string.cooking_ingredients)) }
        if (showIngredients) {
            state.ingredients.forEach { line ->
                Row(Modifier.fillMaxWidth()) {
                    Text(line.name.text(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(amountText(line.amount, line.unit), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun TimerCard(timer: StepTimer, onStart: () -> Unit, onPause: () -> Unit, onReset: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (timer.finished) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(NumberFormat.clock(timer.remainingSeconds), style = MaterialTheme.typography.displayMedium)
            if (timer.finished) Text(stringResource(R.string.cooking_timer_done), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    timer.running -> FilledTonalButton(onClick = onPause) { Text(stringResource(R.string.cooking_timer_pause)) }
                    timer.finished -> FilledTonalButton(onClick = onReset) { Text(stringResource(R.string.cooking_timer_reset)) }
                    timer.remainingSeconds < timer.totalSeconds -> {
                        FilledTonalButton(onClick = onStart) { Text(stringResource(R.string.cooking_timer_resume)) }
                        OutlinedButton(onClick = onReset) { Text(stringResource(R.string.cooking_timer_reset)) }
                    }
                    else -> Button(onClick = onStart) {
                        Text(stringResource(R.string.cooking_timer_start, NumberFormat.clock(timer.totalSeconds)))
                    }
                }
            }
        }
    }
}
