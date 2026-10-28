package sk.matej.spajza.domain.receipt

/** Recognizes the text on a photo. The app reads it on the phone, so the photo is never uploaded. */
interface TextReader {
    /** The lines found on the image at [imageUri]; failure when the image cannot be read. */
    suspend fun read(imageUri: String): Result<List<OcrLine>>
}
