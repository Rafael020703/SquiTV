package rsv.squitv.core.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import rsv.squitv.core.ui.theme.*
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.core.ui.components.common.adaptiveFocus

@Composable
fun ChannelCard(
    item: IptvItem,
    modifier: Modifier = Modifier,
    epg: String? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    
    val fontSize = responsive.sp(tokens.typography.caption.fontSize)
    val cardPadding = responsive.dp(tokens.spacing.small)
    val iconSize = responsive.dp(16.dp)

    val highestQuality = remember(item.versions, item.name) {
        item.versions.mapNotNull { it.resolution }.maxByOrNull { it.rank }
            ?: rsv.squitv.domain.model.LiveChannelGrouping.parse(item.name).resolution
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveFocus(
                shape = tokens.shapes.card,
                onFocus = { isFocused = it }
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(tokens.dimensions.channelAspectRatio)
                .clip(tokens.shapes.card)
                .background(tokens.colors.surfaceVariant.copy(alpha = 0.1f))
        ) {
            AsyncImage(
                model = item.icon,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            // Bottom gradient for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 0.6f
                        )
                    )
            )

            ChannelQualityBadge(
                quality = highestQuality,
                primaryColor = tokens.colors.primary,
                secondaryColor = tokens.colors.textSecondary,
                successColor = tokens.colors.success,
                shape = tokens.shapes.small,
                textStyle = tokens.typography.caption,
                modifier = Modifier.align(Alignment.TopStart).padding(cardPadding)
            )

            if (onToggleFavorite != null) {
                CardFavoriteButton(
                    isFavorite = item.isFavorite,
                    contentName = item.name,
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(cardPadding)
                )
            } else if (item.isFavorite) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = tokens.colors.error,
                    modifier = Modifier.align(Alignment.TopEnd).padding(cardPadding).size(iconSize)
                )
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(cardPadding)
            ) {
                if (epg != null) {
                    Text(
                        text = epg.uppercase(),
                        style = tokens.typography.caption.copy(fontSize = fontSize),
                        color = tokens.colors.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = item.name.uppercase(),
                    style = tokens.typography.caption.copy(fontSize = fontSize),
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
