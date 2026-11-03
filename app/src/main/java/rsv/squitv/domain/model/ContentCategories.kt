package rsv.squitv.domain.model

import java.util.Locale
import rsv.squitv.domain.usecase.GenreUtils

data class ContentCategory(
    val id: String,
    val name: String,
    val items: List<IptvItem>,
    val count: Int = items.size,
    val isHeader: Boolean = false
)

/** Each entry owns the exact items counted and opened by the UI. Never fabricates genre metadata. */
object ContentCategories {
    const val ALL = "ALL"
    const val UNCLASSIFIED = "UNCLASSIFIED"
    const val GENRE_PREFIX = "GENRE:"
    const val HEADER_GENRES = "HEADER_GENRES"
    const val HEADER_SERVER = "HEADER_SERVER"

    fun genreId(raw: String): String =
        GENRE_PREFIX + (GenreUtils.extractNormalizedGenres(raw).firstOrNull() ?: raw.trim())

    fun entry(id: String, name: String, items: List<IptvItem>, scope: String,
              grouped: Boolean, quality: ChannelQuality): ContentCategory =
        ContentCategory(id, name, items, LiveChannelGrouping.visible(items, scope, grouped, quality).size)

    fun catalog(
        items: List<IptvItem>,
        providerCategories: List<Pair<String, String>>,
        allLabel: String,
        unclassifiedLabel: String,
        genresHeaderLabel: String = "Filtros por gênero",
        serverHeaderLabel: String = "Categorias do servidor",
        scope: String,
        grouped: Boolean,
        quality: ChannelQuality,
        mode: CategoryMode = CategoryMode.PROVIDER,
        locale: Locale = Locale.getDefault()
    ): List<ContentCategory> {
        return rsv.squitv.shared.catalog.CatalogCategoryGrouping.catalog(items, providerCategories,
            allLabel, unclassifiedLabel, genresHeaderLabel, serverHeaderLabel,
            if (items.firstOrNull()?.type == ContentType.LIVE) CategoryMode.PROVIDER else mode,
            locale, { it.categoryId }, { it.genre }).map {
            if (it.isHeader) ContentCategory(it.id, it.name, emptyList(), isHeader = true)
            else entry(it.id, it.name, it.items, scope, grouped, quality)
        }
    }

    fun withSpecials(base: List<ContentCategory>, specials: List<ContentCategory>): List<ContentCategory> =
        base.take(1) + specials.filter { it.items.isNotEmpty() } + base.drop(1)

    /** Restored provider categories/empty lists fall back to actual content, without a second filter. */
    fun selected(entries: List<ContentCategory>, requested: String?): ContentCategory? =
        entries.filter { !it.isHeader }.let { valid ->
            val id = rsv.squitv.shared.catalog.CatalogBrowseMemory.selectCategory(valid.map { it.id }, requested)
            valid.firstOrNull { it.id == id }
        }
}
