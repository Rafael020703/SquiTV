package rsv.squitv.core.ui.components.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original Android transport geometry: equal wings keep play/pause at the exact center. */
@Composable
fun CenteredPlayerActions(gap: Dp, modifier: Modifier = Modifier,
    left: @Composable RowScope.() -> Unit, center: @Composable () -> Unit,
    right: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically, content = left)
        Spacer(Modifier.width(gap))
        center()
        Spacer(Modifier.width(gap))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically, content = right)
    }
}

/** Compact zapping card extracted from Android PlayerZapping; image loading stays platform-specific. */
@Composable
fun CompactChannelBanner(name: String, number: Int?, programme: String?, progress: Float,
    expanded: Boolean, background: Color, primary: Color, text: Color, secondary: Color,
    titleStyle: TextStyle, bodyStyle: TextStyle, labelStyle: TextStyle,
    modifier: Modifier = Modifier, logo: @Composable () -> Unit) {
    Surface(modifier.semantics(mergeDescendants = true) {}, color = background.copy(alpha = .90f),
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
        tonalElevation = 0.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            logo()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    number?.takeIf { it > 0 }?.let {
                        Text(it.toString(), Modifier.widthIn(max = 44.dp), style = labelStyle.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.SemiBold, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(name.trim(), Modifier.weight(1f), style = titleStyle.copy(fontSize = if (expanded) 17.sp else 15.sp),
                        fontWeight = FontWeight.SemiBold, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                programme?.trim()?.takeIf(String::isNotEmpty)?.let {
                    Text(it, style = bodyStyle.copy(fontSize = if (expanded) 13.sp else 12.sp),
                        color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                        color = primary.copy(alpha = .8f), trackColor = Color.White.copy(alpha = .08f),
                        gapSize = 0.dp, drawStopIndicator = {})
                }
            }
        }
    }
}
