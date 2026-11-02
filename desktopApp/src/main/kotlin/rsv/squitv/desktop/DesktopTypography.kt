package rsv.squitv.desktop

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import rsv.squitv.shared.brand.BrandTypography
import rsv.squitv.shared.brand.TextMetrics

internal val desktopTypography = Typography(
    displayLarge = BrandTypography.displayLarge.toStyle(),
    displayMedium = BrandTypography.displayMedium.toStyle(),
    headlineLarge = BrandTypography.headlineLarge.toStyle(),
    headlineMedium = BrandTypography.headlineMedium.toStyle(),
    titleLarge = BrandTypography.titleLarge.toStyle(),
    titleMedium = BrandTypography.titleMedium.toStyle(),
    bodyLarge = BrandTypography.bodyLarge.toStyle(),
    bodyMedium = BrandTypography.bodyMedium.toStyle(),
    labelLarge = BrandTypography.labelLarge.toStyle(),
    labelSmall = BrandTypography.labelSmall.toStyle()
)

private fun TextMetrics.toStyle() = TextStyle(fontFamily = FontFamily.Default,
    fontWeight = FontWeight(weight), fontSize = sizeSp.sp, lineHeight = lineHeightSp.sp,
    letterSpacing = letterSpacingSp.sp)
