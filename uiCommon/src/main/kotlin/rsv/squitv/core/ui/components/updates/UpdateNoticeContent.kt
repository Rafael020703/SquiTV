package rsv.squitv.core.ui.components.updates

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared Android update notice presentation. The host owns lifecycle, countdown and actions. */
@Composable
fun UpdateNoticeContent(title: String, version: String, description: String, timeout: String,
    silence: String, remaining: Int, primary: Color, text: Color, secondary: Color,
    titleStyle: TextStyle, labelStyle: TextStyle, bodyStyle: TextStyle, captionStyle: TextStyle,
    onIgnore: () -> Unit, ignoreModifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = primary, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = text, style = titleStyle, fontWeight = FontWeight.Bold)
                Text(version, color = primary, style = labelStyle)
            }
        }
        Text(description, color = secondary, style = bodyStyle)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
        LinearProgressIndicator(progress = { remaining.coerceIn(0, 10) / 10f }, modifier = Modifier.fillMaxWidth(), color = primary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(timeout, color = secondary, style = captionStyle)
            TextButton(onIgnore, modifier = Modifier.heightIn(min = 48.dp).then(ignoreModifier)) { Text(silence, color = secondary, style = captionStyle) }
        }
    }
}
