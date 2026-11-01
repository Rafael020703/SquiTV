package rsv.squitv.core.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
            .adaptiveFocus(shape = CircleShape, focusedScale = 1f)
    ) {
        Box(Modifier.size(24.dp).background(tokens.colors.background.copy(alpha = 0.68f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = "$contentName: ${stringResource(if (isFavorite) R.string.quick_favorite_remove else R.string.quick_favorite_add)}",
            tint = if (isFavorite) tokens.colors.error else tokens.colors.textPrimary,
            modifier = Modifier.size(16.dp)
        )
        }
    }
}
