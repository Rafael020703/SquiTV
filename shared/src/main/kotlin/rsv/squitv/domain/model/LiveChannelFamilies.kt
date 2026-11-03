package rsv.squitv.domain.model

import java.util.Locale

/** Platform-neutral inputs. Adapters retain their own catalog and favorite models. */
data class ChannelCandidate<T>(val value: T, val id: String, val name: String,
    val categoryId: String?, val epgId: String?, val type: String = "LIVE", val live: Boolean = true) {
    internal val label = LiveChannelLabels.parse(name)
}

data class ChannelFamily<T>(val selected: T, val name: String, val versions: List<T>, val groupKey: String?,
    val members: List<T>)

/** Original Android grouping boundaries and deterministic quality selection, without UI/storage. */
object LiveChannelFamilies {
    private val epgSuffix = Regex(
        """[._\s|/\-]+(SD|HD|720P|FHD|1080P|UHD|4K|2160P|H[.]?264|H[.]?265|HEVC|AVC)[¹²³⁴⁵⁶⁷⁸⁹⁰]*$""",
        RegexOption.IGNORE_CASE
    )
    private val whitespace = Regex("""\s+""")
    private fun normalized(value: String) = value.trim().replace(whitespace, " ").uppercase(Locale.ROOT)
    private fun epgIdentity(id: String?): String {
        var value = normalized(id.orEmpty())
        while (true) {
            val suffix = epgSuffix.find(value) ?: break
            value = value.substring(0, suffix.range.first)
        }
        return value
    }

    fun <T> group(items: List<ChannelCandidate<T>>, scope: String,
        preferred: ChannelQuality = ChannelQuality.AUTO): List<ChannelFamily<T>> {
        val buckets = linkedMapOf<List<String>, MutableList<ChannelCandidate<T>>>()
        items.forEach { item ->
            val base = item.label.name
            val key = if (!item.live || item.id.toIntOrNull() == null) listOf("item", item.type, item.id)
                else listOf(scope, item.categoryId.orEmpty(), normalized(base), if (base.isBlank()) item.id else "")
            buckets.getOrPut(key) { mutableListOf() }.add(item)
        }
        return buckets.flatMap { (key, members) ->
            val first = members.first()
            if (!first.live || first.id.toIntOrNull() == null)
                listOf(ChannelFamily(first.value, first.name, emptyList(), null, listOf(first.value)))
            else {
                val identities = members.map { epgIdentity(it.epgId) }.filter { it.isNotEmpty() }.distinct()
                // Missing EPG IDs join a family only when the known identity is unambiguous.
                val families = members.groupBy {
                    if (identities.size <= 1) identities.firstOrNull().orEmpty() else epgIdentity(it.epgId)
                }
                families.map { (epg, family) ->
                    val versions = family.distinctBy { it.id.toInt() }.sortedBy { it.id.toInt() }
                    val selected = choose(versions, preferred, { it.label.resolution }, { it.id.toInt() })
                    val canonical = family.minBy { it.id.toInt() }
                    ChannelFamily(selected.value, canonical.label.name.ifBlank { canonical.name },
                        versions.map { it.value }, (key + epg).joinToString("") { "${it.length}:$it" },
                        family.map { it.value })
                }
            }
        }
    }

    /** Exact, lower descending, higher ascending, unknown last; AUTO prefers the highest label. */
    fun <T> choose(versions: List<T>, preferred: ChannelQuality,
        resolution: (T) -> ChannelQuality?, streamId: (T) -> Int): T {
        require(versions.isNotEmpty())
        val ranks = if (preferred == ChannelQuality.AUTO) listOf(4, 3, 2, 1, 0)
            else listOf(preferred.rank) + (preferred.rank - 1 downTo 1) + (preferred.rank + 1..4) + listOf(0)
        return versions.minWith(compareBy<T> { ranks.indexOf(resolution(it)?.rank ?: 0) }.thenBy(streamId))
    }
}
