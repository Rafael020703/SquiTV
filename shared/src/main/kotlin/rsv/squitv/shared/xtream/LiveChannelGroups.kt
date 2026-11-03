package rsv.squitv.shared.xtream

import rsv.squitv.domain.model.ChannelCandidate
import rsv.squitv.domain.model.ChannelQuality
import rsv.squitv.domain.model.LiveChannelFamilies

/** Xtream adapter for the same grouping used by the Android catalog and player. */
object LiveChannelGroups {
    fun expand(channels: List<LiveChannel>): List<LiveChannel> = channels.flatMap {
        it.versions.ifEmpty { listOf(it) }
    }.distinctBy { it.id }

    fun visible(channels: List<LiveChannel>, scope: String, grouped: Boolean,
        preferred: ChannelQuality = ChannelQuality.AUTO): List<LiveChannel> {
        val raw = expand(channels)
        if (!grouped) return raw
        return LiveChannelFamilies.group(raw.map {
            ChannelCandidate(it, it.id.toString(), it.name, it.categoryId, it.epgId)
        }, scope, preferred).map { family ->
            family.selected.copy(name = family.name, versions = family.versions, groupKey = family.groupKey)
        }
    }

    /** Preserve the displayed family while changing only its selected real provider address. */
    fun select(channel: LiveChannel, streamId: Int?): LiveChannel? {
        if (streamId == null) return null
        if (channel.versions.isEmpty()) return channel.takeIf { it.id == streamId }
        val version = channel.versions.firstOrNull { it.id == streamId } ?: return null
        return version.copy(name = channel.name, versions = channel.versions, groupKey = channel.groupKey)
    }
}
