package rsv.squitv.core.ui.components.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/** Title/subtitle drawing from Android AppHeader. Platforms retain their own action/drag areas. */
@Composable
fun PageHeadingText(title: String, subtitle: String?, primary: Color, secondary: Color,
    titleStyle: TextStyle, subtitleStyle: TextStyle, modifier: Modifier = Modifier,
    titleMaxLines: Int = Int.MAX_VALUE, subtitleMaxLines: Int = Int.MAX_VALUE) {
    Column(modifier) {
        if (title.isNotBlank()) Text(title.uppercase(), style = titleStyle, fontWeight = FontWeight.Black,
            color = primary, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) Text(subtitle.uppercase(), style = subtitleStyle, color = secondary,
            maxLines = subtitleMaxLines, overflow = TextOverflow.Ellipsis)
    }
}
