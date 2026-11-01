package rsv.squitv.core.ui.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import rsv.squitv.R
import rsv.squitv.domain.model.ChannelQuality

@Composable
fun channelQualityLabel(quality: ChannelQuality): String = stringResource(when (quality) {
    ChannelQuality.AUTO -> R.string.channel_quality_auto
    ChannelQuality.SD -> R.string.channel_quality_sd
    ChannelQuality.HD -> R.string.channel_quality_hd
    ChannelQuality.FHD -> R.string.channel_quality_fhd
    ChannelQuality.UHD -> R.string.channel_quality_uhd
})

@Composable
fun ChannelQualitySettings(
    grouped: Boolean,
    preferred: String,
    onGroupingChanged: (Boolean) -> Unit,
    onQualityChanged: (String) -> Unit
) {
    SettingsToggle(label = stringResource(R.string.group_live_channels), checked = grouped,
        onCheckedChange = onGroupingChanged)
    androidx.compose.material3.Text(stringResource(R.string.preferred_channel_quality),
        color = rsv.squitv.core.ui.theme.AppDesignSystem.colors.textPrimary)
    ChannelQuality.entries.forEach { quality ->
        SettingsActionItem(label = channelQualityLabel(quality),
            trailingText = if (preferred == quality.name) stringResource(R.string.channel_selected) else null,
            onClick = { onQualityChanged(quality.name) })
    }
    androidx.compose.material3.Text(stringResource(R.string.channel_quality_hint),
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = rsv.squitv.core.ui.theme.AppDesignSystem.colors.textSecondary)
}
