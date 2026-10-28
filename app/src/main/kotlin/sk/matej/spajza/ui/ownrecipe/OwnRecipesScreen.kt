package sk.matej.spajza.ui.ownrecipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sk.matej.spajza.R
import sk.matej.spajza.ui.cook.RecipeListItem
import sk.matej.spajza.ui.components.EmptyState
import sk.matej.spajza.ui.components.SpajzaIcons
import sk.matej.spajza.ui.navigation.RecipeEditorDestination

@Composable
fun OwnRecipesRoute(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onOpenEditor: (RecipeEditorDestination) -> Unit,
    viewModel: OwnRecipesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noCamera = stringResource(R.string.receipt_no_camera)
    val addRecipe = rememberAddRecipeState()
    OwnRecipesScreen(state, snackbarHostState, onBack, onOpenRecipe, onAdd = addRecipe::open)
    AddRecipeDialogs(
        state = addRecipe,
        onWrite = { onOpenEditor(RecipeEditorDestination()) },
        onImportUrl = { onOpenEditor(RecipeEditorDestination(importUrl = it)) },
        onImportPhoto = { onOpenEditor(RecipeEditorDestination(photoUri = it)) },
        onNoCamera = { scope.launch { snackbarHostState.showSnackbar(noCamera) } },
    )
}

/** The household's own recipes, with what the pantry has for each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnRecipesScreen(
    state: OwnRecipesUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.own_recipes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_recipe_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.recipes.isEmpty() -> EmptyState(
                icon = SpajzaIcons.MenuBook,
                title = stringResource(R.string.own_recipes_empty_title),
                body = stringResource(R.string.own_recipes_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.recipes, key = { it.recipeId }) { model -> RecipeListItem(model, onClick = { onOpenRecipe(model.recipeId) }) }
            }
        }
    }
}
