package sk.matej.spajza.data.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import sk.matej.spajza.domain.own.RecipeDraft
import sk.matej.spajza.domain.repository.RecipeImporter
import java.io.IOException

/** Downloads a recipe page (at most a few megabytes) and reads its schema.org Recipe. */
class WebRecipeImporter(private val http: OkHttpClient, private val userAgent: String) : RecipeImporter {

    override suspend fun fromUrl(url: String): Result<RecipeDraft?> = withContext(Dispatchers.IO) {
        runCatching {
            // Android blocks plain http; recipe sites serve https anyway.
            val secure = url.trim().replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
            val request = Request.Builder()
                .url(secure)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("The page answered ${response.code}")
                val body = response.body
                val source = body.source()
                source.request(MAX_BYTES)
                val bytes = source.buffer.readByteArray(minOf(source.buffer.size, MAX_BYTES))
                val html = String(bytes, body.contentType()?.charset() ?: Charsets.UTF_8)
                RecipePageParser.parse(html, response.request.url.toString())
            }
        }
    }

    private companion object {
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}
