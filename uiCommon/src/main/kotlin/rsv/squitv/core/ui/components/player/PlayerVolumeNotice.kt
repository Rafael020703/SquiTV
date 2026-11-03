package rsv.squitv.core.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Noninteractive HUD shared by Media3 and LibVLC; it never captures focus. */
@Composable
fun PlayerVolumeNotice(percent: Int, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
        shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (percent == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                "Volume do aplicativo")
            Text("$percent%", style = MaterialTheme.typography.titleMedium)
        }
    }
}
