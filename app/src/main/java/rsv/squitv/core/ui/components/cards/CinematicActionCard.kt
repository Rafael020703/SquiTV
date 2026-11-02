package rsv.squitv.core.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.core.ui.theme.ThemeLive

@Composable
fun CinematicActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    cardType: HomeCardType = HomeCardType.LIVE_TV,
    themeColor: Color = ThemeLive,
    isLarge: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    
    val contentAlpha by animateFloatAsState(if (isFocused) 1f else 0.85f, label = "contentAlpha")
    
    // Adaptive Dimensions
    val cardPadding = responsive.dp(tokens.spacing.large)
    val iconBoxSize = responsive.dp(if (isLarge) 56.dp else 44.dp)
    val iconSize = responsive.dp(if (isLarge) 32.dp else 24.dp)
    val actionIndicatorSize = responsive.dp(if (isLarge) 44.dp else 36.dp)
    val actionIconSize = responsive.dp(if (isLarge) 28.dp else 22.dp)
    
    val titleSize = responsive.sp(if (isLarge) 28.sp else 18.sp)
    val descSize = responsive.sp(tokens.typography.body.fontSize)

    Surface(
        modifier = modifier
            .adaptiveFocus(
                shape = tokens.shapes.large,
                glowColor = themeColor,
                focusedScale = if (isLarge) 1.04f else 1.06f,
                onFocus = { isFocused = it }
            )
            .clickable { onClick() },
        shape = tokens.shapes.large,
        color = Color(0xFF0F131C)
    ) {
        CinematicCardContent(
            title, description, icon, cardType, themeColor, isFocused, contentAlpha,
            tokens.colors.textPrimary, tokens.colors.textSecondary,
            if (isLarge) tokens.typography.display else tokens.typography.headline, tokens.typography.body,
            tokens.shapes.large, tokens.shapes.medium, cardPadding, responsive.dp(tokens.spacing.medium),
            iconBoxSize, iconSize, actionIndicatorSize, actionIconSize, titleSize, descSize
        )
    }
}
