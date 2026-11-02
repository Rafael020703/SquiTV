package rsv.squitv.core.ui.components.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Android portal backdrop; no external images or platform services. */
@Composable
fun PortalBackdrop(background: Color, surfaceVariant: Color, primary: Color,
    showAtmosphere: Boolean = false, atmosphere: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        if (showAtmosphere) {
            atmosphere()

            // Deep vignette and color grading
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                surfaceVariant.copy(alpha = 0.12f),
                                background.copy(alpha = 0.85f),
                                background
                            ),
                            center = Offset(0.5f, 0.4f),
                            radius = 1800f
                        )
                    )
            )

            // Thematic Glow Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(0.8f, 0.2f),
                            radius = 1500f
                        )
                    )
            )
        }

        // Subtle Radial Gradient for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.03f), Color.Transparent),
                        center = Offset(0.5f, 0.3f),
                        radius = 1500f
                    )
                )
        )

        // Dot pattern
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotSpacing = 24.dp.toPx()
            val dotColor = Color.White.copy(alpha = 0.02f)
            val strokeWidth = 1.2.dp.toPx()
            val step = dotSpacing.toInt()
            val width = size.width.toInt()
            val height = size.height.toInt()
            if (width > 0 && height > 0 && step > 0) {
                val pointCount = ((width / step) + 1) * ((height / step) + 1)
                val points = ArrayList<Offset>(pointCount)
                for (x in 0..width step step) {
                    for (y in 0..height step step) {
                        points.add(Offset(x.toFloat(), y.toFloat()))
                    }
                }
                drawPoints(
                    points = points,
                    pointMode = androidx.compose.ui.graphics.PointMode.Points,
                    color = dotColor,
                    strokeWidth = strokeWidth
                )
            }
        }
        content()
    }
}
