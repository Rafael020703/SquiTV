package rsv.squitv.core.ui.components.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import rsv.squitv.core.ui.theme.*
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.components.badges.RatingBadge

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    item: IptvItem,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    
    val fontSize = responsive.sp(tokens.typography.caption.fontSize)
    val cardSpacing = responsive.dp(tokens.spacing.small)
    val badgePadding = responsive.dp(tokens.spacing.small)
    val iconSize = responsive.dp(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveFocus(
                shape = tokens.shapes.card,
                onFocus = { isFocused = it }
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(tokens.dimensions.posterAspectRatio)
                .clip(tokens.shapes.card)
                .background(tokens.colors.surfaceVariant.copy(alpha = 0.1f))
        ) {
            AsyncImage(
                model = item.icon,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dynamic Gradient for better readability of metadata if added
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                            startY = 0.7f
                        )
                    )
            )

            if (onToggleFavorite != null) {
                CardFavoriteButton(
                    isFavorite = item.isFavorite,
                    contentName = item.name,
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(badgePadding)
                )
            } else if (item.isFavorite) {
                Surface(
                    color = tokens.colors.background.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopEnd).padding(badgePadding).size(iconSize)
                ) {
                    Icon(Icons.Rounded.Favorite, null, tint = tokens.colors.error, modifier = Modifier.padding(responsive.dp(4.dp)))
                }
            }

            val rating = item.rating
            if (!rating.isNullOrBlank() && rating != "0") {
                RatingBadge(
                    rating = rating,
                    modifier = Modifier.align(Alignment.TopStart).padding(badgePadding)
                )
            }

            val displayProgress = progress ?: item.progress
            if (displayProgress != null && displayProgress > 0.01f) {
                LinearProgressIndicator(
                    progress = { displayProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(responsive.dp(tokens.spacing.micro)),
                    color = tokens.colors.primary,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
        Spacer(modifier = Modifier.height(cardSpacing))
        Text(
            text = item.name.uppercase(),
            color = if (isFocused) tokens.colors.primary else tokens.colors.textPrimary,
            style = tokens.typography.caption.copy(fontSize = fontSize),
            fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = responsive.dp(tokens.spacing.tiny))
        )
    }
}
