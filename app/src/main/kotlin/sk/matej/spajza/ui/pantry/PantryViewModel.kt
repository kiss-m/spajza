package sk.matej.spajza.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.pantry.IngredientRef
import sk.matej.spajza.domain.pantry.PantryChange
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.IngredientOptions
import sk.matej.spajza.ui.common.quantityInput
import sk.matej.spajza.ui.common.toOption
import sk.matej.spajza.ui.common.parseNumber
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class PantryRow(
    val item: PantryItem,
    val name: LocalizedText,
    val category: IngredientCategory,
    val unit: PantryUnit,
    val addedByName: String?,
)

data class PantrySuggestion(val option: IngredientOption, val inPantry: Boolean, val isStaple: Boolean)

/** The add/edit dialog; [existing] is null when adding. */
data class PantryEditor(
    val option: IngredientOption,
    val existing: PantryItem?,
    val quantityText: String,
    val expiresAt: LocalDate?,
)

data class PantryUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val rows: List<PantryRow> = emptyList(),
    val suggestions: List<PantrySuggestion> = emptyList(),
    val quickCategories: List<IngredientCategory> = emptyList(),
    val quickCategory: IngredientCategory? = null,
    val quickOptions: List<IngredientOption> = emptyList(),
    val editor: PantryEditor? = null,
)

sealed interface PantryEvent {
    data class Added(val name: LocalizedText) : PantryEvent

    data class Removed(val item: PantryItem, val name: LocalizedText) : PantryEvent
}

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val home: HomeData,
    private val pantry: PantryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val quickCategory = MutableStateFlow<IngredientCategory?>(null)
    private val editor = MutableStateFlow<PantryEditor?>(null)
    private var options: Pair<Catalog, IngredientOptions>? = null

    private val events = Channel<PantryEvent>(Channel.BUFFERED)
    val screenEvents: Flow<PantryEvent> = events.receiveAsFlow()

    private val data = combine(home.catalog, home.session, home.pantry) { catalog, session, items -> Triple(catalog, session, items) }

    val state: StateFlow<PantryUiState> = combine(data, _query, quickCategory, editor) { (catalog, session, items), query, category, openEditor ->
        if (catalog == null || session == null || items == null) return@combine PantryUiState(editor = openEditor)
        val inPantry = items.mapNotNull { it.catalogId }.toSet()
        val staples = session.household.staples
        val rows = items.mapNotNull { item ->
            val ingredient = item.catalogId?.let { catalog.ingredientsById[it] } ?: return@mapNotNull null
            PantryRow(item, ingredient.name, ingredient.category, ingredient.pantryUnit, item.addedBy?.let { session.household.memberNames[it] })
        }
        val pickable = catalog.ingredients.filter { it.id !in staples }
        PantryUiState(
            isLoading = false,
            today = LocalDate.now(clock),
            rows = rows,
            suggestions = if (query.isBlank()) {
                emptyList()
            } else {
                optionsFor(catalog).search(query).map { PantrySuggestion(it, it.ingredientId in inPantry, it.ingredientId in staples) }
            },
            quickCategories = pickable.map { it.category }.distinct().sorted(),
            quickCategory = category,
            quickOptions = if (category == null) emptyList() else pickable.filter { it.category == category && it.id !in inPantry }.map { it.toOption() },
            editor = openEditor,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryUiState())

    private fun optionsFor(catalog: Catalog): IngredientOptions {
        val cached = options
        if (cached != null && cached.first === catalog) return cached.second
        return IngredientOptions(catalog).also { options = catalog to it }
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onQuickCategory(category: IngredientCategory) = quickCategory.update { if (it == category) null else category }

    /** A chip adds the ingredient right away, without an amount. */
    fun onQuickAdd(option: IngredientOption) {
        val session = home.session.value ?: return
        viewModelScope.launch {
            pantry.add(session.householdId, IngredientRef.Catalog(option.ingredientId), quantity = null, expiresAt = null, addedBy = session.uid)
            events.send(PantryEvent.Added(option.name))
        }
    }

    fun onPickSuggestion(suggestion: PantrySuggestion) {
        val existing = home.pantry.value?.firstOrNull { it.catalogId == suggestion.option.ingredientId }
        editor.value = PantryEditor(suggestion.option, existing, quantityInput(existing?.quantity), existing?.expiresAt)
    }

    fun onEditRow(row: PantryRow) {
        val option = IngredientOption(row.item.catalogId ?: return, row.name, row.category, row.unit)
        editor.value = PantryEditor(option, row.item, quantityInput(row.item.quantity), row.item.expiresAt)
    }

    fun onEditorQuantity(text: String) = editor.update { it?.copy(quantityText = text) }

    fun onEditorExpiry(date: LocalDate?) = editor.update { it?.copy(expiresAt = date) }

    fun onEditorDismiss() {
        editor.value = null
    }

    fun onEditorSave() {
        val current = editor.value ?: return
        val session = home.session.value ?: return
        editor.value = null
        val quantity = parseNumber(current.quantityText)?.takeIf { it > 0 }
        viewModelScope.launch {
            val existing = current.existing
            if (existing == null) {
                pantry.add(session.householdId, IngredientRef.Catalog(current.option.ingredientId), quantity, current.expiresAt, session.uid)
                _query.value = ""
                events.send(PantryEvent.Added(current.option.name))
            } else {
                if (quantity != existing.quantity) pantry.setQuantity(session.householdId, existing.id, existing.quantity, quantity)
                if (current.expiresAt != existing.expiresAt) pantry.setExpiry(session.householdId, existing.id, current.expiresAt)
            }
        }
    }

    fun onDelete(row: PantryRow) {
        val session = home.session.value ?: return
        viewModelScope.launch {
            pantry.remove(session.householdId, row.item.id)
            events.send(PantryEvent.Removed(row.item, row.name))
        }
    }

    fun onUndoDelete(item: PantryItem) {
        val session = home.session.value ?: return
        viewModelScope.launch { pantry.apply(session.householdId, listOf(PantryChange.Restore(item))) }
    }
}
