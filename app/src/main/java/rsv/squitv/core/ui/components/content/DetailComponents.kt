package rsv.squitv.core.ui.components.content

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.theme.*

@Composable
fun MetadataItem(
    text: String,
    modifier: Modifier = Modifier
) {
    val tokens = AppDesignSystem
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = tokens.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = text.uppercase(),
            color = tokens.colors.textPrimary,
            modifier = Modifier.padding(horizontal = tokens.spacing.medium, vertical = tokens.spacing.tiny),
            style = tokens.typography.caption,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ActorAvatar(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false
) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }
    val avatarColor = remember(name) {
        val hash = name.hashCode()
        val h = (hash and 0xFFFF) % 360
        Color.hsl(h.toFloat(), 0.5f, 0.5f)
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(if (isExpanded) 120.dp else 90.dp)
            .appFocus(shape = CircleShape, onFocus = { isFocused = it })
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(if (isExpanded) 90.dp else 70.dp),
            shape = CircleShape,
            color = avatarColor.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.firstOrNull()?.uppercase()?.toString() ?: "",
                    style = tokens.typography.title,
                    color = avatarColor,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(modifier = Modifier.height(tokens.spacing.small))
        Text(
            text = name.uppercase(),
            style = tokens.typography.caption,
            color = if (isFocused) tokens.colors.primary else tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp
        )
    }
}

@Composable
fun EpisodeCard(
    title: String,
    episodeNum: Int,
    image: String?,
    plot: String?,
    progress: Float?,
    isWatched: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false
) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .appFocus(
                shape = tokens.shapes.card,
                onFocus = { isFocused = it }
            ),
        shape = tokens.shapes.card,
        color = if (isFocused) tokens.colors.surfaceElevated else tokens.colors.surface.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).clickable { onClick() },
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(if (isExpanded) 160.dp else 120.dp)
                        .aspectRatio(1.77f)
                        .clip(tokens.shapes.small)
                        .background(tokens.colors.surfaceVariant.copy(alpha = 0.1f))
                ) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Episode Number Tag
                    Surface(
                        color = tokens.colors.primary,
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "E$episodeNum",
                            modifier = Modifier.padding(horizontal = tokens.spacing.small, vertical = tokens.spacing.micro),
                            style = tokens.typography.caption,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                    
                    if (isWatched) {
                        Surface(
                            color = tokens.colors.secondary,
                            shape = CircleShape,
                            modifier = Modifier.align(Alignment.TopEnd).padding(tokens.spacing.tiny).size(20.dp)
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.padding(tokens.spacing.micro))
                        }
                    }
                    
                    // Play Overlay
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }

                    if (progress != null && progress > 0.0f) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter),
                            color = tokens.colors.primary,
                            trackColor = Color.Black.copy(alpha = 0.5f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(tokens.spacing.medium))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.uppercase(),
                        style = tokens.typography.title,
                        color = if (isWatched) tokens.colors.textSecondary.copy(alpha = 0.5f) else tokens.colors.textPrimary,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = plot ?: stringResource(R.string.no_description),
                        style = tokens.typography.body,
                        color = tokens.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    if (progress != null && progress > 0.0f) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .padding(top = tokens.spacing.small)
                                .clip(CircleShape),
                            color = tokens.colors.primary,
                            trackColor = tokens.colors.surfaceVariant.copy(alpha = 0.2f)
                        )
                    }
                }
            }
            AppIconButton(
                icon = Icons.Rounded.Download,
                onClick = onDownload,
                modifier = Modifier.padding(start = tokens.spacing.medium)
            )
        }
    }
}

@Composable
fun SeasonListItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }
    val textColor by animateColorAsState(if (isFocused || isSelected) tokens.colors.primary else tokens.colors.textSecondary)
    val bgColor by animateColorAsState(if (isFocused) tokens.colors.primary.copy(alpha = 0.25f) else if (isSelected) tokens.colors.primary.copy(alpha = 0.12f) else Color.Transparent)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .appFocus(
                shape = tokens.shapes.medium,
                onFocus = { isFocused = it }
            )
            .clickable { onClick() }
            .background(bgColor, tokens.shapes.medium)
            .padding(vertical = tokens.spacing.medium, horizontal = tokens.spacing.large)
    ) {
        Text(
            text = name.uppercase(),
            color = textColor,
            style = tokens.typography.title,
            fontWeight = if (isFocused || isSelected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 1.sp
        )
    }
}
