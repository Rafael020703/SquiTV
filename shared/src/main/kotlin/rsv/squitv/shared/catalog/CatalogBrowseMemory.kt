package rsv.squitv.shared.catalog

import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CatalogPosition(val categoryId: String, val itemId: String? = null,
    val itemsByCategory: Map<String, String> = emptyMap())

/** Navigation only: no catalogue payload, media address or account credentials. */
@Serializable
data class CatalogBrowseMemory(val positions: Map<String, CatalogPosition> = emptyMap()) {
    fun position(scope: String, type: String): CatalogPosition? = key(scope, type)?.let(positions::get)

    fun record(scope: String, type: String, categoryId: String, itemId: String? = null): CatalogBrowseMemory {
        val key = key(scope, type) ?: return this
        if (categoryId.length !in 1..256 || itemId != null && itemId.length !in 1..128) return this
        val previous = positions[key]
        val categoryItems = LinkedHashMap(previous?.itemsByCategory.orEmpty())
        previous?.itemId?.let { categoryItems[previous.categoryId] = it }
        if (itemId != null) { categoryItems.remove(categoryId); categoryItems[categoryId] = itemId }
        while (categoryItems.size > 16) categoryItems.remove(categoryItems.keys.first())
        val next = CatalogPosition(categoryId, itemId ?: categoryItems[categoryId], categoryItems)
        if (previous == next) return this
        val updated = LinkedHashMap(positions)
        updated.remove(key)
        updated[key] = next
        while (updated.size > 64) updated.remove(updated.keys.first())
        // Reserve space for the existing Windows preferences and avoid unbounded navigation history.
        while (Json.encodeToString(copy(positions = updated)).toByteArray(Charsets.UTF_8).size > 16 * 1024) {
            if (updated.size > 1) updated.remove(updated.keys.first())
            else {
                categoryItems.remove(categoryItems.keys.first())
                updated[key] = next.copy(itemsByCategory = categoryItems.toMap())
            }
        }
        return copy(positions = updated)
    }

    fun validate() {
        require(positions.size <= 64)
        require(positions.all { (key, value) ->
            key.matches(Regex("[a-f0-9]{64}/(live|movie|series)")) &&
                value.categoryId.length in 1..256 && (value.itemId == null || value.itemId.length in 1..128)
                && value.itemsByCategory.size <= 16 && value.itemsByCategory.all { (category, item) ->
                    category.length in 1..256 && item.length in 1..128
                }
        })
    }

    companion object {
        /** Android's provider/genre fallback, applied only to the visible, loaded categories. */
        fun selectCategory(available: List<String>, requested: String?): String? =
            available.firstOrNull { it == requested }
                ?: available.firstOrNull { requested != null && it == "FALLBACK:$requested" }
                ?: available.firstOrNull { requested != null && requested.startsWith("FALLBACK:") && it == requested.removePrefix("FALLBACK:") }
                ?: available.firstOrNull { it == "ALL" }
                ?: available.firstOrNull()

        private fun key(scope: String, type: String): String? {
            if (scope.isBlank() || scope.startsWith('/')) return null
            val normalized = when (type.lowercase(Locale.ROOT)) {
                "live" -> "live"
                "movie", "movies", "vod" -> "movie"
                "series" -> "series"
                else -> return null
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return "$hash/$normalized"
        }
    }
}
