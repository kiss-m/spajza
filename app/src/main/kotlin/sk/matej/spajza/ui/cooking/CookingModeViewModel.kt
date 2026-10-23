package sk.matej.spajza.ui.cooking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.RecipeStep
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.unit.MeasureUnit
import sk.matej.spajza.domain.usecase.CookUndo
import sk.matej.spajza.ui.navigation.CookingDestination
import sk.matej.spajza.ui.recipe.RecipeCooking
import javax.inject.Inject

data class StepTimer(val totalSeconds: Int, val remainingSeconds: Int = totalSeconds, val running: Boolean = false) {
    val finished: Boolean get() = remainingSeconds == 0
}

data class CookingLine(val name: LocalizedText, val amount: Double, val unit: MeasureUnit, val optional: Boolean)

data class CookingUiState(
    val isLoading: Boolean = true,
    val title: LocalizedText? = null,
    val steps: List<RecipeStep> = emptyList(),
    val ingredients: List<CookingLine> = emptyList(),
    val stepIndex: Int = 0,
    val timers: Map<Int, StepTimer> = emptyMap(),
    val cooked: Boolean = false,
)

sealed interface CookingEvent {
    data object TimerFinished : CookingEvent

    data class Cooked(val pantryChanges: Int) : CookingEvent

    data object CookUndone : CookingEvent
}

@HiltViewModel
class CookingModeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    home: HomeData,
    private val cooking: RecipeCooking,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<CookingDestination>()
    private val progress = MutableStateFlow(Progress())
    private val tickers = mutableMapOf<Int, Job>()
    private var lastUndo: CookUndo? = null

    private val events = Channel<CookingEvent>(Channel.BUFFERED)
    val screenEvents: Flow<CookingEvent> = events.receiveAsFlow()

    private data class Progress(val stepIndex: Int = 0, val timers: Map<Int, StepTimer> = emptyMap(), val cooked: Boolean = false)

    // The pantry stays collected so "Uvarené" deducts from its current state.
    val state: StateFlow<CookingUiState> = combine(home.catalog, home.pantry, progress) { catalog, _, current ->
        if (catalog == null) return@combine CookingUiState()
        val recipe = catalog.recipesById[route.recipeId] ?: return@combine CookingUiState(isLoading = false)
        val factor = route.servings.toDouble() / recipe.servings
        val timers = recipe.steps.withIndex()
            .mapNotNull { (index, step) -> step.timerSeconds?.let { index to (current.timers[index] ?: StepTimer(it)) } }
            .toMap()
        CookingUiState(
            isLoading = false,
            title = recipe.title,
            steps = recipe.steps,
            ingredients = recipe.ingredients.map { line ->
                CookingLine(catalog.ingredient(line.ingredientId).name, line.amount * factor, line.unit, line.optional)
            },
            stepIndex = current.stepIndex.coerceIn(0, (recipe.steps.size - 1).coerceAtLeast(0)),
            timers = timers,
            cooked = current.cooked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CookingUiState())

    fun onNext() = progress.update { it.copy(stepIndex = (it.stepIndex + 1).coerceAtMost(state.value.steps.lastIndex.coerceAtLeast(0))) }

    fun onPrevious() = progress.update { it.copy(stepIndex = (it.stepIndex - 1).coerceAtLeast(0)) }

    fun onStartTimer(index: Int) {
        val timer = state.value.timers[index] ?: return
        val start = if (timer.finished) timer.copy(remainingSeconds = timer.totalSeconds) else timer
        setTimer(index, start.copy(running = true))
        tickers[index]?.cancel()
        tickers[index] = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val current = progress.value.timers[index] ?: break
                if (!current.running) break
                val next = current.copy(remainingSeconds = (current.remainingSeconds - 1).coerceAtLeast(0))
                setTimer(index, if (next.finished) next.copy(running = false) else next)
                if (next.finished) {
                    events.send(CookingEvent.TimerFinished)
                    break
                }
            }
        }
    }

    fun onPauseTimer(index: Int) {
        tickers.remove(index)?.cancel()
        val timer = state.value.timers[index] ?: return
        setTimer(index, timer.copy(running = false))
    }

    fun onResetTimer(index: Int) {
        tickers.remove(index)?.cancel()
        val timer = state.value.timers[index] ?: return
        setTimer(index, StepTimer(timer.totalSeconds))
    }

    private fun setTimer(index: Int, timer: StepTimer) = progress.update { it.copy(timers = it.timers + (index to timer)) }

    fun onCooked() {
        if (progress.value.cooked) return
        viewModelScope.launch {
            val undo = cooking.cook(route.recipeId, route.servings) ?: return@launch
            lastUndo = undo
            progress.update { it.copy(cooked = true) }
            events.send(CookingEvent.Cooked(undo.pantryChanges))
        }
    }

    fun onUndoCooked() {
        val undo = lastUndo ?: return
        lastUndo = null
        viewModelScope.launch {
            undo.undo()
            progress.update { it.copy(cooked = false) }
            events.send(CookingEvent.CookUndone)
        }
    }
}
