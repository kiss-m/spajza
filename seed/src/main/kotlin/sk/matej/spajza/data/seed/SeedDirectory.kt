package sk.matej.spajza.data.seed

import java.io.File

/** Reads seed files from a directory on disk (tests and build tooling; the app reads assets). */
object SeedDirectory {
    fun read(root: File): SeedSources {
        require(root.isDirectory) { "Seed directory not found: $root" }
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .map { SeedFile(it.relativeTo(root).invariantSeparatorsPath, it.readText()) }
            .toList()
        return SeedSources.of(files)
    }
}
