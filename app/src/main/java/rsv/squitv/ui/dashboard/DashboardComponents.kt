package rsv.squitv.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import rsv.squitv.R
import rsv.squitv.core.ui.components.cards.ChannelCard
import rsv.squitv.core.ui.components.cards.PosterCard
import rsv.squitv.core.ui.components.common.AppSectionHeader
import rsv.squitv.core.ui.theme.*
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem

@Composable
fun PortalBackground(
    showAtmosphere: Boolean = false,
    atmosphereUrl: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = AppDesignSystem
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.background)
    ) {
        if (showAtmosphere) {
            // Optional custom atmosphere image
            if (!atmosphereUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = atmosphereUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.20f
                )
            }

            // Deep vignette and color grading
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                tokens.colors.surfaceVariant.copy(alpha = 0.12f),
                                tokens.colors.background.copy(alpha = 0.85f),
                                tokens.colors.background
                            ),
                            center = Offset(0.5f, 0.4f),
                            radius = 1800f
                        )
                    )
            )

            // Thematic Glow Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                tokens.colors.primary.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(0.8f, 0.2f),
                            radius = 1500f
                        )
                    )
            )
        }

        // Subtle Radial Gradient for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(tokens.colors.primary.copy(alpha = 0.03f), Color.Transparent),
                        center = Offset(0.5f, 0.3f),
                        radius = 1500f
                    )
                )
        )

        // Dot pattern
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotSpacing = 24.dp.toPx()
            val dotColor = Color.White.copy(alpha = 0.02f)
            for (x in 0..size.width.toInt() step dotSpacing.toInt()) {
                for (y in 0..size.height.toInt() step dotSpacing.toInt()) {
                    drawCircle(
                        color = dotColor,
                        radius = 0.6.dp.toPx(),
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun ContentRow(
    title: String,
    items: List<IptvItem>,
    modifier: Modifier = Modifier,
    watchProgress: Map<Int, Float> = emptyMap(),
    onItemClick: (IptvItem) -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    
    Column(modifier = modifier.fillMaxWidth().padding(vertical = responsive.dp(tokens.spacing.medium))) {
        if (title.isNotEmpty()) {
            AppSectionHeader(
                title = title,
                onActionClick = { /* Can navigate to specific view if needed */ }
            )
            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.medium)))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = responsive.dp(tokens.spacing.extraLarge)),
            horizontalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.large))
        ) {
            items(items, key = { it.id + it.type.toString() }) { item ->
                val progressValue = watchProgress[item.id.toIntOrNull() ?: -1]
                
                val itemWidth = responsive.dp(if (item.type == ContentType.LIVE) 260.dp else 160.dp)

                Box(modifier = Modifier.width(itemWidth)) {
                    if (item.type == ContentType.LIVE) {
                        ChannelCard(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    } else {
                        PosterCard(
                            item = item,
                            progress = progressValue,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyncIndicator(modifier: Modifier = Modifier) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(end = responsive.dp(tokens.spacing.large))
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(responsive.dp(16.dp)),
            strokeWidth = responsive.dp(2.dp),
            color = tokens.colors.primary
        )
        Spacer(Modifier.width(responsive.dp(tokens.spacing.small)))
        Text(
            text = stringResource(R.string.sync_live_tv_label).uppercase(),
            style = tokens.typography.caption.copy(fontSize = responsive.sp(tokens.typography.caption.fontSize)),
            color = tokens.colors.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = responsive.sp(1.sp)
        )
    }
}

@Composable
fun OfflineBanner() {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = responsive.dp(tokens.spacing.large), vertical = responsive.dp(tokens.spacing.small)),
        color = tokens.colors.error.copy(alpha = 0.1f),
        shape = tokens.shapes.small,
        border = BorderStroke(responsive.dp(1.dp), tokens.colors.error.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = responsive.dp(tokens.spacing.large), vertical = responsive.dp(tokens.spacing.small)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff, 
                contentDescription = null, 
                tint = tokens.colors.error, 
                modifier = Modifier.size(responsive.dp(16.dp))
            )
            Spacer(Modifier.width(responsive.dp(tokens.spacing.medium)))
            Text(
                text = stringResource(R.string.offline_mode_banner).uppercase(),
                style = tokens.typography.caption.copy(fontSize = responsive.sp(tokens.typography.caption.fontSize)),
                fontWeight = FontWeight.Black,
                color = tokens.colors.error
            )
        }
    }
}
