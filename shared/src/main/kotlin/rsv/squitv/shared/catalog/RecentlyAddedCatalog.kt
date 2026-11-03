package rsv.squitv.shared.catalog

import java.text.SimpleDateFormat
import java.util.Locale

/** Android CatalogRepository's recent-content policy, independent of storage and platform. */
object RecentlyAddedCatalog {
    const val FOURTEEN_DAYS_MS = 14 * 24 * 60 * 60 * 1000L

    fun parseAddedTimestamp(added: String?): Long {
        if (added.isNullOrBlank()) return 0L
        added.trim().toLongOrNull()?.let {
            return if (it < 10000000000L) it * 1000L else it
        }
        try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(added)?.time?.let { return it }
        } catch (_: Exception) {}
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(added)?.time?.let { return it }
        } catch (_: Exception) {}
        return 0L
    }

    fun <T> select(items: List<T>, limit: Int, currentTimeMillis: Long = System.currentTimeMillis(),
        added: (T) -> String?, id: (T) -> Int): List<T> {
        val minTimestamp = currentTimeMillis - FOURTEEN_DAYS_MS
        val maxTimestamp = currentTimeMillis + 300_000L // Original five-minute clock-skew tolerance.
        return items.mapNotNull { item ->
            val timestamp = parseAddedTimestamp(added(item))
            if (timestamp in minTimestamp..maxTimestamp) item to timestamp else null
        }.sortedWith(compareByDescending<Pair<T, Long>> { it.second }.thenByDescending { id(it.first) })
            .take(limit).map { it.first }
    }
}
