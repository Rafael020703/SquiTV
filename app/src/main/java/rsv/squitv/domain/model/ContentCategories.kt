package rsv.squitv.domain.model

import java.text.Collator
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
        if (items.isEmpty()) return emptyList()
        val all = entry(ALL, allLabel, items, scope, grouped, quality)

        if (items.first().type == ContentType.LIVE || mode == CategoryMode.PROVIDER) {
            val byCategory = items.groupBy { it.categoryId }
            val providerEntries = providerCategories.mapNotNull { (id, name) ->
                byCategory[id]?.takeIf { it.isNotEmpty() }?.let { entry(id, name, it, scope, grouped, quality) }
            }
            val knownCategoryIds = providerCategories.map { it.first }.toSet()
            val unclassifiedItems = items.filter { it.categoryId.isNullOrBlank() || it.categoryId !in knownCategoryIds }
            val unclassifiedEntry = if (unclassifiedItems.isNotEmpty()) {
                listOf(entry(UNCLASSIFIED, unclassifiedLabel, unclassifiedItems, scope, grouped, quality))
            } else emptyList()

            return listOf(all) + providerEntries + unclassifiedEntry
        }

        // CategoryMode.GENRE
        val providerCatMap = providerCategories.toMap()
        val knownCategoryIds = providerCatMap.keys
        val byGenre = linkedMapOf<String, MutableList<IptvItem>>()
        val unknownItems = mutableListOf<IptvItem>()

        items.forEach { item ->
            val genres = GenreUtils.extractNormalizedGenres(item.genre)
            if (genres.isEmpty()) {
                unknownItems.add(item)
            } else {
                genres.forEach { byGenre.getOrPut(it) { mutableListOf() }.add(item) }
            }
        }

        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }

        val genreCategories = byGenre.map { (genre, genreItems) ->
            entry(GENRE_PREFIX + genre, genre, genreItems, scope, grouped, quality)
        }.sortedWith { a, b ->
            val comp = collator.compare(a.name, b.name)
            if (comp != 0) comp else a.id.compareTo(b.id)
        }

        // Server category fallback for items without genre
        val unknownByCategory = unknownItems.groupBy { it.categoryId }
        val fallbackCategories = providerCategories.mapNotNull { (catId, catName) ->
            unknownByCategory[catId]?.takeIf { it.isNotEmpty() }?.let { catItems ->
                entry("FALLBACK:$catId", catName, catItems, scope, grouped, quality)
            }
        }.sortedWith { a, b ->
            val comp = collator.compare(a.name, b.name)
            if (comp != 0) comp else a.id.compareTo(b.id)
        }

        val trueUnclassified = unknownItems.filter { it.categoryId.isNullOrBlank() || it.categoryId !in knownCategoryIds }
        val unclassifiedEntry = if (trueUnclassified.isNotEmpty()) {
            listOf(entry(UNCLASSIFIED, unclassifiedLabel, trueUnclassified, scope, grouped, quality))
        } else emptyList()

        val result = mutableListOf<ContentCategory>()
        result.add(all)

        if (genreCategories.isNotEmpty()) {
            result.add(ContentCategory(HEADER_GENRES, genresHeaderLabel, emptyList(), isHeader = true))
            result.addAll(genreCategories)
        }

        if (fallbackCategories.isNotEmpty()) {
            result.add(ContentCategory(HEADER_SERVER, serverHeaderLabel, emptyList(), isHeader = true))
            result.addAll(fallbackCategories)
        }

        if (unclassifiedEntry.isNotEmpty()) {
            result.addAll(unclassifiedEntry)
        }

        return result
    }

    fun withSpecials(base: List<ContentCategory>, specials: List<ContentCategory>): List<ContentCategory> =
        base.take(1) + specials.filter { it.items.isNotEmpty() } + base.drop(1)

    /** Restored provider categories/empty lists fall back to actual content, without a second filter. */
    fun selected(entries: List<ContentCategory>, requested: String?): ContentCategory? =
        entries.filter { !it.isHeader }.let { valid ->
            valid.firstOrNull { it.id == requested }
                ?: valid.firstOrNull { requested != null && it.id == "FALLBACK:$requested" }
                ?: valid.firstOrNull { requested != null && requested.startsWith("FALLBACK:") && it.id == requested.removePrefix("FALLBACK:") }
                ?: valid.firstOrNull { it.id == ALL }
                ?: valid.firstOrNull()
        }
}
