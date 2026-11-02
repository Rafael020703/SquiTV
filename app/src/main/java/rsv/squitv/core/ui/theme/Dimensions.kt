package rsv.squitv.core.ui.theme

import androidx.compose.ui.unit.dp
import rsv.squitv.shared.brand.AppBrand

/**
 * SPACING SCALE
 */
object Spacing {
    val micro = 2.dp
    val tiny = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val huge = 32.dp
    val giant = 48.dp
    
    // Legacy support
    val xxs = micro
    val xs = tiny
    val sm = small
    val md = medium
    val lg = large
    val xl = extraLarge
    val xxl = huge
    val xxxl = giant
}

/**
 * COMPONENT DIMENSIONS
 */
object AppDimensions {
    val cardElevation = 8.dp
    val focusBorderWidth = AppBrand.FOCUS_BORDER_DP.dp
    val standardBorderWidth = 1.dp
    val posterAspectRatio = 2f / 3f
    val channelAspectRatio = AppBrand.CHANNEL_ASPECT_RATIO
    
    val sidebarWidth = AppBrand.SIDEBAR_WIDTH_DP.dp
    val topBarHeight = 72.dp
    val bottomNavHeight = 64.dp
    val minTouchTarget = 48.dp

    // Note: Replaced legacy capitalized versions with camelCase to avoid JVM signature clashes.
    // Use the lowerCase version throughout the app.
}
