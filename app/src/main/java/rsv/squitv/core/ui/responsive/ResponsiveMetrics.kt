package rsv.squitv.core.ui.responsive

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.util.DeviceType
import rsv.squitv.util.WindowSize
import rsv.squitv.util.rememberWindowInfo
import kotlin.math.min

@Immutable
data class ResponsiveMetrics(
    val widthDp: Dp,
    val heightDp: Dp,
    val density: Float,
    val fontScale: Float,
    val isLandscape: Boolean,
    val aspectRatio: Float,
    val screenClass: ScreenClass,
    val contentScale: Float, // Scale based on width (legacy/standard)
    val viewportScale: Float, // Combined width/height scale for "fit-to-viewport"
    val spacingScale: Float,
    val textScale: Float
) {
    val horizontalPadding: Dp get() = (if (screenClass == ScreenClass.COMPACT) 16.dp else 48.dp) * viewportScale
    val verticalPadding: Dp get() = (if (screenClass == ScreenClass.COMPACT) 16.dp else 32.dp) * viewportScale
    
    /**
     * Standard scaling based on viewport scale (Fits both axes)
     */
    fun dp(base: Dp): Dp = base * viewportScale
    
    /**
     * Scaling for text with a legibility minimum floor (default 10.sp)
     */
    fun sp(base: TextUnit, minSp: TextUnit = 10.sp): TextUnit {
        val scaled = base.value * textScale
        return kotlin.math.max(scaled, minSp.value).sp
    }
}

enum class ScreenClass {
    COMPACT,   // < 600dp (Phones)
    MEDIUM,    // 600dp - 840dp (Tablets portrait / Phone landscape)
    EXPANDED,  // 840dp - 1200dp (Tablets / Laptops)
    LARGE      // > 1200dp (TVs / Desktop)
}

val LocalResponsiveMetrics = compositionLocalOf {
    ResponsiveMetrics(
        widthDp = 0.dp,
        heightDp = 0.dp,
        density = 1f,
        fontScale = 1f,
        isLandscape = true,
        aspectRatio = 1.77f,
        screenClass = ScreenClass.LARGE,
        contentScale = 1f,
        viewportScale = 1f,
        spacingScale = 1f,
        textScale = 1f
    )
}

@Composable
fun calculateResponsiveMetrics(
    availableWidth: Dp? = null,
    availableHeight: Dp? = null
): ResponsiveMetrics {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val widthDp = availableWidth ?: configuration.screenWidthDp.dp
    val heightDp = availableHeight ?: configuration.screenHeightDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val aspectRatio = widthDp.value / heightDp.value.coerceAtLeast(1f)
    
    val screenClass = when {
        widthDp < 600.dp -> ScreenClass.COMPACT
        widthDp < 840.dp -> ScreenClass.MEDIUM
        widthDp < 1200.dp -> ScreenClass.EXPANDED
        else -> ScreenClass.LARGE
    }
    
    // REFERENCE DIMENSIONS (Reference is a 1280x720 layout)
    val refWidth = 1280f
    val refHeight = 720f
    
    val widthScale = widthDp.value / refWidth
    val heightScale = heightDp.value / refHeight
    
    // viewportScale: The "Fit-to-Viewport" rule. 
    // It ensures that whatever was designed for 1280x720 fits in the current screen 
    // by taking the minimum of both scales.
    // Lowered minimum clamp to 0.25f to accommodate small portrait screens side-by-side.
    val viewportScale = min(widthScale, heightScale).coerceIn(0.25f, 2.0f)
    
    // Legacy content scale (width-based)
    val contentScale = widthScale.coerceIn(0.7f, 1.5f)
    
    // Spacing and Text scales derived from viewportScale but with slightly different curves
    val spacingScale = viewportScale.coerceIn(0.5f, 1.5f)
    
    // Text scale needs to be legibility-aware. 
    // We reduced the minimum to 0.65f to prevent overflow in very small viewports (scale < 0.4)
    val textScale = (viewportScale * 1.1f).coerceIn(0.65f, 1.4f) * density.fontScale

    return ResponsiveMetrics(
        widthDp = widthDp,
        heightDp = heightDp,
        density = density.density,
        fontScale = density.fontScale,
        isLandscape = isLandscape,
        aspectRatio = aspectRatio,
        screenClass = screenClass,
        contentScale = contentScale,
        viewportScale = viewportScale,
        spacingScale = spacingScale,
        textScale = textScale
    )
}
