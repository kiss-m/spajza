package sk.matej.spajza.ui.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
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
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.custom.CustomIngredient
import sk.matej.spajza.domain.model.IngredientCategory
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.usecase.AddCustomToPantryUseCase
import sk.matej.spajza.domain.usecase.ScanProductUseCase
import sk.matej.spajza.domain.usecase.ScanResult
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.IngredientOptions
import sk.matej.spajza.ui.navigation.ProductDestination
import sk.matej.spajza.ui.pantry.PantryNotices
import java.time.LocalDate
import javax.inject.Inject

data class ProductUiState(
    val isLoading: Boolean = true,
    val status: ProductStatus = ProductStatus.MANUAL,
    val form: ProductForm? = null,
    /** A known product shows only the pantry part until the user asks to edit it. */
    val showDetails: Boolean = true,
    val saving: Boolean = false,
)

/** Checking a scanned or typed-in food and putting it into the pantry. */
@HiltViewModel
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val home: HomeData,
    private val scan: ScanProductUseCase,
    private val addToPantry: AddCustomToPantryUseCase,
    private val notices: PantryNotices,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<ProductDestination>()
    private val mutableState = MutableStateFlow(ProductUiState())
    val state: StateFlow<ProductUiState> = mutableState.asStateFlow()

    private val doneEvents = Channel<Unit>(Channel.CONFLATED)

    /** Emits once the food is saved; the screen then closes. */
    val done: Flow<Unit> = doneEvents.receiveAsFlow()

    private var started = false
    private var options: Pair<Catalog, IngredientOptions>? = null

    /** Loads the product; [language] decides which Open Food Facts name comes first. */
    fun start(language: Language) {
        if (started) return
        started = true
        viewModelScope.launch {
            val catalog = home.catalog.filterNotNull().first()
            val barcode = args.barcode
            val (status, ingredient) = if (barcode == null) {
                ProductStatus.MANUAL to CustomIngredient(id = AddCustomToPantryUseCase.newManualId(), name = args.name.orEmpty())
            } else {
                val known = home.customIngredients.filterNotNull().first().values
                when (val result = scan(barcode, known, catalog, language)) {
                    is ScanResult.Known -> ProductStatus.KNOWN to result.ingredient
                    is ScanResult.Found -> ProductStatus.FOUND to result.ingredient
                    is ScanResult.Unknown -> ProductStatus.UNKNOWN to result.ingredient
                    is ScanResult.Offline -> ProductStatus.OFFLINE to result.ingredient
                }
            }
            mutableState.value = ProductUiState(
                isLoading = false,
                status = status,
                form = ProductForm.of(ingredient, catalog),
                showDetails = status != ProductStatus.KNOWN,
            )
        }
    }

    fun onChange(change: (ProductForm) -> ProductForm) = mutableState.update { state -> state.copy(form = state.form?.let(change)) }

    fun onShowDetails() = mutableState.update { it.copy(showDetails = true) }

    fun onCategory(category: IngredientCategory) = onChange { it.copy(category = category) }

    fun onUnit(unit: PantryUnit) = onChange { it.copy(unit = unit) }

    fun onExpiry(date: LocalDate?) = onChange { it.copy(expiresAt = date) }

    /** The catalog ingredient the product counts as; null unlinks it. */
    fun onLink(option: IngredientOption?) = onChange { it.copy(link = option) }

    fun searchIngredients(query: String, exclude: Set<String>): List<IngredientOption> {
        val catalog = home.catalog.value ?: return emptyList()
        return optionsFor(catalog).search(query, exclude)
    }

    fun onSave() {
        if (mutableState.value.saving) return
        val form = mutableState.value.form?.takeIf { it.canSave } ?: return
        val session = home.session.value ?: return
        val catalog = home.catalog.value ?: return
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ingredient = form.toIngredient()
            addToPantry(session.householdId, ingredient, form.quantity, form.expiresAt, session.uid, catalog)
            val name = form.link?.name ?: LocalizedText.same(ingredient.displayName)
            notices.announceAdded(name)
            doneEvents.send(Unit)
        }
    }

    private fun optionsFor(catalog: Catalog): IngredientOptions {
        val cached = options
        if (cached != null && cached.first === catalog) return cached.second
        return IngredientOptions(catalog).also { options = catalog to it }
    }
}
