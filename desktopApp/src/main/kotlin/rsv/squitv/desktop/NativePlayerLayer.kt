package rsv.squitv.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.RenderSettings
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import javax.swing.JWindow
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class PlayerLayerPlacement { BOTTOM, TOP_START, CENTER }

/** An owned lightweight HUD window above LibVLC's heavyweight Canvas, without copying video frames.
 * It follows the owner, hides when another app gains focus, and is disposed with the player. */
@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun NativePlayerLayer(visible: Boolean, placement: PlayerLayerPlacement,
    heightDp: Int, widthDp: Int? = null, interactive: Boolean = true,
    onKey: (KeyEvent) -> Boolean = { false }, content: @Composable () -> Unit) {
    val owner = LocalDesktopWindow.current ?: return
    val density = LocalDensity.current
    val currentContent = rememberUpdatedState(content)
    val currentKey = rememberUpdatedState(onKey)
    val currentDensity = rememberUpdatedState(density)
    val hud = remember(owner) {
        JWindow(owner).apply {
            background = java.awt.Color(12, 14, 20)
            isAutoRequestFocus = false
            focusableWindowState = interactive
        }
    }
    val uiScale = density.density / owner.graphicsConfiguration.defaultTransform.scaleY
    fun bounds(): Rectangle {
        val margin = (16 * uiScale).roundToInt()
        val width = widthDp?.let { min((it * uiScale).roundToInt(), owner.width - margin * 2) } ?: owner.width
        val height = min((heightDp * uiScale).roundToInt(), owner.height - margin * 2)
        val x = when (placement) {
            PlayerLayerPlacement.BOTTOM -> owner.x
            PlayerLayerPlacement.TOP_START -> owner.x + margin
            PlayerLayerPlacement.CENTER -> owner.x + (owner.width - width) / 2
        }
        val y = when (placement) {
            PlayerLayerPlacement.BOTTOM -> owner.y + owner.height - height
            PlayerLayerPlacement.TOP_START -> owner.y + margin
            PlayerLayerPlacement.CENTER -> owner.y + (owner.height - height) / 2
        }
        return Rectangle(x, y, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }
    val currentBounds = rememberUpdatedState(::bounds)
    val requestedVisible = rememberUpdatedState(visible)
    val focus = remember { KeyboardFocusManager.getCurrentKeyboardFocusManager() }
    fun belongsToOwner(): Boolean {
        var active = focus.activeWindow
        while (active != null) { if (active == owner) return true; active = active.owner }
        return false
    }
    val updateVisibility: () -> Unit = {
        val show = requestedVisible.value && owner.isShowing && belongsToOwner()
        if (show) hud.bounds = currentBounds.value()
        val wasVisible = hud.isVisible
        hud.isVisible = show
        if (show && !wasVisible && interactive) hud.requestFocus()
    }
    val update = rememberUpdatedState(updateVisibility)
    DisposableEffect(hud) {
        // A separate SkiaSurface calls GraphicsDevice.setFullScreenWindow(null) when shown
        // with its default fullscreen=false, clearing the MAIN window's exclusive fullscreen.
        // SwingGraphics draws only this small HUD and never owns the device fullscreen state.
        // LibVLC's video surface and the main Compose window keep their existing renderers.
        val panel = ComposePanel(renderSettings = RenderSettings.SwingGraphics()).apply {
            background = java.awt.Color(12, 14, 20)
            setContent {
                CompositionLocalProvider(LocalDensity provides currentDensity.value) {
                    MaterialTheme(colorScheme = palette, typography = desktopTypography) {
                        Box(Modifier.fillMaxSize().onPreviewKeyEvent { currentKey.value(it) }) { currentContent.value() }
                    }
                }
            }
        }
        hud.contentPane.add(panel)
        val listener = object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) { update.value() }
            override fun componentResized(e: ComponentEvent) { update.value() }
            override fun componentShown(e: ComponentEvent) { update.value() }
            override fun componentHidden(e: ComponentEvent) { hud.isVisible = false }
        }
        val focusListener = PropertyChangeListener { update.value() }
        owner.addComponentListener(listener)
        focus.addPropertyChangeListener("activeWindow", focusListener)
        onDispose {
            owner.removeComponentListener(listener)
            focus.removePropertyChangeListener("activeWindow", focusListener)
            hud.isVisible = false
            panel.dispose()
            hud.dispose()
        }
    }
    SideEffect { updateVisibility() }
}
