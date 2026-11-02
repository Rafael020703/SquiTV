package rsv.squitv.shared.brand

/** Exact values from Android Type.kt, expressed without Compose or Android dependencies. */
data class TextMetrics(val sizeSp: Int, val lineHeightSp: Int, val weight: Int, val letterSpacingSp: Float = 0f)

object BrandTypography {
    val displayLarge = TextMetrics(48, 56, 900, -1f)
    val displayMedium = TextMetrics(36, 44, 800)
    val headlineLarge = TextMetrics(28, 36, 900)
    val headlineMedium = TextMetrics(24, 32, 700)
    val titleLarge = TextMetrics(20, 28, 700)
    val titleMedium = TextMetrics(18, 24, 600, .1f)
    val bodyLarge = TextMetrics(16, 24, 400, .5f)
    val bodyMedium = TextMetrics(14, 20, 400, .25f)
    val labelLarge = TextMetrics(12, 16, 700, 1f)
    val labelSmall = TextMetrics(10, 14, 500, .4f)
}
