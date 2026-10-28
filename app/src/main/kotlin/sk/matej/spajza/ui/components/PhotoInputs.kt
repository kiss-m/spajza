package sk.matej.spajza.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import sk.matej.spajza.R
import java.io.File

/** Opens the camera app or the photo picker; the result is a photo to read on the phone. */
class PhotoPicker(val takePhoto: () -> Unit, val pickPhoto: () -> Unit)

@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit, onNoCamera: () -> Unit): PhotoPicker {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val noCamera by rememberUpdatedState(onNoCamera)
    // The file the camera app writes to; kept across the activity being recreated meanwhile.
    var pending by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pending
        pending = null
        if (saved && uri != null) picked(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) picked(uri) }
    return remember(context, camera, gallery) {
        PhotoPicker(
            takePhoto = {
                val uri = PhotoFiles.newPhotoUri(context)
                pending = uri
                try {
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    pending = null
                    noCamera()
                }
            },
            pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

/** Camera or gallery, with a hint how to take a readable photo. */
@Composable
fun PhotoSourceDialog(title: String, body: String, onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onCamera) { Text(stringResource(R.string.photo_source_camera)) } },
        dismissButton = { TextButton(onClick = onGallery) { Text(stringResource(R.string.photo_source_gallery)) } },
    )
}

/** Photos to read live in the cache only, shared with the camera app through the FileProvider. */
object PhotoFiles {
    private const val DIRECTORY = "photos"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    fun newPhotoUri(context: Context): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val now = System.currentTimeMillis()
        directory.listFiles()?.filter { now - it.lastModified() > MAX_AGE_MS }?.forEach { it.delete() }
        val file = File(directory, "photo-$now.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
