package rsv.squitv.desktop

import rsv.squitv.domain.model.ChannelQuality

/** Portuguese labels matching the Android resources; preferences retain the shared enum names. */
internal fun ChannelQuality.desktopLabel(): String = when (this) {
    ChannelQuality.AUTO -> "Automático"
    ChannelQuality.SD -> "SD"
    ChannelQuality.HD -> "HD"
    ChannelQuality.FHD -> "Full HD"
    ChannelQuality.UHD -> "4K"
}
