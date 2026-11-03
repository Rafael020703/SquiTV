package rsv.squitv.core.ui.components.badges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.*

@Composable
fun AgeBadge(age: String, modifier: Modifier = Modifier) {
    val tokens = AppDesignSystem
    val color = when {
        age.contains("18") || age.contains("R") -> tokens.colors.error
        age.contains("16") -> Color(0xFFFF9100)
        age.contains("14") -> tokens.colors.warning
        age.contains("12") -> Color(0xFFAEEA00)
        age.contains("10") -> tokens.colors.primary
        age.contains("L") || age.contains("G") -> tokens.colors.success
        else -> tokens.colors.textSecondary
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.5.dp, color),
        modifier = modifier
    ) {
        Text(
            text = age.uppercase(),
            modifier = Modifier.padding(horizontal = tokens.spacing.small, vertical = tokens.spacing.micro),
            style = tokens.typography.label,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun RatingBadge(rating: String, modifier: Modifier = Modifier) {
    val tokens = AppDesignSystem
    rsv.squitv.core.ui.components.cards.RatingBadgeContent(rating, tokens.typography.label, modifier,
        tokens.spacing.small, tokens.spacing.micro, tokens.spacing.tiny)
}

@Composable
fun GenreBadge(genre: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        color = if (isFocused) tokens.colors.primary.copy(alpha = 0.2f) else tokens.colors.surface.copy(alpha = 0.1f),
        shape = tokens.shapes.medium,
        modifier = modifier.adaptiveFocus(shape = tokens.shapes.medium, onFocus = { isFocused = it })
    ) {
        Text(
            text = genre.uppercase(),
            modifier = Modifier.padding(horizontal = tokens.spacing.large, vertical = tokens.spacing.small),
            style = tokens.typography.label,
            color = if (isFocused) tokens.colors.primary else tokens.colors.textPrimary,
            fontWeight = FontWeight.Black
        )
    }
}
