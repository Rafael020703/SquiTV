package rsv.squitv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import kotlin.math.min

/** Borderless windowed fullscreen: never calls GraphicsDevice.setFullScreenWindow. */
@Composable
internal fun MaintainBorderlessWindow(window: Window, fullscreen: Boolean, showing: Boolean) {
    val mode = remember(window) { BorderlessWindowMode(window) }
    DisposableEffect(mode, fullscreen, showing) {
        if (showing) mode.apply(fullscreen)
        val listener = object : ComponentAdapter() {
            override fun componentMoved(event: ComponentEvent) { if (showing && fullscreen) mode.fitMonitor() }
            override fun componentResized(event: ComponentEvent) { if (showing && fullscreen) mode.fitMonitor() }
        }
        val configuration = PropertyChangeListener { if (showing && fullscreen) mode.fitMonitor() }
        window.addComponentListener(listener)
        window.addPropertyChangeListener("graphicsConfiguration", configuration)
        onDispose {
            window.removeComponentListener(listener)
            window.removePropertyChangeListener("graphicsConfiguration", configuration)
        }
    }
}

private class BorderlessWindowMode(private val window: Window) {
    private var floating: Rectangle? = null
    private var fullscreen = false

    fun apply(value: Boolean) {
        if (value == fullscreen) { if (value) fitMonitor(); return }
        fullscreen = value
        if (value) {
            floating = Rectangle(window.bounds)
            fitMonitor()
        } else {
            val bounds = Rectangle(window.graphicsConfiguration.bounds)
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(window.graphicsConfiguration)
            bounds.x += insets.left; bounds.y += insets.top
            bounds.width -= insets.left + insets.right; bounds.height -= insets.top + insets.bottom
            val old = floating ?: Rectangle(bounds.x, bounds.y, 1120, 630)
            val units = min(min(old.width / 16, old.height / 9), min(bounds.width / 16, bounds.height / 9)).coerceAtLeast(1)
            val width = units * 16; val height = units * 9
            window.setBounds(old.x.coerceIn(bounds.x, bounds.x + bounds.width - width),
                old.y.coerceIn(bounds.y, bounds.y + bounds.height - height), width, height)
        }
    }

    fun fitMonitor() {
        val bounds = window.graphicsConfiguration.bounds
        if (window.bounds != bounds) window.bounds = Rectangle(bounds)
    }
}
