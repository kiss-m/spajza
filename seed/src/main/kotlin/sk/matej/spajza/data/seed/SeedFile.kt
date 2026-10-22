package sk.matej.spajza.data.seed

import java.security.MessageDigest

/** A seed file's path relative to the seed root (e.g. `recipes/italian.json`) and its content. */
data class SeedFile(val path: String, val content: String)

data class SeedSources(
    val ingredients: List<SeedFile>,
    val recipes: List<SeedFile>,
    val substitutions: List<SeedFile>,
) {
    /** Stable hash of every file; a change triggers a re-import of the catalog. */
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        (ingredients + recipes + substitutions).sortedBy { it.path }.forEach { file ->
            digest.update(file.path.toByteArray())
            digest.update(0)
            digest.update(file.content.toByteArray())
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val INGREDIENTS_DIR = "ingredients"
        const val RECIPES_DIR = "recipes"
        const val SUBSTITUTIONS_DIR = "substitutions"

        /** Groups files by their top-level directory; other paths are ignored. */
        fun of(files: List<SeedFile>) = SeedSources(
            ingredients = files.filter { it.path.startsWith("$INGREDIENTS_DIR/") },
            recipes = files.filter { it.path.startsWith("$RECIPES_DIR/") },
            substitutions = files.filter { it.path.startsWith("$SUBSTITUTIONS_DIR/") },
        )
    }
}
