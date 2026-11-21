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
        val byProvider = mutableMapOf<String, MutableList<T>>()
        val unclassified = mutableListOf<T>()
        fun addToProvider(item: T) {
            val id = categoryId(item)
            val knownId = id != null && id in known
            if (knownId) byProvider.getOrPut(id) { mutableListOf() }.add(item)
            if (!knownId || id.isNullOrBlank()) unclassified.add(item)
        }
        if (mode == CategoryMode.PROVIDER) {
            items.forEach(::addToProvider)
            val providers = providerCategories.mapNotNull { (id, name) ->
                byProvider[id]?.let { CatalogCategoryEntry(id, name, it) }
            }
            return listOf(all) + providers + if (unclassified.isEmpty()) emptyList()
                else listOf(CatalogCategoryEntry(UNCLASSIFIED, unclassifiedLabel, unclassified))
        }
        val byGenre = linkedMapOf<String, MutableList<T>>()
        val genreCache = HashMap<String, List<String>>()
        items.forEach { item ->
            val rawGenre = genre(item)
            val genres = if (rawGenre.isNullOrBlank()) emptyList() else genreCache[rawGenre]
                ?: GenreUtils.extractNormalizedGenres(rawGenre).also {
                    if (genreCache.size < 256) genreCache[rawGenre] = it
                }
            if (genres.isEmpty()) addToProvider(item)
            else genres.forEach { byGenre.getOrPut(it) { mutableListOf() }.add(item) }
        }
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val order = Comparator<CatalogCategoryEntry<T>> { a, b ->
            collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.id.compareTo(b.id)
        }
        val genres = byGenre.map { (name, values) -> CatalogCategoryEntry(GENRE_PREFIX + name, name, values.toList()) }.sortedWith(order)
        val fallback = providerCategories.mapNotNull { (id, name) ->
            byProvider[id]?.let { CatalogCategoryEntry("FALLBACK:$id", name, it) }
        }.sortedWith(order)
        return buildList {
            add(all)
            if (genres.isNotEmpty()) { add(CatalogCategoryEntry(HEADER_GENRES, genresHeaderLabel, emptyList(), true)); addAll(genres) }
            if (fallback.isNotEmpty()) { add(CatalogCategoryEntry(HEADER_SERVER, serverHeaderLabel, emptyList(), true)); addAll(fallback) }
            if (unclassified.isNotEmpty()) add(CatalogCategoryEntry(UNCLASSIFIED, unclassifiedLabel, unclassified))
        }
    }
}
