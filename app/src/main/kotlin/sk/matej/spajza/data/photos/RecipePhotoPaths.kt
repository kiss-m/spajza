package sk.matej.spajza.data.photos

/**
 * Where and how own recipe photos are kept on the phone (docs/DECISIONS.md D14):
 * `files/recipe-photos/<recipeKey>/<timestamp>.jpg`, the newest first. No Android imports, so it is
 * tested on the JVM.
 */
object RecipePhotoPaths {
    const val DIRECTORY = "recipe-photos"

    /** Photos are scaled down to at most this many pixels on the long side. */
    const val MAX_SIDE = 1600
    const val JPEG_QUALITY = 85

    private const val EXTENSION = ".jpg"

    /** The folder name of a recipe's photos: `own:abc` becomes `own_abc`. */
    fun keyOf(recipeId: String): String = recipeId.replace(':', '_')

    fun fileName(timestamp: Long): String = "$timestamp$EXTENSION"

    /** The photo file names among [names], newest first; anything else in the folder is ignored. */
    fun newestFirst(names: Collection<String>): List<String> = names
        .mapNotNull { name -> timestampOf(name)?.let { it to name } }
        .sortedByDescending { it.first }
        .map { it.second }

    /** [now], or one past the newest existing photo, so two photos saved in the same millisecond keep their order. */
    fun nextTimestamp(now: Long, existing: Collection<String>): Long {
        val newest = existing.mapNotNull(::timestampOf).maxOrNull() ?: return now
        return maxOf(now, newest + 1)
    }

    /** [width] × [height] scaled down to fit [maxSide], keeping the aspect ratio; never enlarged. */
    fun fittedSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        require(width > 0 && height > 0) { "size must be positive, was $width × $height" }
        val longSide = maxOf(width, height)
        if (longSide <= maxSide) return width to height
        val factor = maxSide.toDouble() / longSide
        fun scaled(side: Int) = Math.round(side * factor).toInt().coerceAtLeast(1)
        return scaled(width) to scaled(height)
    }

    private fun timestampOf(name: String): Long? =
        if (name.endsWith(EXTENSION)) name.removeSuffix(EXTENSION).toLongOrNull()?.takeIf { it >= 0 } else null
}
