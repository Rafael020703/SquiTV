package rsv.squitv.core.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Original Android rating badge, shared without Android theme or image dependencies. */
@Composable
fun RatingBadgeContent(rating: String, style: TextStyle, modifier: Modifier = Modifier,
    horizontalPadding: Dp = 8.dp, verticalPadding: Dp = 2.dp, gap: Dp = 4.dp) {
    if (rating == "0" || rating.isEmpty()) return
    Surface(color = Color(0xFFFFD700), shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = Color.Black)
            Spacer(Modifier.width(gap))
            Text(rating, color = Color.Black, fontWeight = FontWeight.Black, style = style)
        }
    }
}
