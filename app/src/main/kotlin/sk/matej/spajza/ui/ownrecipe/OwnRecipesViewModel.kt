package sk.matej.spajza.ui.ownrecipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.own.OwnRecipes
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.ui.cook.CookFeed
import sk.matej.spajza.ui.cook.RecipeCardModel
import java.text.Collator
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

data class OwnRecipesUiState(val isLoading: Boolean = true, val recipes: List<RecipeCardModel> = emptyList())

@HiltViewModel
class OwnRecipesViewModel @Inject constructor(home: HomeData, private val clock: Clock) : ViewModel() {
    private var feed: Pair<Catalog, CookFeed>? = null
    private val collator = Collator.getInstance(Locale.forLanguageTag("sk"))

    val state: StateFlow<OwnRecipesUiState> = combine(home.catalog, home.session, home.pantry, home.ratings, home.cookHistory) { catalog, session, pantry, ratings, history ->
        if (catalog == null || session == null || pantry == null) return@combine OwnRecipesUiState()
        val own = catalog.recipes.filter { OwnRecipes.isOwn(it.id) }.sortedWith(compareBy(collator) { it.title.sk })
        OwnRecipesUiState(
            isLoading = false,
            recipes = feedFor(catalog).cards(own, session, pantry, ratings.orEmpty(), history.orEmpty(), LocalDate.now(clock)),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OwnRecipesUiState())

    private fun feedFor(catalog: Catalog): CookFeed {
        val cached = feed
        if (cached != null && cached.first === catalog) return cached.second
        return CookFeed(catalog).also { feed = catalog to it }
    }
}
