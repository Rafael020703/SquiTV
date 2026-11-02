package rsv.squitv.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import rsv.squitv.shared.brand.BrandTypography
import rsv.squitv.shared.brand.TextMetrics

/**
 * SQUI TV TYPOGRAPHY escala cinematográfica
 */
val Typography = Typography(
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

private fun TextMetrics.toStyle() = TextStyle(
    fontFamily = FontFamily.Default, fontWeight = FontWeight(weight), fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp, letterSpacing = letterSpacingSp.sp
)

/**
 * ORIGINAL LARGE TYPOGRAPHY (Legacy support)
 */
val LargeTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.4.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)
