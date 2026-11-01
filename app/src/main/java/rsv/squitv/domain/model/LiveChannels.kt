package rsv.squitv.domain.model

import kotlinx.serialization.Serializable
import java.util.Locale

enum class ChannelQuality(val rank: Int) {
    AUTO(0), SD(1), HD(2), FHD(3), UHD(4);
    companion object {
        fun fromPreference(value: String) = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

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
    private val suffix = Regex(
        """(?:\s+|\s*[\[({|/\-]\s*)(SD|HD|720P|FHD|1080P|UHD|4K|2160P|H[.]?264|H[.]?265|HEVC|AVC)[¹²³⁴⁵⁶⁷⁸⁹⁰]*\s*[\])}|]?$""",
        RegexOption.IGNORE_CASE
    )
    private val prefix = Regex(
        """^(?:[\[({]\s*(SD|HD|720P|FHD|1080P|UHD|4K|2160P|H[.]?264|H[.]?265|HEVC|AVC)[¹²³⁴⁵⁶⁷⁸⁹⁰]*\s*[\])}]\s*|(SD|HD|720P|FHD|1080P|UHD|4K|2160P|H[.]?264|H[.]?265|HEVC|AVC)[¹²³⁴⁵⁶⁷⁸⁹⁰]*\s*[|:/\-]\s*)""",
        RegexOption.IGNORE_CASE
    )
    private val epgSuffix = Regex(
        """[._\s|/\-]+(SD|HD|720P|FHD|1080P|UHD|4K|2160P|H[.]?264|H[.]?265|HEVC|AVC)[¹²³⁴⁵⁶⁷⁸⁹⁰]*$""",
        RegexOption.IGNORE_CASE
    )
    private val whitespace = Regex("""\s+""")
    data class Label(val name: String, val resolution: ChannelQuality?, val codec: String?)

    fun parse(name: String): Label {
        var base = name.trim()
        var quality: ChannelQuality? = null
        var codec: String? = null
        while (true) {
            val ending = suffix.find(base)
            val beginning = if (ending == null) prefix.find(base) else null
            val match = ending ?: beginning ?: break
            val tag = match.groupValues.drop(1).first { it.isNotEmpty() }.uppercase(Locale.ROOT)
            val resolution = when (tag) {
                "SD" -> ChannelQuality.SD
                "HD", "720P" -> ChannelQuality.HD
                "FHD", "1080P" -> ChannelQuality.FHD
                "UHD", "4K", "2160P" -> ChannelQuality.UHD
                else -> null
            }
            val encoding = when (tag) {
                "H264", "H.264", "AVC" -> "H.264"
                "H265", "H.265", "HEVC" -> "H.265"
                else -> null
            }
            // Ambiguous/conflicting labels must not erase part of a channel's identity.
            if (resolution != null && quality != null && quality != resolution) break
            if (encoding != null && codec != null && codec != encoding) break
            quality = quality ?: resolution
            codec = codec ?: encoding
            base = if (ending != null) base.substring(0, match.range.first).trim()
                else base.substring(match.range.last + 1).trim()
        }
        return Label(base, quality, codec)
    }

    private fun normalized(value: String) = value.trim().replace(whitespace, " ").uppercase(Locale.ROOT)

    fun version(item: IptvItem): ChannelVersion {
        val label = parse(item.name)
        return ChannelVersion(item.id.toInt(), item.name, label.resolution, label.codec,
            item.epgId, item.icon, item.categoryId, item.containerExtension)
    }

    private fun epgIdentity(id: String?): String {
        var value = normalized(id.orEmpty())
        while (true) {
            val suffix = epgSuffix.find(value) ?: break
            value = value.substring(0, suffix.range.first)
        }
        return value
    }

    /** Rebuild from real versions every time; grouping is reversible and idempotent. */
    fun group(items: List<IptvItem>, scope: String, preferred: ChannelQuality = ChannelQuality.AUTO): List<IptvItem> {
        val buckets = linkedMapOf<List<String>, MutableList<IptvItem>>()
        expand(items).forEach { item ->
            val base = parse(item.name).name
            val key = if (item.type != ContentType.LIVE || item.id.toIntOrNull() == null)
                listOf("item", item.type.name, item.id)
            else listOf(scope, item.categoryId.orEmpty(), normalized(base), if (base.isBlank()) item.id else "")
            buckets.getOrPut(key) { mutableListOf() }.add(item)
        }
        return buckets.flatMap { (key, members) ->
            val first = members.first()
            if (first.type != ContentType.LIVE || first.id.toIntOrNull() == null) listOf(first) else {
                val identities = members.map { epgIdentity(it.epgId) }.filter { it.isNotEmpty() }.distinct()
                // A missing EPG ID is not a different channel. Attach it only to an unambiguous family.
                val families = members.groupBy { if (identities.size <= 1) identities.firstOrNull().orEmpty() else epgIdentity(it.epgId) }
                families.map { (epg, family) ->
                    val versions = family.map(::version).distinctBy { it.streamId }.sortedBy { it.streamId }
                    val selected = choose(versions, preferred)
                    val canonical = family.minBy { it.id.toInt() }
                    selected.toItem().copy(name = parse(canonical.name).name.ifBlank { canonical.name },
                        versions = versions, groupKey = (key + epg).joinToString("") { "${it.length}:$it" },
                        isFavorite = family.any { it.isFavorite })
                }
            }
        }
    }

    /** Exact, lower resolutions descending, higher ascending, then unlabelled; ties use provider ID. */
    fun choose(versions: List<ChannelVersion>, preferred: ChannelQuality): ChannelVersion {
        require(versions.isNotEmpty())
        val ranks = if (preferred == ChannelQuality.AUTO) listOf(4, 3, 2, 1, 0)
            else listOf(preferred.rank) + (preferred.rank - 1 downTo 1) +
                (preferred.rank + 1..4) + listOf(0)
        return versions.minWith(compareBy<ChannelVersion> {
            ranks.indexOf(it.resolution?.rank ?: 0)
        }.thenBy { it.streamId })
    }

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
    require(size > 0)
    if (current < 0) return if (offset < 0) size - 1 else 0
    return ((current + offset) % size + size) % size
}
