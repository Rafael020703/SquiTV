package rsv.squitv.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import rsv.squitv.shared.brand.AppBrand

/**
 * SHAPE TOKENS
 */
object AppShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(AppBrand.CARD_CORNER_DP.dp)
    val large = RoundedCornerShape(16.dp)
    val extraLarge = RoundedCornerShape(24.dp)
    
    val card = medium
    val button = small
    val dialog = large
}
