package rsv.squitv.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import rsv.squitv.core.ui.responsive.LocalResponsiveMetrics
import rsv.squitv.core.ui.responsive.ResponsiveCards
import rsv.squitv.core.ui.responsive.ResponsiveMetrics
import rsv.squitv.core.ui.responsive.ResponsiveSpacing

/**
 * Squi TV Design System Tokens
 * Ponto central de acesso aos tokens visuais.
 */
object AppDesignSystem {

    val colors: ColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalColors.current

    val typography: TypeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalTypography.current

    val spacing: Spacing
        get() = Spacing

    val adaptiveSpacing: ResponsiveSpacing
        get() = ResponsiveSpacing

    val shapes: AppShapes
        get() = AppShapes

    val dimensions: AppDimensions
        get() = AppDimensions

    val adaptiveDimensions: ResponsiveCards
        get() = ResponsiveCards

    val responsive: ResponsiveMetrics
        @Composable
        get() = LocalResponsiveMetrics.current
}

/**
 * COLOR TOKENS
 */
data class ColorTokens(
    val primary: Color = PrimaryCyan,
    val primaryVariant: Color = PrimaryCyanVariant,
    val secondary: Color = SecondaryPurple,
    val background: Color = DeepBackground,
    val backgroundSecondary: Color = BackgroundDark,
    val surface: Color = SurfaceDark,
    val surfaceVariant: Color = SurfaceDarkVariant,
    val surfaceElevated: Color = SurfaceElevated,
    val surfaceSelected: Color = SurfaceSelected,
    val textPrimary: Color = OnSurfaceLight,
    val textSecondary: Color = OnSurfaceMuted,
    val textDisabled: Color = OnSurfaceDisabled,
    val error: Color = ErrorRed,
    val success: Color = SuccessGreen,
    val warning: Color = WarningAmber,
    val accent: Color = PrimaryCyan,
    val focus: Color = FocusGlowCyan,
    val border: Color = GlassBorder,
    val divider: Color = GlassBorder.copy(alpha = 0.1f),
    val overlay: Color = GlassBlack
)

/**
 * TYPOGRAPHY TOKENS
 */
data class TypeTokens(
    val display: TextStyle = Typography.displayLarge,
    val headline: TextStyle = Typography.headlineLarge,
    val title: TextStyle = Typography.titleLarge,
    val body: TextStyle = Typography.bodyLarge,
    val label: TextStyle = Typography.labelLarge,
    val caption: TextStyle = Typography.labelSmall
)

internal val LocalColors = staticCompositionLocalOf { ColorTokens() }
internal val LocalTypography = staticCompositionLocalOf { TypeTokens() }
