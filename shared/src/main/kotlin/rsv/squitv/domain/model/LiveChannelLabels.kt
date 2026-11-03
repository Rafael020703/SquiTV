package rsv.squitv.domain.model

import java.util.Locale

/** Android channel label rules shared with desktop. Labels are provider metadata, not measured video resolution. */
object LiveChannelLabels {
    private val suffix = Regex(
        """(?:\s+|\s*[\[({|/\-]\s*)(SD|HD|720P|FHD|1080P|UHD|4K|2160P|H[.]?264|H[.]?265|HEVC|AVC)[¹²³⁴⁵⁶⁷⁸⁹⁰]*\s*[\])}|]?$""",
        RegexOption.IGNORE_CASE
    )
    private val prefix = Regex(
        """^(?:[\[({]\s*(SD|HD|720P|FHD|1080P|UHD|4K|2160P|H[.]?264|H[.]?265|HEVC|AVC)[¹²³⁴⁵⁶⁷⁸⁹⁰]*\s*[\])}]\s*|(SD|HD|720P|FHD|1080P|UHD|4K|2160P|H[.]?264|H[.]?265|HEVC|AVC)[¹²³⁴⁵⁶⁷⁸⁹⁰]*\s*[|:/\-]\s*)""",
        RegexOption.IGNORE_CASE
    )
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
}
