package sk.matej.spajza.ui.book

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sk.matej.spajza.R
import sk.matej.spajza.domain.book.BookChapter
import sk.matej.spajza.ui.common.currentLanguage
import sk.matej.spajza.ui.common.durationText
import sk.matej.spajza.ui.common.labelRes
import sk.matej.spajza.ui.common.text
import sk.matej.spajza.ui.components.RecipeImage
import sk.matej.spajza.ui.components.SearchField
import sk.matej.spajza.ui.navigation.RecipeEditorDestination
import sk.matej.spajza.ui.ownrecipe.AddRecipeDialogs
import sk.matej.spajza.ui.ownrecipe.rememberAddRecipeState

@Composable
fun RecipeBookRoute(
    onOpenRecipe: (String) -> Unit,
    onOpenEditor: (RecipeEditorDestination) -> Unit,
    viewModel: RecipeBookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val language = currentLanguage()
    LaunchedEffect(language) { viewModel.onLanguage(language) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noCamera = stringResource(R.string.receipt_no_camera)
    val addRecipe = rememberAddRecipeState()
    RecipeBookScreen(
        state = state,
        query = query,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::onQueryChange,
        onOpenRecipe = onOpenRecipe,
        onAdd = addRecipe::open,
    )
    AddRecipeDialogs(
        state = addRecipe,
        onWrite = { onOpenEditor(RecipeEditorDestination()) },
        onImportUrl = { onOpenEditor(RecipeEditorDestination(importUrl = it)) },
        onImportPhoto = { onOpenEditor(RecipeEditorDestination(photoUri = it)) },
        onNoCamera = { scope.launch { snackbarHostState.showSnackbar(noCamera) } },
    )
}

/** Every recipe by chapter, like a cookbook, with favorites and the household's own recipes first. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecipeBookScreen(
    state: BookUiState,
    query: String,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Each section is a header followed by its recipes, so a header's index is the count of rows before it.
    val headerIndexes = remember(state.sections) {
        state.sections.runningFold(0) { index, section -> index + 1 + section.recipes.size }.dropLast(1)
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.book_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.book_add_recipe))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchField(query, onQueryChange, stringResource(R.string.book_search_hint), Modifier.padding(horizontal = 16.dp))
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.sections.isEmpty() -> Text(
                    stringResource(R.string.book_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(state.sections, key = { _, section -> section.chapter.name }) { index, section ->
                            AssistChip(
                                onClick = { scope.launch { listState.animateScrollToItem(headerIndexes[index]) } },
                                label = { Text("${stringResource(section.chapter.labelRes())} ${section.recipes.size}") },
                            )
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp),
                    ) {
                        state.sections.forEach { section ->
                            stickyHeader(key = "header-${section.chapter}") { ChapterHeader(section) }
                            items(section.recipes, key = { "${section.chapter}-${it.recipeId}" }) { recipe ->
                                BookRow(recipe, onClick = { onOpenRecipe(recipe.recipeId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterHeader(section: BookSectionModel) {
    val name = stringResource(section.chapter.labelRes())
    val title = if (section.chapter in MAIN_CHAPTERS) "${stringResource(R.string.book_main_dishes)} · $name" else name
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            "$title (${section.recipes.size})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { heading() },
        )
    }
}

@Composable
private fun BookRow(recipe: BookRecipeModel, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            RecipeImage(recipe.recipeId, recipe.cuisine, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), iconSize = 24.dp)
        },
        headlineContent = { Text(recipe.title.text(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(stringResource(R.string.book_recipe_meta, durationText(recipe.totalTimeMin), recipe.kcal)) },
        trailingContent = if (recipe.favorite) {
            {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.cook_favorite),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
    )
}

private val MAIN_CHAPTERS = setOf(BookChapter.POULTRY, BookChapter.PORK, BookChapter.BEEF_LAMB, BookChapter.FISH, BookChapter.MEATLESS)
