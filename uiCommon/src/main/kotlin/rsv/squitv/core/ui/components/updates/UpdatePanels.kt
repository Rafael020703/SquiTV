package rsv.squitv.core.ui.components.updates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original Android update panels, with platform resources and actions supplied by the host. */
@Composable
fun UpdateStatusPanel(title: String, description: String, icon: ImageVector,
    surface: Color, border: Color, text: Color, secondary: Color, accent: Color,
    titleStyle: TextStyle, bodyStyle: TextStyle, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = surface,
        border = BorderStroke(1.dp, border)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(52.dp).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(28.dp), tint = accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = titleStyle.copy(fontSize = 22.sp), fontWeight = FontWeight.Bold, color = text)
                    Text(description, style = bodyStyle.copy(fontSize = 14.sp), color = secondary)
                }
            }
            content()
        }
    }
}

@Composable
fun UpdateVersionPanel(currentLabel: String, current: String, availableLabel: String, available: String?,
    background: Color, text: Color, secondary: Color, primary: Color, titleStyle: TextStyle, captionStyle: TextStyle) {
    Surface(shape = RoundedCornerShape(12.dp), color = background) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(currentLabel, color = secondary, style = captionStyle)
                Text(current, color = text, style = titleStyle)
            }
            if (available != null) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(availableLabel, color = secondary, style = captionStyle)
                Text(available, color = primary, style = titleStyle)
            }
        }
    }
}
