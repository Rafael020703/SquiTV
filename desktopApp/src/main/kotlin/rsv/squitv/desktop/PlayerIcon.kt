package rsv.squitv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerIcon(icon: ImageVector, label: String, action: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = rememberVisualInteraction()
    IconButton(onClick = action, interactionSource = interaction.source,
        modifier = modifier.then(if (interaction.emphasized) Modifier.background(palette.primary.copy(alpha = .1f), CircleShape)
            .border(1.dp, palette.primary.copy(alpha = .6f), CircleShape) else Modifier)) {
        Icon(icon, label, tint = if (interaction.emphasized) palette.primary else palette.onSurfaceVariant)
    }
}
