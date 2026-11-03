package rsv.squitv.core.ui.components.content

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.shared.catalog.ContentImages

/** Optional artwork candidates; the real resolved URL remains Coil's cache identity. */
data class ContentImageSource(val candidates: List<String?>)

class ContentImageInterceptor(private val settings: SettingsRepository) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is String && data !is ContentImageSource) return chain.proceed(chain.request)
        // Preserve local avatars/resources supported by Coil; only provider artwork is normalized.
        if (data is String && data.startsWithAny("content:", "file:", "android.resource:")) return chain.proceed(chain.request)
        val candidates = (if (data is ContentImageSource) data.candidates else listOf(data as String))
            .mapNotNull(ContentImages::clean).distinct().take(3)
        var base: String? = null
        var readBase = false
        var last: ImageResult? = null
        for (candidate in candidates) {
            var address = ContentImages.resolve(candidate)
            if (address == null && !candidate.contains(':')) {
                if (!readBase) {
                    base = withContext(Dispatchers.IO) { settings.settingsFlow.first().credentials?.baseUrl }
                    readBase = true
                }
                address = ContentImages.resolve(candidate, base)
            }
            if (address == null) continue
            val result = chain.proceed(chain.request.newBuilder().data(address).build())
            if (result !is ErrorResult) return result
            last = result
        }
        return last ?: chain.proceed(chain.request.newBuilder().data(null).build())
    }

    private fun String.startsWithAny(vararg prefixes: String) = prefixes.any { startsWith(it, ignoreCase = true) }
}
