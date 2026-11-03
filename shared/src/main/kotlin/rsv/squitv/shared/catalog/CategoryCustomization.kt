package rsv.squitv.shared.catalog

/** Android category customization rules, independent of Room/DataStore and platform models. */
object CategoryCustomization {
    fun <T> apply(categories: List<T>, customOrder: List<String>? = null,
        hidden: Set<String>? = null, id: (T) -> String): List<T> {
        var result = categories
        if (!hidden.isNullOrEmpty()) result = result.filter { id(it) !in hidden }
        if (!customOrder.isNullOrEmpty()) {
            val positions = customOrder.withIndex().associate { it.value to it.index }
            result = result.sortedWith(compareBy { positions[id(it)] ?: Int.MAX_VALUE })
        }
        return result
    }

    /** Missing provider categories remain in the saved order for the next successful sync. */
    fun move(previous: List<String>, availableIds: List<String>, categoryId: String,
        offset: Int = 0, position: Int? = null): List<String>? {
        val available = availableIds.toSet()
        val ordered = (previous.filter { it in available } + availableIds).distinct().toMutableList()
        val from = ordered.indexOf(categoryId)
        if (from < 0) return null
        val to = (position ?: (from + offset)).coerceIn(0, ordered.lastIndex)
        if (to == from) return null
        ordered.add(to, ordered.removeAt(from))
        return ordered + previous.filter { it !in available }
    }
}
