package rsv.squitv.shared.catalog

/** Android CategoryViewModel ordering, shared by platform catalog adapters. */
object CatalogOrdering {
    fun <T> sort(items: List<T>, order: String, name: (T) -> String,
        releaseDate: (T) -> String?, rating: (T) -> String?): List<T> = when (order) {
        "RECENT" -> items.sortedByDescending(releaseDate)
        "RATING" -> items.sortedByDescending { rating(it)?.toDoubleOrNull() ?: 0.0 }
        else -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, name))
    }
}
