package rsv.squitv.core.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Original Android card favorite: 16 dp icon, 24 dp background, 48 dp input target. */
@Composable
fun CompactFavoriteButton(isFavorite: Boolean, description: String, onClick: () -> Unit,
    background: Color, favoriteColor: Color, foreground: Color, modifier: Modifier = Modifier,
    enabled: Boolean = true, interactionSource: MutableInteractionSource? = null) {
    IconButton(onClick, modifier.size(48.dp), enabled = enabled, interactionSource = interactionSource) {
        Box(Modifier.size(24.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
            val tint = if (isFavorite) favoriteColor else foreground
            Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, description,
                Modifier.size(16.dp), tint = if (enabled) tint else tint.copy(alpha = .38f))
        }
    }
}
