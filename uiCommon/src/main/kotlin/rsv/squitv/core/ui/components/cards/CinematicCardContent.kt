package rsv.squitv.core.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original Android dashboard card presentation; focus/input are owned by each platform. */
@Composable
fun CinematicCardContent(title: String, description: String, icon: ImageVector,
    cardType: HomeCardType, themeColor: Color, isFocused: Boolean, contentAlpha: Float,
    textColor: Color, secondaryText: Color, titleStyle: TextStyle, bodyStyle: TextStyle,
    cardShape: Shape, badgeShape: Shape, cardPadding: Dp, gap: Dp,
    iconBoxSize: Dp, iconSize: Dp, actionIndicatorSize: Dp, actionIconSize: Dp,
    titleSize: TextUnit, descSize: TextUnit) {
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
                        ),
                        shape = cardShape
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
                        ),
                        shape = cardShape
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
                            ),
                            shape = cardShape
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
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    // Category Icon box with glow
                    Box(
                        modifier = Modifier
                            .size(iconBoxSize)
                            .clip(badgeShape)
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
                            style = titleStyle.copy(fontSize = titleSize),
                            color = textColor.copy(alpha = contentAlpha),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            maxLines = 1
                        )
                        if (description.isNotEmpty()) {
                            Text(
                                text = description,
                                style = bodyStyle.copy(fontSize = descSize),
                                color = secondaryText.copy(alpha = contentAlpha),
                                maxLines = 1
                            )
                        }
                    }

                    // Action Indicator (Play Arrow / Forward)
                    if (isFocused) {
                        Surface(
                            shape = badgeShape,
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
