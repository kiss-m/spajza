package sk.matej.spajza.ui.receipt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.receipt.ReceiptParser
import sk.matej.spajza.domain.receipt.TextReader
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.IngredientOptions
import sk.matej.spajza.ui.navigation.ReceiptDestination
import sk.matej.spajza.ui.pantry.PantryNotices
import javax.inject.Inject

enum class ReceiptProblem { UNREADABLE, NO_TEXT, NO_ITEMS }

data class ReceiptUiState(
    val isLoading: Boolean = true,
    val problem: ReceiptProblem? = null,
    val rows: List<ReceiptRow> = emptyList(),
    /** The row open in the edit dialog. */
    val editing: ReceiptRow? = null,
    val saving: Boolean = false,
) {
    val selectedCount: Int get() = ReceiptAdditions.of(rows).size
}

/** Reads a receipt photo on the phone and lets the user confirm what goes into the pantry. */
@HiltViewModel
class ReceiptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val home: HomeData,
    private val reader: TextReader,
    private val pantry: PantryRepository,
    private val notices: PantryNotices,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<ReceiptDestination>()
    private val mutableState = MutableStateFlow(ReceiptUiState())
    val state: StateFlow<ReceiptUiState> = mutableState.asStateFlow()

    private val doneEvents = Channel<Unit>(Channel.CONFLATED)

    /** Emits once the items are in the pantry; the screen then closes. */
    val done: Flow<Unit> = doneEvents.receiveAsFlow()

    private var options: Pair<Catalog, IngredientOptions>? = null

    init {
        viewModelScope.launch { read() }
    }

    private suspend fun read() {
        val catalog = home.catalog.filterNotNull().first()
        val lines = reader.read(args.imageUri).getOrElse {
            mutableState.value = ReceiptUiState(isLoading = false, problem = ReceiptProblem.UNREADABLE)
            return
        }
        val items = withContext(Dispatchers.Default) { ReceiptParser(catalog).parse(lines) }
        val problem = when {
            lines.isEmpty() -> ReceiptProblem.NO_TEXT
            items.isEmpty() -> ReceiptProblem.NO_ITEMS
            else -> null
        }
        mutableState.value = ReceiptUiState(isLoading = false, problem = problem, rows = ReceiptRow.of(items))
    }

    /** Ticks or unticks a row; a row without an ingredient opens the dialog to pick one. */
    fun onToggle(row: ReceiptRow) {
        if (row.option == null) onEdit(row) else replace(row.copy(checked = !row.checked))
    }

    fun onEdit(row: ReceiptRow) = mutableState.update { it.copy(editing = row) }

    fun onEditDismiss() = mutableState.update { it.copy(editing = null) }

    fun onEditSave(row: ReceiptRow) {
        replace(row)
        onEditDismiss()
    }

    fun searchIngredients(query: String, exclude: Set<String>): List<IngredientOption> {
        val catalog = home.catalog.value ?: return emptyList()
        val cached = options?.takeIf { it.first === catalog }?.second ?: IngredientOptions(catalog).also { options = catalog to it }
        return cached.search(query, exclude)
    }

    fun onAdd() {
        val current = mutableState.value
        val session = home.session.value ?: return
        val additions = ReceiptAdditions.of(current.rows)
        if (current.saving || additions.isEmpty()) return
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            additions.forEach { addition ->
                pantry.add(session.householdId, IngredientRef.Catalog(addition.ingredientId), addition.quantity, expiresAt = null, addedBy = session.uid)
            }
            notices.announceAddedMany(additions.size)
            doneEvents.send(Unit)
        }
    }

    private fun replace(row: ReceiptRow) = mutableState.update { state ->
        state.copy(rows = state.rows.map { if (it.id == row.id) row else it })
    }
}
