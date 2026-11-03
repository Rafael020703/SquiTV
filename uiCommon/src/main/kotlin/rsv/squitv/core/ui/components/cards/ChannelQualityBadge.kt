package rsv.squitv.core.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.domain.model.ChannelQuality

/** Provider quality label from the Android card; unknown labels do not create a badge. */
@Composable
fun ChannelQualityBadge(
    quality: ChannelQuality?,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    successColor: Color = Color(0xFF00E676),
    shape: Shape = RoundedCornerShape(4.dp),
    textStyle: TextStyle = MaterialTheme.typography.labelSmall
) {
    val label = when (quality) {
        null, ChannelQuality.AUTO -> return
        ChannelQuality.UHD -> "4K"
        else -> quality.name
    }
    val color = when (quality) {
        ChannelQuality.UHD -> Color(0xFFFFD700)
        ChannelQuality.FHD -> primaryColor
        ChannelQuality.HD -> successColor
        else -> secondaryColor
    }
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = shape,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = textStyle,
            fontWeight = FontWeight.Black,
            color = color,
            fontSize = 9.sp
        )
    }
}
