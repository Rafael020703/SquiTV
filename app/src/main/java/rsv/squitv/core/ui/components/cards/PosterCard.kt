package rsv.squitv.core.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import rsv.squitv.core.ui.theme.*
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.components.badges.RatingBadge

@Composable
fun PosterCard(
    item: IptvItem,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onRemoveFromContinueWatching: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    
    val fontSize = responsive.sp(tokens.typography.caption.fontSize)
    val cardSpacing = responsive.dp(tokens.spacing.small)
    val badgePadding = responsive.dp(tokens.spacing.small)
    val iconSize = responsive.dp(24.dp)

    PosterCardContent(
        name = item.name, focused = isFocused, primary = tokens.colors.primary,
        text = tokens.colors.textPrimary, background = tokens.colors.surfaceVariant.copy(alpha = 0.1f),
        titleStyle = tokens.typography.caption, titleSize = fontSize, shape = tokens.shapes.card,
        aspectRatio = tokens.dimensions.posterAspectRatio, spacing = cardSpacing,
        titlePadding = responsive.dp(tokens.spacing.tiny), progressHeight = responsive.dp(tokens.spacing.micro),
        modifier = modifier.fillMaxWidth()
            .adaptiveFocus(shape = tokens.shapes.card, onFocus = { isFocused = it })
            .clickable(onClick = onClick),
        progress = progress ?: item.progress,
        image = { imageModifier ->
            Box(imageModifier, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Movie, null, Modifier.size(44.dp), tint = tokens.colors.textSecondary.copy(alpha = .3f))
                AsyncImage(model = rsv.squitv.core.ui.components.content.ContentImageSource(listOf(item.icon, item.backdropUrl)), contentDescription = item.name, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            }
        },
        badges = {
            if (onRemoveFromContinueWatching != null) {
                CompactCardActionButton(
                    icon = Icons.Rounded.Close,
                    description = "${item.name}: ${androidx.compose.ui.res.stringResource(rsv.squitv.R.string.remove_from_continue_watching)}",
                    onClick = onRemoveFromContinueWatching,
                    background = tokens.colors.background.copy(alpha = .68f),
                    foreground = tokens.colors.textPrimary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(badgePadding)
                        .adaptiveFocus(shape = CircleShape, focusedScale = 1f),
                )
            }
            if (onToggleFavorite != null) {
                CardFavoriteButton(item.isFavorite, item.name, onToggleFavorite,
                    Modifier.align(Alignment.TopEnd).padding(badgePadding))
            } else if (item.isFavorite) {
                Surface(
                    color = tokens.colors.background.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopEnd).padding(badgePadding).size(iconSize)
                ) {
                    Icon(Icons.Rounded.Favorite, null, tint = tokens.colors.error, modifier = Modifier.padding(responsive.dp(4.dp)))
                }
            }

            item.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { rating ->
                RatingBadge(rating, Modifier.align(Alignment.TopStart).padding(badgePadding))
            }
        }
    )
}
