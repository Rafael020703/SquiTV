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
import java.awt.GraphicsDevice
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowStateListener
import java.beans.PropertyChangeListener
import javax.swing.JWindow
import javax.swing.SwingUtilities
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class PlayerLayerPlacement { BOTTOM, BOTTOM_END, TOP, TOP_START, START, CENTER }

/** An owned lightweight HUD window above LibVLC's heavyweight Canvas, without copying video frames.
 * It follows the owner, hides when another app gains focus, and is disposed with the player. */
@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun NativePlayerLayer(visible: Boolean, placement: PlayerLayerPlacement,
    heightDp: Int?, widthDp: Int? = null, interactive: Boolean = true, transparent: Boolean = true,
    onKey: (KeyEvent) -> Boolean = { false }, onInputCancelled: () -> Unit = {}, content: @Composable () -> Unit) {
    val owner = LocalDesktopWindow.current ?: return
    val density = LocalDensity.current
    val currentContent = rememberUpdatedState(content)
    val currentKey = rememberUpdatedState(onKey)
    val currentInputCancelled = rememberUpdatedState(onInputCancelled)
    val currentDensity = rememberUpdatedState(density)
    val hud = remember(owner, transparent) {
        val device = owner.graphicsConfiguration.device
        val alphaConfiguration = if (transparent && device.isWindowTranslucencySupported(
                GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT))
            device.configurations.firstOrNull { it.isTranslucencyCapable } else null
        JWindow(owner, alphaConfiguration ?: owner.graphicsConfiguration).apply {
            // Only the small HUD is translucent. Never change owner opacity/fullscreen or render video frames.
            background = if (alphaConfiguration != null) java.awt.Color(0, 0, 0, 0) else java.awt.Color(12, 14, 20)
            rootPane.isOpaque = false
            layeredPane.isOpaque = false
            (contentPane as? javax.swing.JComponent)?.isOpaque = false
            isAutoRequestFocus = false
            focusableWindowState = interactive
        }
    }
    val uiScale = density.density / owner.graphicsConfiguration.defaultTransform.scaleY
    fun bounds(): Rectangle {
        val margin = (16 * uiScale).roundToInt()
        val width = widthDp?.let { min((it * uiScale).roundToInt(), owner.width - margin * 2) } ?: owner.width
        val height = heightDp?.let { min((it * uiScale).roundToInt(), owner.height - margin * 2) } ?: owner.height
        val x = when (placement) {
            PlayerLayerPlacement.BOTTOM, PlayerLayerPlacement.TOP, PlayerLayerPlacement.START -> owner.x
            PlayerLayerPlacement.TOP_START -> owner.x + margin
            PlayerLayerPlacement.CENTER -> owner.x + (owner.width - width) / 2
            PlayerLayerPlacement.BOTTOM_END -> owner.x + owner.width - width - margin
        }
        val y = when (placement) {
            PlayerLayerPlacement.BOTTOM_END -> owner.y + owner.height - height - margin
            PlayerLayerPlacement.BOTTOM -> owner.y + owner.height - height
            PlayerLayerPlacement.TOP, PlayerLayerPlacement.START -> owner.y
            PlayerLayerPlacement.TOP_START -> owner.y + margin
            PlayerLayerPlacement.CENTER -> owner.y + (owner.height - height) / 2
        }
        return Rectangle(x, y, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }
    val currentBounds = rememberUpdatedState(::bounds)
    val requestedVisible = rememberUpdatedState(visible)
    val wasPresented = remember(hud) { AtomicBoolean(false) }
    val focus = remember { KeyboardFocusManager.getCurrentKeyboardFocusManager() }
    fun belongsToOwner(): Boolean {
        var active = focus.activeWindow
        while (active != null) { if (active == owner) return true; active = active.owner }
        return false
    }
    val updateVisibility: () -> Unit = {
        val minimized = owner is Frame && owner.extendedState and Frame.ICONIFIED != 0
        val show = requestedVisible.value && owner.isShowing && !minimized && belongsToOwner()
        // A release event can be lost when Windows hides/minimizes the HUD mid-gesture.
        // Keep menu content/state, but release interaction holds outside this window family.
        if (show) wasPresented.set(true)
        else if (wasPresented.getAndSet(false)) SwingUtilities.invokeLater {
            // activeWindow may briefly be null while focus moves between owner and HUD.
            val stillMinimized = owner is Frame && owner.extendedState and Frame.ICONIFIED != 0
            if (hud.isDisplayable && (!owner.isShowing || stillMinimized || !belongsToOwner()))
                currentInputCancelled.value()
        }
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
            background = hud.background
            isOpaque = false
            setContent {
                CompositionLocalProvider(LocalDensity provides currentDensity.value) {
                    MaterialTheme(colorScheme = palette, typography = desktopTypography) {
                        Box(Modifier.fillMaxSize().onPreviewKeyEvent { currentKey.value(it) }.directionalNavigation()) {
                            // Do not keep hidden controls/pulsing badges composing during playback.
                            if (requestedVisible.value) currentContent.value()
                        }
                    }
                }
            }
        }
        hud.contentPane.add(panel)
        val listener = object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) { update.value() }
            override fun componentResized(e: ComponentEvent) { update.value() }
            override fun componentShown(e: ComponentEvent) { update.value() }
            override fun componentHidden(e: ComponentEvent) {
                if (wasPresented.getAndSet(false)) currentInputCancelled.value()
                hud.isVisible = false
            }
        }
        val focusListener = PropertyChangeListener { update.value() }
        val stateListener = WindowStateListener { update.value() }
        owner.addComponentListener(listener)
        (owner as? Frame)?.addWindowStateListener(stateListener)
        focus.addPropertyChangeListener("activeWindow", focusListener)
        onDispose {
            owner.removeComponentListener(listener)
            (owner as? Frame)?.removeWindowStateListener(stateListener)
            wasPresented.set(false)
            focus.removePropertyChangeListener("activeWindow", focusListener)
            hud.isVisible = false
            panel.dispose()
            hud.dispose()
        }
    }
    SideEffect { updateVisibility() }
}
