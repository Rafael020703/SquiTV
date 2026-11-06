package rsv.squitv.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.MouseInfo
import java.awt.Point

/** Existing noninteractive content can drag the window without reserving a title strip. */
internal val LocalWindowDragArea = staticCompositionLocalOf<@Composable (Modifier, @Composable () -> Unit) -> Unit> {
    { modifier, content -> Box(modifier) { content() } }
}

internal val LocalDesktopWindow = staticCompositionLocalOf<java.awt.Window?> { null }

/** Only wraps headings/other noninteractive content; buttons and editors keep their gestures. */
@Composable
internal fun WindowDragArea(window: java.awt.Window, enabled: Boolean, modifier: Modifier,
    onDoubleClick: () -> Unit, content: @Composable () -> Unit) {
    val doubleClick = rememberUpdatedState(onDoubleClick)
    val drag = if (!enabled) Modifier else Modifier.pointerHoverIcon(PointerIcon.Hand)
        .pointerInput(window) {
            var origin: Point? = null
            var pointer: Point? = null
            detectDragGestures(
                onDragStart = {
                    origin = window.location
                    pointer = MouseInfo.getPointerInfo()?.location
                },
                onDragEnd = { origin = null; pointer = null },
                onDragCancel = { origin = null; pointer = null },
                onDrag = { change, _ ->
                    val start = origin
                    val pressedAt = pointer
                    val current = MouseInfo.getPointerInfo()?.location
                    if (start != null && pressedAt != null && current != null) {
                        // AWT screen coordinates remain stable while the window itself moves,
                        // including on monitors with a different scaling factor.
                        window.setLocation(start.x + current.x - pressedAt.x, start.y + current.y - pressedAt.y)
                        change.consume()
                    }
                })
        }
    Box(modifier.then(drag).pointerInput(window) {
        detectTapGestures(onDoubleTap = { doubleClick.value() })
    }) { content() }
}
