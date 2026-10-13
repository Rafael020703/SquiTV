package rsv.squitv.core.ui.components.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rsv.squitv.core.ui.theme.AppDimensions
import rsv.squitv.core.ui.theme.AppShapes
import rsv.squitv.core.ui.theme.FocusGlowCyan
import rsv.squitv.util.DeviceType
import rsv.squitv.util.rememberWindowInfo

/**
 * Adaptive focus modifier that handles smooth scale, border highlights,
 * and neon glow for TV and Mobile without causing vertical layout shifts.
 */
fun Modifier.adaptiveFocus(
    shape: Shape = AppShapes.medium,
    focusedBorderWidth: Dp = AppDimensions.focusBorderWidth,
    unfocusedBorderWidth: Dp = AppDimensions.standardBorderWidth,
    focusedScale: Float = 1.04f,
    focusedColor: Color = FocusGlowCyan,
    unfocusedColor: Color = Color.White.copy(alpha = 0.1f),
    glowColor: Color? = null,
    onFocus: (Boolean) -> Unit = {}
) = composed {
    val windowInfo = rememberWindowInfo()
    val isTv = windowInfo.deviceType == DeviceType.TV
    var isFocused by remember { mutableStateOf(false) }
    
    val targetScale = if (isFocused && isTv) focusedScale else 1.0f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 200),
        label = "focusScale"
    )
    
    val finalFocusedColor = glowColor ?: focusedColor
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) finalFocusedColor else unfocusedColor,
        animationSpec = tween(durationMillis = 150),
        label = "focusBorderColor"
    )

    this
        .onFocusChanged { 
            isFocused = it.isFocused
            onFocus(it.isFocused)
        }
        .scale(scale)
        .then(
            if (isFocused) {
                Modifier.drawBehind {
                    if (isTv) {
                        val glowBg = finalFocusedColor.copy(alpha = 0.12f)
                        val outline = shape.createOutline(size, layoutDirection, this)
                        val shapePath = Path().apply {
                            when (outline) {
                                is Outline.Rectangle -> addRect(outline.rect)
                                is Outline.Rounded -> addRoundRect(outline.roundRect)
                                is Outline.Generic -> addPath(outline.path)
                            }
                        }
                        drawPath(path = shapePath, color = glowBg)
                    }
                }
            } else Modifier
        )
        .then(
            if (isTv || isFocused) {
                Modifier.border(
                    BorderStroke(if (isFocused) focusedBorderWidth else unfocusedBorderWidth, borderColor),
                    shape
                )
            } else Modifier
        )
}

