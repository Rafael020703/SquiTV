package rsv.squitv.domain.model

import kotlinx.serialization.Serializable

/** Provider labels describe a different address, never an adaptive video track. */
@Serializable
data class ChannelVersion(
    val streamId: Int,
    val name: String,
    val resolution: ChannelQuality? = null,
    val codec: String? = null,
    val epgId: String? = null,
    val icon: String? = null,
    val categoryId: String? = null,
    val container: String? = "ts"
) {
    fun toItem() = IptvItem(streamId.toString(), name, icon, ContentType.LIVE,
        epgId = epgId, containerExtension = container, categoryId = categoryId)
}

/** Conservative suffix parser: region, language, edition and channel numbers stay in the name. */
object LiveChannelGrouping {
    fun parse(name: String): LiveChannelLabels.Label = LiveChannelLabels.parse(name)

    fun version(item: IptvItem): ChannelVersion {
        val label = parse(item.name)
        return ChannelVersion(item.id.toInt(), item.name, label.resolution, label.codec,
            item.epgId, item.icon, item.categoryId, item.containerExtension)
    }

    /** Rebuild from real versions every time; grouping remains reversible and idempotent. */
    fun group(items: List<IptvItem>, scope: String, preferred: ChannelQuality = ChannelQuality.AUTO): List<IptvItem> {
        val candidates = expand(items).map {
            ChannelCandidate(it, it.id, it.name, it.categoryId, it.epgId, it.type.name, it.type == ContentType.LIVE)
        }
        return LiveChannelFamilies.group(candidates, scope, preferred).map { family ->
            if (family.groupKey == null) family.selected else version(family.selected).toItem().copy(
                name = family.name, versions = family.versions.map(::version), groupKey = family.groupKey,
                isFavorite = family.members.any { it.isFavorite })
        }
    }

    fun choose(versions: List<ChannelVersion>, preferred: ChannelQuality): ChannelVersion =
        LiveChannelFamilies.choose(versions, preferred, { it.resolution }, { it.streamId })

    fun expand(items: List<IptvItem>): List<IptvItem> = items.flatMap { item ->
        if (item.versions.isEmpty()) listOf(item) else item.versions.map { it.toItem().copy(isFavorite = item.isFavorite && it.streamId.toString() == item.id) }
    }.distinctBy { it.type to it.id }

    fun adjacent(visible: List<IptvItem>, currentId: String?, offset: Int): IptvItem? {
        if (visible.isEmpty()) return null
        val current = visible.indexOfFirst { it.containsStream(currentId) }
        return visible[channelStepIndex(visible.size, current, offset)].takeUnless { it.containsStream(currentId) }
    }

    fun visible(items: List<IptvItem>, scope: String, grouped: Boolean, preferred: ChannelQuality) =
        if (grouped) group(items, scope, preferred) else expand(items)
}

/** Circular navigation within the supplied list; callers handle empty lists without a fallback. */
fun channelStepIndex(size: Int, current: Int, offset: Int): Int {
    return rsv.squitv.shared.xtream.LivePlayback.stepIndex(size, current, offset)
}
