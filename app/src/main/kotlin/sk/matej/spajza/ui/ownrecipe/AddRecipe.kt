package sk.matej.spajza.ui.ownrecipe

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import sk.matej.spajza.R
import sk.matej.spajza.ui.components.PhotoSourceDialog
import sk.matej.spajza.ui.components.rememberPhotoPicker

/** The steps of "Pridať recept": choose how, then the address or the photo source. */
enum class AddRecipeStep { CLOSED, CHOOSE, URL, PHOTO }

@Stable
class AddRecipeState {
    var step by mutableStateOf(AddRecipeStep.CLOSED)

    fun open() {
        step = AddRecipeStep.CHOOSE
    }

    fun close() {
        step = AddRecipeStep.CLOSED
    }
}

@Composable
fun rememberAddRecipeState(): AddRecipeState = remember { AddRecipeState() }

/** "Pridať recept": write one, import it from a web page, or read it from a photo. */
@Composable
fun AddRecipeDialogs(
    state: AddRecipeState,
    onWrite: () -> Unit,
    onImportUrl: (String) -> Unit,
    onImportPhoto: (String) -> Unit,
    onNoCamera: () -> Unit,
) {
    val photos = rememberPhotoPicker(onPicked = { onImportPhoto(it.toString()) }, onNoCamera = onNoCamera)
    when (state.step) {
        AddRecipeStep.CLOSED -> Unit
        AddRecipeStep.CHOOSE -> AlertDialog(
            onDismissRequest = state::close,
            title = { Text(stringResource(R.string.add_recipe_title)) },
            text = {
                Column {
                    Choice(R.string.add_recipe_write, R.string.add_recipe_write_detail) {
                        state.close()
                        onWrite()
                    }
                    Choice(R.string.add_recipe_url, R.string.add_recipe_url_detail) { state.step = AddRecipeStep.URL }
                    Choice(R.string.add_recipe_photo, R.string.add_recipe_photo_detail) { state.step = AddRecipeStep.PHOTO }
                }
            },
            confirmButton = { TextButton(onClick = state::close) { Text(stringResource(R.string.action_cancel)) } },
        )
        AddRecipeStep.URL -> ImportUrlDialog(
            onImport = { url ->
                state.close()
                onImportUrl(url)
            },
            onDismiss = state::close,
        )
        AddRecipeStep.PHOTO -> PhotoSourceDialog(
            title = stringResource(R.string.add_recipe_photo),
            body = stringResource(R.string.add_recipe_photo_body),
            onCamera = {
                state.close()
                photos.takePhoto()
            },
            onGallery = {
                state.close()
                photos.pickPhoto()
            },
            onDismiss = state::close,
        )
    }
}

@Composable
private fun Choice(@StringRes title: Int, @StringRes detail: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(detail)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ImportUrlDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val url = RecipeLinks.urlIn(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_recipe_url)) },
        text = {
            Column {
                Text(stringResource(R.string.add_recipe_url_body))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.add_recipe_url_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { url?.let(onImport) }, enabled = url != null) { Text(stringResource(R.string.add_recipe_url_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
