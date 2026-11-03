package rsv.squitv.shared.catalog

import java.net.URI
import java.util.Locale

/** Provider artwork only. Never infer an image host or synthesize missing artwork. */
object ContentImages {
    fun clean(raw: String?): String? = raw?.trim()?.replace("&amp;", "&")?.takeIf {
        it.isNotEmpty() && it.length <= 8192 && it.lowercase(Locale.ROOT) !in setOf("null", "undefined", "n/a", "none", "0", "false")
    }

    fun first(vararg values: String?): String? = values.firstNotNullOfOrNull(::clean)

    fun resolve(raw: String?, baseUrl: String? = null): String? {
        val value = clean(raw)?.replace(" ", "%20") ?: return null
        return runCatching {
            val base = baseUrl?.let { URI(it) }?.takeIf { it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && it.host != null && it.userInfo == null }
            val parsed = URI(value)
            val uri = when {
                parsed.isAbsolute -> parsed
                value.startsWith("//") -> URI("${base?.scheme ?: "https"}:$value")
                base != null -> base.resolve(parsed)
                else -> return null
            }
            uri.takeIf { it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && it.host != null && it.userInfo == null }?.toASCIIString()
        }.getOrNull()
    }
}
