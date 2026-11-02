package rsv.squitv.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Same restrained error treatment as Android LoginScreen; messages are supplied by existing state. */
@Composable
internal fun StatusNotice(message: String, error: Boolean = false) {
    val tint = if (error) palette.error else palette.onSurfaceVariant
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = .08f),
        border = BorderStroke(1.dp, tint.copy(alpha = .3f))) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                null, Modifier.size(18.dp), tint = tint)
            Text(message, color = tint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}
