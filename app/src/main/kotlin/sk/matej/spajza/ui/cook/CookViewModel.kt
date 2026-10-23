package sk.matej.spajza.ui.cook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import sk.matej.spajza.domain.repository.CatalogRepository
import javax.inject.Inject

data class CookUiState(val isLoading: Boolean = true, val recipeCount: Int = 0)

@HiltViewModel
class CookViewModel @Inject constructor(catalog: CatalogRepository) : ViewModel() {
    val state: StateFlow<CookUiState> = catalog.catalog
        .map { CookUiState(isLoading = false, recipeCount = it.recipes.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CookUiState())
}
