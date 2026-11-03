package rsv.squitv.core.ui.components.cards

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rsv.squitv.R
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.AppDesignSystem

@Composable
internal fun CardFavoriteButton(isFavorite: Boolean, contentName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = AppDesignSystem
    CompactFavoriteButton(
        isFavorite = isFavorite,
        description = "$contentName: ${stringResource(if (isFavorite) R.string.quick_favorite_remove else R.string.quick_favorite_add)}",
        background = tokens.colors.background.copy(alpha = 0.68f),
        favoriteColor = tokens.colors.error,
        foreground = tokens.colors.textPrimary,
        onClick = onClick,
        modifier = modifier.size(48.dp)
            .adaptiveFocus(shape = CircleShape, focusedScale = 1f)
    )
}
