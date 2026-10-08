package rsv.squitv.core.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.core.ui.theme.ThemeLive

enum class HomeCardType {
    LIVE_TV,
    MOVIES,
    SERIES,
    EPG,
    MULTI_VIEW,
    DOWNLOADS,
    FAVORITES
}

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
        Box(modifier = Modifier.fillMaxSize()) {
            // Dark Minimalist Base Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF141A26),
                                Color(0xFF0A0D14),
                                Color(0xFF07090D)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(1000f, 1000f)
                        )
                    )
            )

            // Vector Graphic for Card (No photographs / No external bitmaps)
            HomeCardGraphic(
                cardType = cardType,
                themeColor = themeColor,
                isFocused = isFocused,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle Gradient Scrim for Text Contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF07090D).copy(alpha = 0.6f),
                                Color(0xFF07090D).copy(alpha = 0.95f)
                            ),
                            startY = 0f
                        )
                    )
            )
            
            // Focus Highlight Glow
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(themeColor.copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(0.5f, 0.5f),
                                radius = 900f
                            )
                        )
                )
            }

            // Card Text and Action Button Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(cardPadding),
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.medium))
                ) {
                    // Category Icon box with glow
                    Box(
                        modifier = Modifier
                            .size(iconBoxSize)
                            .clip(tokens.shapes.medium)
                            .background(
                                if (isFocused) themeColor.copy(alpha = 0.3f)
                                else themeColor.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isFocused) Color.White else themeColor,
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.uppercase(),
                            style = (if (isLarge) tokens.typography.display else tokens.typography.headline).copy(fontSize = titleSize),
                            color = tokens.colors.textPrimary.copy(alpha = contentAlpha),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            maxLines = 1
                        )
                        if (description.isNotEmpty()) {
                            Text(
                                text = description,
                                style = tokens.typography.body.copy(fontSize = descSize),
                                color = tokens.colors.textSecondary.copy(alpha = contentAlpha),
                                maxLines = 1
                            )
                        }
                    }

                    // Action Indicator (Play Arrow / Forward)
                    if (isFocused) {
                        Surface(
                            shape = tokens.shapes.medium,
                            color = themeColor,
                            modifier = Modifier.size(actionIndicatorSize)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(actionIconSize)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = themeColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(actionIndicatorSize * 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCardGraphic(
    cardType: HomeCardType,
    themeColor: Color,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val strokeAlpha = if (isFocused) 0.85f else 0.45f
        val fillAlpha = if (isFocused) 0.20f else 0.08f

        when (cardType) {
            HomeCardType.LIVE_TV -> drawLiveTvGraphic(w, h, themeColor, strokeAlpha, fillAlpha)
            HomeCardType.MOVIES -> drawMoviesGraphic(w, h, themeColor, strokeAlpha, fillAlpha)
            HomeCardType.SERIES -> drawSeriesGraphic(w, h, themeColor, strokeAlpha, fillAlpha)
            HomeCardType.EPG -> drawEpgGraphic(w, h, themeColor, strokeAlpha, fillAlpha)
            HomeCardType.MULTI_VIEW -> drawMultiViewGraphic(w, h, themeColor, strokeAlpha, fillAlpha)
            HomeCardType.DOWNLOADS -> drawDownloadsGraphic(w, h, themeColor, strokeAlpha, fillAlpha)
            HomeCardType.FAVORITES -> drawFavoritesGraphic(w, h, themeColor, strokeAlpha, fillAlpha)
        }
    }
}

// 1. TV AO VIVO: Television screen + transmission signal waves
private fun DrawScope.drawLiveTvGraphic(
    w: Float, h: Float, color: Color, strokeAlpha: Float, fillAlpha: Float
) {
    val tvW = w * 0.48f
    val tvH = h * 0.42f
    val tvX = w * 0.45f
    val tvY = h * 0.12f

    // TV Screen frame
    drawRoundRect(
        color = color.copy(alpha = strokeAlpha),
        topLeft = Offset(tvX, tvY),
        size = Size(tvW, tvH),
        cornerRadius = CornerRadius(16f, 16f),
        style = Stroke(width = 3f)
    )
    drawRoundRect(
        color = color.copy(alpha = fillAlpha),
        topLeft = Offset(tvX, tvY),
        size = Size(tvW, tvH),
        cornerRadius = CornerRadius(16f, 16f)
    )

    // TV Stand / Base
    val standY = tvY + tvH
    drawLine(
        color = color.copy(alpha = strokeAlpha),
        start = Offset(tvX + tvW * 0.35f, standY),
        end = Offset(tvX + tvW * 0.25f, standY + 12f),
        strokeWidth = 3f
    )
    drawLine(
        color = color.copy(alpha = strokeAlpha),
        start = Offset(tvX + tvW * 0.65f, standY),
        end = Offset(tvX + tvW * 0.75f, standY + 12f),
        strokeWidth = 3f
    )

    // Play icon in TV center
    val playCenter = Offset(tvX + tvW * 0.5f, tvY + tvH * 0.5f)
    val playPath = Path().apply {
        moveTo(playCenter.x - 12f, playCenter.y - 16f)
        lineTo(playCenter.x + 18f, playCenter.y)
        lineTo(playCenter.x - 12f, playCenter.y + 16f)
        close()
    }
    drawPath(playPath, color = color.copy(alpha = strokeAlpha))

    // Transmission Signal Arcs
    val signalOrigin = Offset(tvX + tvW, tvY)
    for (i in 1..3) {
        val radius = 28f * i
        drawArc(
            color = color.copy(alpha = strokeAlpha / i),
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(signalOrigin.x - radius, signalOrigin.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 3f)
        )
    }

    // Signal dots
    drawCircle(color.copy(alpha = strokeAlpha), radius = 4f, center = signalOrigin)
}

// 2. FILMES: Cinema Clapperboard / Film Frame
private fun DrawScope.drawMoviesGraphic(
    w: Float, h: Float, color: Color, strokeAlpha: Float, fillAlpha: Float
) {
    val boxW = w * 0.44f
    val boxH = h * 0.40f
    val boxX = w * 0.48f
    val boxY = h * 0.14f

    // Main Slate Body
    drawRoundRect(
        color = color.copy(alpha = fillAlpha),
        topLeft = Offset(boxX, boxY + 20f),
        size = Size(boxW, boxH - 20f),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRoundRect(
        color = color.copy(alpha = strokeAlpha),
        topLeft = Offset(boxX, boxY + 20f),
        size = Size(boxW, boxH - 20f),
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = 3f)
    )

    // Clapper Top Bar (Angled)
    val barH = 16f
    val clapperPath = Path().apply {
        moveTo(boxX, boxY + 12f)
        lineTo(boxX + boxW, boxY - 8f)
        lineTo(boxX + boxW, boxY - 8f + barH)
        lineTo(boxX, boxY + 12f + barH)
        close()
    }
    drawPath(clapperPath, color = color.copy(alpha = strokeAlpha), style = Stroke(width = 2.5f))

    // Stripes on Top Bar
    for (i in 1..4) {
        val sx = boxX + (boxW / 5) * i
        drawLine(
            color = color.copy(alpha = strokeAlpha),
            start = Offset(sx, boxY + 12f - (i * 3f)),
            end = Offset(sx + 10f, boxY + 12f + barH - (i * 3f)),
            strokeWidth = 2.5f
        )
    }

    // Play triangle in clapper center
    val center = Offset(boxX + boxW * 0.5f, boxY + boxH * 0.55f)
    val playPath = Path().apply {
        moveTo(center.x - 10f, center.y - 14f)
        lineTo(center.x + 16f, center.y)
        lineTo(center.x - 10f, center.y + 14f)
        close()
    }
    drawPath(playPath, color = color.copy(alpha = strokeAlpha))
}

// 3. SÉRIES: Stacked streaming screens / Catalog layers
private fun DrawScope.drawSeriesGraphic(
    w: Float, h: Float, color: Color, strokeAlpha: Float, fillAlpha: Float
) {
    val cardW = w * 0.34f
    val cardH = h * 0.38f

    // Card 3 (Back)
    drawRoundRect(
        color = color.copy(alpha = fillAlpha * 0.5f),
        topLeft = Offset(w * 0.60f, h * 0.08f),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = color.copy(alpha = strokeAlpha * 0.3f),
        topLeft = Offset(w * 0.60f, h * 0.08f),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 2f)
    )

    // Card 2 (Middle)
    drawRoundRect(
        color = color.copy(alpha = fillAlpha * 0.8f),
        topLeft = Offset(w * 0.52f, h * 0.12f),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = color.copy(alpha = strokeAlpha * 0.6f),
        topLeft = Offset(w * 0.52f, h * 0.12f),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 2.5f)
    )

    // Card 1 (Front - Primary)
    val frontX = w * 0.44f
    val frontY = h * 0.16f
    drawRoundRect(
        color = color.copy(alpha = fillAlpha * 1.5f),
        topLeft = Offset(frontX, frontY),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = color.copy(alpha = strokeAlpha),
        topLeft = Offset(frontX, frontY),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 3f)
    )

    // Play icon on Front Card
    val center = Offset(frontX + cardW * 0.5f, frontY + cardH * 0.5f)
    val playPath = Path().apply {
        moveTo(center.x - 8f, center.y - 12f)
        lineTo(center.x + 14f, center.y)
        lineTo(center.x - 8f, center.y + 12f)
        close()
    }
    drawPath(playPath, color = color.copy(alpha = strokeAlpha))
}

// 4. EPG: Programming Timeline Grid
private fun DrawScope.drawEpgGraphic(
    w: Float, h: Float, color: Color, strokeAlpha: Float, fillAlpha: Float
) {
    val gridX = w * 0.45f
    val gridY = h * 0.12f
    val gridW = w * 0.48f
    val gridH = h * 0.42f

    // Grid Container Frame
    drawRoundRect(
        color = color.copy(alpha = strokeAlpha * 0.5f),
        topLeft = Offset(gridX, gridY),
        size = Size(gridW, gridH),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 2f)
    )

    // Header timeline bar
    drawRect(
        color = color.copy(alpha = fillAlpha),
        topLeft = Offset(gridX, gridY),
        size = Size(gridW, gridH * 0.25f)
    )
    for (i in 1..3) {
        val tx = gridX + (gridW / 4) * i
        drawLine(
            color = color.copy(alpha = strokeAlpha * 0.4f),
            start = Offset(tx, gridY + 4f),
            end = Offset(tx, gridY + gridH * 0.25f - 4f),
            strokeWidth = 2f
        )
    }

    // Row 1 Block (Active program highlighted)
    drawRoundRect(
        color = color.copy(alpha = fillAlpha * 2.5f),
        topLeft = Offset(gridX + 8f, gridY + gridH * 0.32f),
        size = Size(gridW * 0.55f, gridH * 0.28f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = color.copy(alpha = strokeAlpha),
        topLeft = Offset(gridX + 8f, gridY + gridH * 0.32f),
        size = Size(gridW * 0.55f, gridH * 0.28f),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 2.5f)
    )

    // Row 1 Next Block
    drawRoundRect(
        color = color.copy(alpha = fillAlpha * 0.8f),
        topLeft = Offset(gridX + gridW * 0.60f, gridY + gridH * 0.32f),
        size = Size(gridW * 0.35f, gridH * 0.28f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Row 2 Block
    drawRoundRect(
        color = color.copy(alpha = fillAlpha * 0.8f),
        topLeft = Offset(gridX + 8f, gridY + gridH * 0.66f),
        size = Size(gridW * 0.40f, gridH * 0.28f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = color.copy(alpha = fillAlpha * 1.2f),
        topLeft = Offset(gridX + gridW * 0.45f, gridY + gridH * 0.66f),
        size = Size(gridW * 0.50f, gridH * 0.28f),
        cornerRadius = CornerRadius(6f, 6f)
    )
}

// 5. MULTI-VIEW: 4 Mini Screen Grid
private fun DrawScope.drawMultiViewGraphic(
    w: Float, h: Float, color: Color, strokeAlpha: Float, fillAlpha: Float
) {
    val gridX = w * 0.48f
    val gridY = h * 0.12f
    val screenW = w * 0.20f
    val screenH = h * 0.18f
    val spacing = 8f

    for (row in 0..1) {
        for (col in 0..1) {
            val sx = gridX + col * (screenW + spacing)
            val sy = gridY + row * (screenH + spacing)

            val isActive = (row == 0 && col == 0)
            val currStroke = if (isActive) strokeAlpha else strokeAlpha * 0.4f
            val currFill = if (isActive) fillAlpha * 2f else fillAlpha * 0.6f

            drawRoundRect(
                color = color.copy(alpha = currFill),
                topLeft = Offset(sx, sy),
                size = Size(screenW, screenH),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = color.copy(alpha = currStroke),
                topLeft = Offset(sx, sy),
                size = Size(screenW, screenH),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = if (isActive) 3f else 1.5f)
            )

            // Mini play button in active screen
            if (isActive) {
                val center = Offset(sx + screenW * 0.5f, sy + screenH * 0.5f)
                val playPath = Path().apply {
                    moveTo(center.x - 5f, center.y - 7f)
                    lineTo(center.x + 8f, center.y)
                    lineTo(center.x - 5f, center.y + 7f)
                    close()
                }
                drawPath(playPath, color = color.copy(alpha = strokeAlpha))
            }
        }
    }
}

// 6. DOWNLOADS: Cloud + Down Arrow + Transfer Progress
private fun DrawScope.drawDownloadsGraphic(
    w: Float, h: Float, color: Color, strokeAlpha: Float, fillAlpha: Float
) {
    val centerX = w * 0.68f
    val centerY = h * 0.22f

    // Down Arrow Shaft & Head
    val arrowPath = Path().apply {
        moveTo(centerX, centerY - 16f)
        lineTo(centerX, centerY + 18f)
        moveTo(centerX - 12f, centerY + 6f)
        lineTo(centerX, centerY + 18f)
        lineTo(centerX + 12f, centerY + 6f)
    }
    drawPath(arrowPath, color = color.copy(alpha = strokeAlpha), style = Stroke(width = 4f, cap = StrokeCap.Round))

    // Transfer Storage Line below
    drawLine(
        color = color.copy(alpha = strokeAlpha),
        start = Offset(centerX - 24f, centerY + 28f),
        end = Offset(centerX + 24f, centerY + 28f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    // Motion Dash Lines (Transfer Activity)
    for (i in -1..1) {
        val dx = centerX + i * 18f
        drawLine(
            color = color.copy(alpha = strokeAlpha * 0.5f),
            start = Offset(dx, centerY - 28f),
            end = Offset(dx, centerY - 20f),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}

// 7. FAVORITOS: Large Contour Heart with Soft Rays
private fun DrawScope.drawFavoritesGraphic(
    w: Float, h: Float, color: Color, strokeAlpha: Float, fillAlpha: Float
) {
    val centerX = w * 0.68f
    val centerY = h * 0.28f
    val scale = 1.3f

    // Radiating Glow Lines
    for (i in 0 until 8) {
        val angle = (i * 45f) * (Math.PI / 180f).toFloat()
        val r1 = 30f * scale
        val r2 = 42f * scale
        drawLine(
            color = color.copy(alpha = strokeAlpha * 0.3f),
            start = Offset(centerX + kotlin.math.cos(angle) * r1, centerY + kotlin.math.sin(angle) * r1),
            end = Offset(centerX + kotlin.math.cos(angle) * r2, centerY + kotlin.math.sin(angle) * r2),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }

    // Heart Path
    val heartPath = Path().apply {
        moveTo(centerX, centerY + 16f * scale)
        cubicTo(
            centerX - 28f * scale, centerY - 4f * scale,
            centerX - 28f * scale, centerY - 24f * scale,
            centerX, centerY - 10f * scale
        )
        cubicTo(
            centerX + 28f * scale, centerY - 24f * scale,
            centerX + 28f * scale, centerY - 4f * scale,
            centerX, centerY + 16f * scale
        )
        close()
    }

    drawPath(heartPath, color = color.copy(alpha = fillAlpha * 1.8f))
    drawPath(heartPath, color = color.copy(alpha = strokeAlpha), style = Stroke(width = 3f, cap = StrokeCap.Round))
}
