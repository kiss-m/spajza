package sk.matej.spajza.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Own photos of recipes, kept only on this phone (D14). The folders are the list: every change is
 * written to disk first and then read back from it.
 */
class RecipePhotoStore(private val context: Context) {
    private val root = File(context.filesDir, RecipePhotoPaths.DIRECTORY)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Photos by recipe key, newest first; null until the folders were read. */
    private val all = MutableStateFlow<Map<String, List<File>>?>(null)

    /** The newest photo of every recipe that has one, by [RecipePhotoPaths.keyOf]. */
    val covers: StateFlow<Map<String, File>> = all
        .map { photos -> photos.orEmpty().mapValues { it.value.first() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch { mutex.withLock { loaded() } }
    }

    fun photos(recipeId: String): Flow<List<File>> {
        val key = RecipePhotoPaths.keyOf(recipeId)
        return all.map { it?.get(key).orEmpty() }.distinctUntilChanged()
    }

    /** Scales the picture at [source] down and saves it as the recipe's newest photo; false if it cannot be read. */
    suspend fun add(recipeId: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        val bitmap = decode(source) ?: return@withContext false
        edit(recipeId) { folder ->
            folder.mkdirs()
            val name = RecipePhotoPaths.fileName(RecipePhotoPaths.nextTimestamp(System.currentTimeMillis(), folder.list().orEmpty().asList()))
            val partial = File(folder, "$name.part")
            try {
                val written = partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, RecipePhotoPaths.JPEG_QUALITY, it) }
                written && partial.renameTo(File(folder, name))
            } catch (e: IOException) {
                Log.w(TAG, "Saving a photo failed", e)
                false
            } finally {
                partial.delete()
            }
        }
    }

    suspend fun delete(recipeId: String, photo: File) {
        withContext(Dispatchers.IO) { edit(recipeId) { folder -> File(folder, photo.name).delete() } }
    }

    /** Removes every photo of a recipe, when an own recipe is deleted. */
    suspend fun deleteAll(recipeId: String) {
        withContext(Dispatchers.IO) { edit(recipeId) { folder -> folder.deleteRecursively() } }
    }

    /** Removes every photo, when the account is deleted. */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                root.deleteRecursively()
                all.value = emptyMap()
            }
        }
    }

    private suspend fun <T> edit(recipeId: String, change: (File) -> T): T = mutex.withLock {
        val photos = loaded()
        val key = RecipePhotoPaths.keyOf(recipeId)
        val folder = File(root, key)
        try {
            change(folder)
        } finally {
            val current = list(folder)
            all.value = if (current.isEmpty()) photos - key else photos + (key to current)
        }
    }

    /** Reads the folders the first time; call with [mutex] held. */
    private fun loaded(): Map<String, List<File>> = all.value ?: root.listFiles().orEmpty()
        .filter { it.isDirectory }
        .associate { it.name to list(it) }
        .filterValues { it.isNotEmpty() }
        .also { all.value = it }

    private fun list(folder: File): List<File> = RecipePhotoPaths.newestFirst(folder.list().orEmpty().asList()).map { File(folder, it) }

    /** The picture upright (as the camera's orientation tag says) and no bigger than [RecipePhotoPaths.MAX_SIDE]. */
    private suspend fun decode(source: Uri): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(source)
            .size(RecipePhotoPaths.MAX_SIDE, RecipePhotoPaths.MAX_SIDE)
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
        if (result == null) {
            Log.w(TAG, "Reading a photo failed")
            return null
        }
        val bitmap = result.image.toBitmap()
        val (width, height) = RecipePhotoPaths.fittedSize(bitmap.width, bitmap.height)
        return if (width == bitmap.width && height == bitmap.height) bitmap else bitmap.scale(width, height)
    }

    private companion object {
        const val TAG = "RecipePhotoStore"
    }
}
