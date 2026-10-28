package sk.matej.spajza.data.ocr

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import sk.matej.spajza.domain.receipt.OcrLine
import sk.matej.spajza.domain.receipt.TextReader
import kotlin.math.roundToInt

/** On-device text recognition (ML Kit, Latin script); the photo never leaves the phone. */
class MlKitTextReader(private val context: Context) : TextReader {

    override suspend fun read(imageUri: String): Result<List<OcrLine>> = withContext(Dispatchers.IO) {
        try {
            val image = load(imageUri.toUri())
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val text = recognizer.process(image).await()
                Result.success(
                    text.textBlocks.flatMapIndexed { index, block ->
                        block.lines.mapNotNull { line ->
                            val box = line.boundingBox ?: return@mapNotNull null
                            OcrLine(line.text, box.left, box.top, box.right, box.bottom, block = index)
                        }
                    },
                )
            } finally {
                recognizer.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Big camera photos are scaled down first; both paths apply the EXIF rotation. */
    private fun load(uri: Uri): InputImage {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return InputImage.fromFilePath(context, uri)
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longSide = maxOf(info.size.width, info.size.height)
            if (longSide > MAX_SIDE_PX) {
                val scale = MAX_SIDE_PX.toDouble() / longSide
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return InputImage.fromBitmap(bitmap, 0)
    }

    private companion object {
        /** About 8 megapixels: plenty for receipt text, without running out of memory. */
        const val MAX_SIDE_PX = 3264
    }
}
