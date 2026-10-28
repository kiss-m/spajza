package sk.matej.spajza.ui.ownrecipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.own.OwnRecipe
import sk.matej.spajza.domain.own.OwnRecipes
import sk.matej.spajza.domain.own.PhotoLine
import sk.matej.spajza.domain.own.PhotoLineKind
import sk.matej.spajza.domain.own.RecipeDraft
import sk.matej.spajza.domain.own.RecipeLineMatcher
import sk.matej.spajza.domain.own.RecipePhotoImport
import sk.matej.spajza.domain.own.RecipeSource
import sk.matej.spajza.domain.receipt.TextReader
import sk.matej.spajza.domain.repository.OwnRecipeRepository
import sk.matej.spajza.domain.repository.RecipeImporter
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.ui.common.IngredientOption
import sk.matej.spajza.ui.common.IngredientOptions
import sk.matej.spajza.ui.navigation.RecipeEditorDestination
import javax.inject.Inject

enum class EditorStage { LOADING, PHOTO_LINES, FORM, NOT_FOUND }

/** Why an import gave less than a full recipe; the form stays open for typing. */
enum class ImportProblem { PAGE_FAILED, NO_RECIPE_ON_PAGE, PHOTO_FAILED, NO_TEXT_ON_PHOTO }

data class RecipeEditorUiState(
    val stage: EditorStage = EditorStage.LOADING,
    val isNew: Boolean = true,
    val form: RecipeForm? = null,
    val preview: List<LinePreview> = emptyList(),
    val photoLines: List<PhotoLine> = emptyList(),
    val problem: ImportProblem? = null,
    /** The ingredient line whose catalog ingredient is being picked. */
    val picking: String? = null,
    val saving: Boolean = false,
)

/** Writing an own recipe, editing one, or checking one imported from a web page or a photo. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val home: HomeData,
    private val recipes: OwnRecipeRepository,
    private val importer: RecipeImporter,
    private val reader: TextReader,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<RecipeEditorDestination>()
    private val mutableState = MutableStateFlow(RecipeEditorUiState(isNew = args.recipeId == null))
    // Keyed by the ingredient list, which stays the same when only own recipes change.
    private var matcher: Pair<List<Ingredient>, RecipeLineMatcher>? = null
    private var options: Pair<List<Ingredient>, IngredientOptions>? = null

    private val doneEvents = Channel<String>(Channel.CONFLATED)

    /** The saved recipe's catalog id, once it shows in the catalog. */
    val done: Flow<String> = doneEvents.receiveAsFlow()

    /** Matching runs a moment after typing stops, off the main thread. */
    private val preview: Flow<List<LinePreview>> = mutableState
        .map { it.form?.let { form -> form.ingredientsText to form.picks } }
        .distinctUntilChanged()
        .debounce(PREVIEW_DELAY_MS)
        .mapLatest {
            val form = mutableState.value.form
            val catalog = home.catalog.value
            if (form == null || catalog == null) emptyList() else RecipePreview.lines(form, matcherFor(catalog), catalog)
        }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<RecipeEditorUiState> = combine(mutableState, preview) { current, lines -> current.copy(preview = lines) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutableState.value)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val catalog = home.catalog.filterNotNull().first()
        val recipeId = args.recipeId
        val importUrl = args.importUrl
        val photoUri = args.photoUri
        when {
            recipeId != null -> {
                val ownId = OwnRecipes.ownId(recipeId) ?: recipeId
                val recipe = home.ownRecipes.filterNotNull().first().firstOrNull { it.id == ownId }
                mutableState.update {
                    if (recipe == null) it.copy(stage = EditorStage.NOT_FOUND) else it.copy(stage = EditorStage.FORM, form = RecipeForm.of(recipe, matcherFor(catalog)))
                }
            }
            importUrl != null -> importPage(importUrl)
            photoUri != null -> readPhoto(photoUri, catalog)
            else -> mutableState.update { it.copy(stage = EditorStage.FORM, form = RecipeForm(id = OwnRecipe.newId())) }
        }
    }

    private suspend fun importPage(url: String) {
        mutableState.update { it.copy(stage = EditorStage.LOADING, problem = null) }
        val fallback = RecipeDraft(source = RecipeSource.URL, sourceUrl = url)
        val result = importer.fromUrl(url)
        val draft = result.getOrNull()
        val problem = when {
            result.isFailure -> ImportProblem.PAGE_FAILED
            draft == null -> ImportProblem.NO_RECIPE_ON_PAGE
            else -> null
        }
        mutableState.update {
            it.copy(stage = EditorStage.FORM, form = RecipeForm.of(draft ?: fallback, it.form?.id ?: OwnRecipe.newId()), problem = problem)
        }
    }

    private suspend fun readPhoto(uri: String, catalog: Catalog) {
        val lines = reader.read(uri).getOrElse {
            mutableState.update { it.copy(stage = EditorStage.FORM, form = photoForm(RecipeDraft()), problem = ImportProblem.PHOTO_FAILED) }
            return
        }
        val guessed = withContext(Dispatchers.Default) { RecipePhotoImport.guess(lines, matcherFor(catalog)) }
        mutableState.update {
            if (guessed.isEmpty()) {
                it.copy(stage = EditorStage.FORM, form = photoForm(RecipeDraft()), problem = ImportProblem.NO_TEXT_ON_PHOTO)
            } else {
                it.copy(stage = EditorStage.PHOTO_LINES, photoLines = guessed)
            }
        }
    }

    private fun photoForm(draft: RecipeDraft) = RecipeForm.of(draft.copy(source = RecipeSource.PHOTO), OwnRecipe.newId())

    /** Loads the page again after a failed import. */
    fun onRetryImport() {
        val url = args.importUrl ?: return
        viewModelScope.launch { importPage(url) }
    }

    fun onPhotoKind(index: Int, kind: PhotoLineKind) = mutableState.update { state ->
        state.copy(photoLines = state.photoLines.mapIndexed { i, line -> if (i == index) line.copy(kind = kind) else line })
    }

    /** The marked photo lines become the form. */
    fun onPhotoDone() = mutableState.update { state ->
        state.copy(stage = EditorStage.FORM, form = photoForm(RecipePhotoImport.draft(state.photoLines)))
    }

    fun onChange(change: (RecipeForm) -> RecipeForm) = mutableState.update { state -> state.copy(form = state.form?.let(change)) }

    fun onToggleMeal(meal: MealType) = onChange { form ->
        form.copy(mealTypes = if (meal in form.mealTypes) form.mealTypes - meal else form.mealTypes + meal)
    }

    fun onPick(lineText: String) = mutableState.update { it.copy(picking = lineText) }

    fun onPickDismiss() = mutableState.update { it.copy(picking = null) }

    /** The catalog ingredient for the line being picked; null marks it as not in the catalog. */
    fun onPicked(option: IngredientOption?) {
        val line = mutableState.value.picking ?: return
        onChange { form -> form.copy(picks = form.picks + (line to option?.ingredientId)) }
        onPickDismiss()
    }

    fun searchIngredients(query: String, exclude: Set<String>): List<IngredientOption> {
        val catalog = home.catalog.value ?: return emptyList()
        val cached = options?.takeIf { it.first === catalog.ingredients }?.second
            ?: IngredientOptions(catalog).also { options = catalog.ingredients to it }
        return cached.search(query, exclude)
    }

    fun onSave() {
        val current = mutableState.value
        val form = current.form?.takeIf { it.canSave } ?: return
        val session = home.session.value ?: return
        val catalog = home.catalog.value ?: return
        if (current.saving) return
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val recipe = form.toRecipe(matcherFor(catalog))
            recipes.save(session.householdId, recipe, session.uid)
            // The local write shows in the catalog at once; the detail screen then finds the recipe.
            withTimeoutOrNull(SHOW_TIMEOUT_MS) { home.catalog.first { it?.recipesById?.containsKey(recipe.recipeId) == true } }
            doneEvents.send(recipe.recipeId)
        }
    }

    private fun matcherFor(catalog: Catalog): RecipeLineMatcher {
        val cached = matcher
        if (cached != null && cached.first === catalog.ingredients) return cached.second
        return RecipeLineMatcher(catalog).also { matcher = catalog.ingredients to it }
    }

    private companion object {
        const val PREVIEW_DELAY_MS = 300L
        const val SHOW_TIMEOUT_MS = 3_000L
    }
}
