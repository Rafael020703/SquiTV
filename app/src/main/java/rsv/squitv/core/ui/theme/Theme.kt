package rsv.squitv.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import rsv.squitv.core.ui.responsive.ResponsiveLayout

/**
 * CINEMATIC DARK SCHEME (Default)
 */
private val BlackColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = Color.Black,
    primaryContainer = PrimaryCyanVariant,
    onPrimaryContainer = Color.White,
    
    secondary = SecondaryPurple,
    onSecondary = Color.White,
    
    background = DeepBackground,
    onBackground = OnSurfaceLight,
    
    surface = BackgroundDark,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceDarkVariant,
    onSurfaceVariant = OnSurfaceMuted,
    
    error = ErrorRed,
    onError = Color.White,
    
    outline = GlassBorder,
    outlineVariant = GlassWhite
)

/**
 * MATERIAL 3 SHAPES WRAPPER
 */
private val Shapes = Shapes(
    small = AppShapes.small,
    medium = AppShapes.medium,
    large = AppShapes.large
)

@Composable
fun MeusCanaisTheme(
    typography: Typography = Typography,
    uiZoom: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = BlackColorScheme
    val appColors = ColorTokens()

    val appTypography = TypeTokens(
        display = typography.displayLarge,
        headline = typography.headlineLarge,
        title = typography.titleLarge,
        body = typography.bodyLarge,
        label = typography.labelLarge,
        caption = typography.labelSmall
    )

    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density * uiZoom,
        fontScale = currentDensity.fontScale
    )

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalColors provides appColors,
        LocalTypography provides appTypography
    ) {
        ResponsiveLayout {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
                shapes = Shapes,
                content = content
            )
        }
    }
}
