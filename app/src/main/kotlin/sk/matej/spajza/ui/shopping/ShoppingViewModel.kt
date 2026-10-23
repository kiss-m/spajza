package sk.matej.spajza.ui.shopping

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
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.repository.ShoppingRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.usecase.BoughtItem
import sk.matej.spajza.domain.usecase.MoveBoughtToPantryUseCase
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.IngredientOptions
import sk.matej.spajza.ui.common.quantityInput
import sk.matej.spajza.ui.common.parseNumber
import java.time.LocalDate
import javax.inject.Inject

data class ShoppingRow(
    val item: ShoppingItem,
    /** Null for free-text items. */
    val name: LocalizedText?,
    val category: IngredientCategory?,
    val unit: PantryUnit?,
    val reasons: List<LocalizedText>,
    val addedByName: String?,
)

/** A checked item in the "Kúpené → do špajze" dialog. */
data class BoughtDraft(val row: ShoppingRow, val quantityText: String, val expiresAt: LocalDate?)

data class ShoppingUiState(
    val isLoading: Boolean = true,
    val rows: List<ShoppingRow> = emptyList(),
    val suggestions: List<IngredientOption> = emptyList(),
    val bought: List<BoughtDraft>? = null,
) {
    val checkedCount: Int get() = rows.count { it.item.checked }
}

sealed interface ShoppingEvent {
    data class Moved(val count: Int) : ShoppingEvent
}

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val home: HomeData,
    private val shopping: ShoppingRepository,
    pantry: PantryRepository,
) : ViewModel() {
    private val moveToPantry = MoveBoughtToPantryUseCase(shopping, pantry)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val bought = MutableStateFlow<List<BoughtDraft>?>(null)
    private var options: Pair<Catalog, IngredientOptions>? = null

    private val events = Channel<ShoppingEvent>(Channel.BUFFERED)
    val screenEvents: Flow<ShoppingEvent> = events.receiveAsFlow()

    private val data = combine(home.catalog, home.session, home.shopping) { catalog, session, items -> Triple(catalog, session, items) }

    val state: StateFlow<ShoppingUiState> = combine(data, _query, bought) { (catalog, session, items), query, drafts ->
        if (catalog == null || session == null || items == null) return@combine ShoppingUiState(bought = drafts)
        val rows = items.map { item ->
            val ingredient = (item.ingredient as? IngredientRef.Catalog)?.let { catalog.ingredientsById[it.id] }
            ShoppingRow(
                item = item,
                name = ingredient?.name,
                category = ingredient?.category,
                unit = ingredient?.pantryUnit,
                reasons = item.reasonRecipeIds.mapNotNull { catalog.recipesById[it]?.title },
                addedByName = item.addedBy?.let { session.household.memberNames[it] },
            )
        }
        val listed = items.mapNotNull { (it.ingredient as? IngredientRef.Catalog)?.id }.toSet()
        ShoppingUiState(
            isLoading = false,
            rows = rows,
            suggestions = if (query.isBlank()) emptyList() else optionsFor(catalog).search(query, exclude = listed),
            bought = drafts,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    private fun optionsFor(catalog: Catalog): IngredientOptions {
        val cached = options
        if (cached != null && cached.first === catalog) return cached.second
        return IngredientOptions(catalog).also { options = catalog to it }
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onAddIngredient(option: IngredientOption) {
        val session = home.session.value ?: return
        _query.value = ""
        viewModelScope.launch {
            shopping.addIngredient(session.householdId, IngredientRef.Catalog(option.ingredientId), null, emptySet(), session.uid)
        }
    }

    /** Adds what was typed as a free-text item (anything outside the catalog, e.g. baking paper). */
    fun onAddText() {
        val session = home.session.value ?: return
        val text = _query.value.trim()
        if (text.isEmpty()) return
        _query.value = ""
        viewModelScope.launch { shopping.addText(session.householdId, text, session.uid) }
    }

    fun onToggle(row: ShoppingRow) {
        val session = home.session.value ?: return
        viewModelScope.launch { shopping.setChecked(session.householdId, row.item.id, !row.item.checked) }
    }

    fun onRemove(row: ShoppingRow) {
        val session = home.session.value ?: return
        viewModelScope.launch { shopping.remove(session.householdId, listOf(row.item.id)) }
    }

    fun onOpenBought() {
        bought.value = state.value.rows.filter { it.item.checked }.map { BoughtDraft(it, quantityInput(it.item.amount), null) }
    }

    fun onDraftQuantity(index: Int, text: String) = updateDraft(index) { it.copy(quantityText = text) }

    fun onDraftExpiry(index: Int, date: LocalDate?) = updateDraft(index) { it.copy(expiresAt = date) }

    private fun updateDraft(index: Int, change: (BoughtDraft) -> BoughtDraft) = bought.update { drafts ->
        drafts?.mapIndexed { i, draft -> if (i == index) change(draft) else draft }
    }

    fun onDismissBought() {
        bought.value = null
    }

    fun onConfirmBought() {
        val drafts = bought.value ?: return
        val session = home.session.value ?: return
        bought.value = null
        viewModelScope.launch {
            moveToPantry(
                householdId = session.householdId,
                bought = drafts.map { BoughtItem(it.row.item, parseNumber(it.quantityText)?.takeIf { q -> q > 0 }, it.expiresAt) },
                addedBy = session.uid,
            )
            events.send(ShoppingEvent.Moved(drafts.count { it.row.item.ingredient != null }))
        }
    }
}
