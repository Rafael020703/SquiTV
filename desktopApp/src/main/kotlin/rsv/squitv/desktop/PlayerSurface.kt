package rsv.squitv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import rsv.squitv.desktop.player.WindowsLivePlayer
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.MouseInfo
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.HierarchyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/** Shared by live and on-demand playback; controls stay outside the native surface. */
@Composable
internal fun NativeVideoSurface(engine: WindowsLivePlayer, modifier: Modifier,
    onPointerMove: (() -> Unit)? = null, onClick: () -> Unit = {}) {
    val pointerMove by rememberUpdatedState(onPointerMove)
    val click by rememberUpdatedState(onClick)
    val panel = remember(engine) {
        JPanel(BorderLayout()).apply {
            background = java.awt.Color.BLACK
            add(Canvas().apply {
                background = java.awt.Color.BLACK
                isFocusable = false
                addMouseMotionListener(object : MouseMotionAdapter() {
                    override fun mouseMoved(e: MouseEvent) { pointerMove?.invoke() }
                })
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) { if (e.button == MouseEvent.BUTTON1) click() }
                })
                addHierarchyListener { event ->
                    if (event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() != 0L && isDisplayable)
                        engine.attach(this)
                }
            }, BorderLayout.CENTER)
        }
    }
    DisposableEffect(panel, onPointerMove != null) {
        // LibVLC may own a child HWND that consumes AWT mouse motion. Sample only while
        // this app is active and the pointer is over the displayed video, never globally log it.
        var lastPoint: Point? = null
        val timer = if (onPointerMove == null) null else Timer(100) {
            val owner = SwingUtilities.getWindowAncestor(panel)
            var active = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            while (active != null && active != owner) active = active.owner
            if (owner != null && active == owner && panel.isShowing) {
                val point = MouseInfo.getPointerInfo()?.location
                if (point != null && point != lastPoint) {
                    val rect = Rectangle(panel.locationOnScreen, panel.size)
                    if (rect.contains(point)) pointerMove?.invoke()
                    lastPoint = point
                }
            } else lastPoint = null
        }.apply { start() }
        onDispose { timer?.stop() }
    }
    SwingPanel(factory = { panel }, modifier = modifier)
}
