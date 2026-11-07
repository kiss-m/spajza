package sk.matej.spajza.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import sk.matej.spajza.domain.book.BookChapter
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.domain.session.HomeData
import javax.inject.Inject

@HiltViewModel
class RecipeBookViewModel @Inject constructor(home: HomeData) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** The language titles are sorted in; the screen passes the app's. */
    private val language = MutableStateFlow(Language.SK)

    /** The chapter chip that is on, or null for the whole book. */
    private val chapter = MutableStateFlow<BookChapter?>(null)
    private var feed: Pair<Catalog, RecipeBookFeed>? = null

    val state: StateFlow<BookUiState> = combine(home.catalog, home.ratings, _query, language, chapter) { catalog, ratings, query, language, chapter ->
        if (catalog == null) BookUiState() else feedFor(catalog).state(ratings.orEmpty(), query, language, chapter)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookUiState())

    private fun feedFor(catalog: Catalog): RecipeBookFeed {
        val cached = feed
        if (cached != null && cached.first === catalog) return cached.second
        return RecipeBookFeed(catalog).also { feed = catalog to it }
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onLanguage(language: Language) {
        this.language.value = language
    }

    /** Narrows the book to [chapter]; the chip that is already on, or null ("Všetko"), shows every chapter. */
    fun onChapter(chapter: BookChapter?) {
        this.chapter.value = chapter?.takeIf { it != state.value.selected }
    }
}
