package rsv.squitv.shared.catalog

import java.net.URI
import java.net.URLDecoder

/** Android detail-screen rules: provider cast lists and same-category suggestions. */
object ContentDiscovery {
    fun people(value: String?): List<String> = value.orEmpty().take(8192).split(',')
        .map(String::trim).filter(String::isNotEmpty).distinct().take(50)
    fun matchesPerson(person: String, cast: String?, director: String?): Boolean =
        (people(cast) + people(director)).any { it.equals(person.trim(), true) }
    fun <T> similar(items: List<T>, currentId: Int, category: String?, id: (T) -> Int, categoryId: (T) -> String?): List<T> =
        if (category.isNullOrBlank()) emptyList() else items.asSequence()
            .filter { id(it) != currentId && categoryId(it) == category }.distinctBy(id).take(15).toList()
    fun trailerUrl(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.length <= 2048 } ?: return null
        val id = if (raw.matches(Regex("[A-Za-z0-9_-]{11}"))) raw else runCatching {
            val uri = URI(raw)
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.rawUserInfo != null) return null
            when (uri.host?.lowercase()) {
                "youtu.be" -> uri.path.removePrefix("/")
                "youtube.com", "www.youtube.com", "m.youtube.com" -> {
                    val path = uri.path.trim('/').split('/')
                    if (path.size == 2 && path[0] in setOf("embed", "shorts")) path[1]
                    else uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "v" }
                        ?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") }
                }
                else -> null
            }
        }.getOrNull()
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://www.youtube.com/watch?v=$it" }
    }
}
