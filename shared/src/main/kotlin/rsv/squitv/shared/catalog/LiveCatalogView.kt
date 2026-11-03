package rsv.squitv.shared.catalog

import rsv.squitv.domain.model.ChannelQuality
import rsv.squitv.shared.xtream.*

data class LiveCategoryCount(val id: String, val name: String, val count: Int)
data class LiveCatalogView(val categories: List<LiveCategoryCount> = emptyList(),
    val channels: List<LiveChannel> = emptyList(), val knownCategoryIds: Set<String> = emptySet()) {
    fun inCategory(id: String): List<LiveChannel> = if (id == UNCLASSIFIED)
        channels.filter { it.categoryId !in knownCategoryIds } else channels.filter { it.categoryId == id }
    companion object {
        const val UNCLASSIFIED = "SPECIAL:UNCLASSIFIED"
        fun build(categories: List<LiveCategory>, raw: List<LiveChannel>, scope: String, grouped: Boolean,
            quality: ChannelQuality, order: List<String>, hidden: Set<String>): LiveCatalogView {
            val visible = LiveChannelGroups.visible(raw.filter { it.categoryId !in hidden }, scope, grouped, quality)
            val counts = visible.groupingBy { it.categoryId }.eachCount()
            val known = categories.map { it.id }.toSet()
            val choices = CategoryCustomization.apply(categories, order, hidden) { it.id }
                .mapNotNull { category -> counts[category.id]?.takeIf { it > 0 }?.let { LiveCategoryCount(category.id, category.name, it) } }
            val unknown = visible.count { it.categoryId !in known }
            return LiveCatalogView(choices + if (unknown > 0) listOf(LiveCategoryCount(UNCLASSIFIED, "Sem classificação", unknown)) else emptyList(),
                visible, known)
        }
    }
}
