package rsv.squitv.shared.catalog

import java.text.Collator
import java.util.Locale
import rsv.squitv.domain.model.CategoryMode
import rsv.squitv.domain.usecase.GenreUtils

data class CatalogCategoryEntry<T>(val id: String, val name: String, val items: List<T>, val isHeader: Boolean = false)

/** Original Android ContentCategories catalog projection; platform adapters own storage/counts. */
object CatalogCategoryGrouping {
    const val ALL = "ALL"
    const val UNCLASSIFIED = "UNCLASSIFIED"
    const val GENRE_PREFIX = "GENRE:"
    const val HEADER_GENRES = "HEADER_GENRES"
    const val HEADER_SERVER = "HEADER_SERVER"

    fun <T> catalog(items: List<T>, providerCategories: List<Pair<String, String>>, allLabel: String,
        unclassifiedLabel: String, genresHeaderLabel: String, serverHeaderLabel: String,
        mode: CategoryMode, locale: Locale, categoryId: (T) -> String?, genre: (T) -> String?): List<CatalogCategoryEntry<T>> {
        if (items.isEmpty()) return emptyList()
        val all = CatalogCategoryEntry(ALL, allLabel, items)
        val known = providerCategories.map { it.first }.toSet()
        if (mode == CategoryMode.PROVIDER) {
            val grouped = items.groupBy(categoryId)
            val providers = providerCategories.mapNotNull { (id, name) ->
                grouped[id]?.takeIf { it.isNotEmpty() }?.let { CatalogCategoryEntry(id, name, it) }
            }
            val unknown = items.filter { categoryId(it).isNullOrBlank() || categoryId(it) !in known }
            return listOf(all) + providers + if (unknown.isEmpty()) emptyList()
                else listOf(CatalogCategoryEntry(UNCLASSIFIED, unclassifiedLabel, unknown))
        }
        val byGenre = linkedMapOf<String, MutableList<T>>()
        val unknown = mutableListOf<T>()
        items.forEach { item ->
            val genres = GenreUtils.extractNormalizedGenres(genre(item))
            if (genres.isEmpty()) unknown.add(item)
            else genres.forEach { byGenre.getOrPut(it) { mutableListOf() }.add(item) }
        }
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val order = Comparator<CatalogCategoryEntry<T>> { a, b ->
            collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.id.compareTo(b.id)
        }
        val genres = byGenre.map { (name, values) -> CatalogCategoryEntry(GENRE_PREFIX + name, name, values.toList()) }.sortedWith(order)
        val unknownByCategory = unknown.groupBy(categoryId)
        val fallback = providerCategories.mapNotNull { (id, name) ->
            unknownByCategory[id]?.takeIf { it.isNotEmpty() }?.let { CatalogCategoryEntry("FALLBACK:$id", name, it) }
        }.sortedWith(order)
        val unclassified = unknown.filter { categoryId(it).isNullOrBlank() || categoryId(it) !in known }
        return buildList {
            add(all)
            if (genres.isNotEmpty()) { add(CatalogCategoryEntry(HEADER_GENRES, genresHeaderLabel, emptyList(), true)); addAll(genres) }
            if (fallback.isNotEmpty()) { add(CatalogCategoryEntry(HEADER_SERVER, serverHeaderLabel, emptyList(), true)); addAll(fallback) }
            if (unclassified.isNotEmpty()) add(CatalogCategoryEntry(UNCLASSIFIED, unclassifiedLabel, unclassified))
        }
    }
}
