package rsv.squitv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Constrains only the floating window; fullscreen uses the monitor's native bounds. */
@Composable
internal fun MaintainWindowAspectRatio(window: Window, state: WindowState) {
    LaunchedEffect(state.placement) {
        // Maximizing to the work area would break 16:9 on non-16:9 desktops.
        // F11 is the explicit way to occupy the entire display.
        if (state.placement == WindowPlacement.Maximized) state.placement = WindowPlacement.Floating
    }
    DisposableEffect(window, state) {
        val config = window.graphicsConfiguration
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        val workWidth = config.bounds.width - insets.left - insets.right
        val workHeight = config.bounds.height - insets.top - insets.bottom
        val maximumUnits = min(workWidth / 16, workHeight / 9).coerceAtLeast(1)
        val minimumUnits = min(60, maximumUnits)
        window.minimumSize = Dimension(16 * minimumUnits, 9 * minimumUnits)
        var previous = Dimension(window.size)
        val listener = object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                if (state.placement != WindowPlacement.Floating || !window.isDisplayable) return
                val current = window.size
                if (current.width <= 0 || current.height <= 0) return
                if (current.width * 9L == current.height * 16L) {
                    previous = Dimension(current)
                    return
                }
                val widthChange = abs(current.width - previous.width) / 16.0
                val heightChange = abs(current.height - previous.height) / 9.0
                val units = (if (widthChange >= heightChange) current.width / 16.0 else current.height / 9.0)
                    .roundToInt().coerceAtLeast(minimumUnits)
                val corrected = Dimension(units * 16, units * 9)
                previous = corrected
                if (current != corrected) window.size = corrected
            }
        }
        window.addComponentListener(listener)
        onDispose { window.removeComponentListener(listener) }
    }
}
