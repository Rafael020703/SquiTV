package rsv.squitv.core.ui.components.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NextEpisodeNotice(title: String, seconds: String, onNow: () -> Unit, onCancel: () -> Unit,
    background: Color, foreground: Color, primary: Color, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
    Surface(modifier.width(340.dp), color = background, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, primary.copy(alpha = .35f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = foreground)
            Text(seconds, style = MaterialTheme.typography.headlineSmall, color = primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onNow, Modifier.weight(1f).focusRequester(focus)) { Text("Assistir agora") }
                OutlinedButton(onCancel) { Text("Cancelar") }
            }
        }
    }
}
