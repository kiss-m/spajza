package sk.matej.spajza.data.off

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import sk.matej.spajza.domain.custom.ProductFacts
import sk.matej.spajza.domain.model.Language
import sk.matej.spajza.domain.repository.ProductLookup
import java.io.IOException

/**
 * Open Food Facts product lookup. The database asks apps to identify themselves with a custom
 * User-Agent; data is under the Open Database License (credited on the About screen).
 */
class OpenFoodFactsClient(
    private val http: OkHttpClient,
    private val userAgent: String,
    private val baseUrl: String = "https://world.openfoodfacts.org",
) : ProductLookup {

    override suspend fun find(barcode: String, language: Language): Result<ProductFacts?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/api/v2/product/$barcode.json?fields=${OpenFoodFactsParser.FIELDS.joinToString(",")}"
            val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "application/json").build()
            http.newCall(request).execute().use { response ->
                when {
                    // Unknown products answer 404 with a small JSON body.
                    response.code == HTTP_NOT_FOUND -> null
                    !response.isSuccessful -> throw IOException("Open Food Facts answered ${response.code}")
                    else -> OpenFoodFactsParser.parse(barcode, response.body.string(), language)
                }
            }
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
