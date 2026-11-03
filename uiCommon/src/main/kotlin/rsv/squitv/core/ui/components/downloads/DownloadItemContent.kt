package rsv.squitv.core.ui.components.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** Android download-row layout; platform adapters own thumbnails, storage and actions. */
@Composable
fun DownloadItemContent(title: String, subtitle: String, titleStyle: TextStyle, subtitleStyle: TextStyle,
    titleColor: Color, subtitleColor: Color, padding: Dp, gap: Dp,
    thumbnail: @Composable () -> Unit, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.padding(padding), verticalAlignment = Alignment.CenterVertically) {
        thumbnail()
        Spacer(Modifier.width(gap))
        Column(Modifier.weight(1f)) {
            Text(title, style = titleStyle, color = titleColor, fontWeight = FontWeight.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = .5.sp)
            Text(subtitle, style = subtitleStyle, color = subtitleColor, fontWeight = FontWeight.Bold)
        }
        actions()
    }
}
