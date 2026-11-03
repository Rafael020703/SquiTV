package rsv.squitv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/** Receives only this JVM's events; does not install a Windows-wide input hook. */
@Composable
internal fun MouseBackNavigation(owner: Window) {
    DisposableEffect(owner) {
        val toolkit = Toolkit.getDefaultToolkit()
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        fun belongsToOwner(window: Window?): Boolean {
            var current = window
            while (current != null) {
                if (current == owner) return true
                current = current.owner
            }
            return false
        }
        fun windowOf(component: Component): Window? = component as? Window ?: SwingUtilities.getWindowAncestor(component)
        var pressedIn: Window? = null
        val listener = AWTEventListener { raw ->
            val event = raw as? MouseEvent ?: return@AWTEventListener
            if (event.button != 4) return@AWTEventListener
            val sourceWindow = windowOf(event.component)
            val pressedWindow = pressedIn
            if (event.id == MouseEvent.MOUSE_RELEASED) pressedIn = null
            if (!belongsToOwner(sourceWindow) || !belongsToOwner(focus.activeWindow)) return@AWTEventListener
            when (event.id) {
                MouseEvent.MOUSE_PRESSED -> { pressedIn = sourceWindow; event.consume() }
                MouseEvent.MOUSE_CLICKED -> event.consume()
                MouseEvent.MOUSE_RELEASED -> {
                    event.consume()
                    if (pressedWindow != sourceWindow) return@AWTEventListener
                    val target = focus.focusOwner?.takeIf { belongsToOwner(windowOf(it)) } ?: return@AWTEventListener
                    val targetWindow = windowOf(target)
                    // Route through the same focused Compose/dialog/HUD handlers as physical Esc.
                    // Dispatch the pair together before another frame can expose the screen below.
                    focus.redispatchEvent(target, KeyEvent(target, KeyEvent.KEY_PRESSED, event.`when`, 0,
                        KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))
                    if (targetWindow?.isShowing == true)
                        focus.redispatchEvent(target, KeyEvent(target, KeyEvent.KEY_RELEASED, event.`when`, 0,
                            KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))
                }
            }
        }
        toolkit.addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
        onDispose { toolkit.removeAWTEventListener(listener) }
    }
}
