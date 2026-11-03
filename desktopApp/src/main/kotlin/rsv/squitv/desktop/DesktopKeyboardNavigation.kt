package rsv.squitv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager

/** Bubble phase only: editors, sliders, menus and player shortcuts get first refusal. */
@Composable
internal fun rememberDirectionalNavigation(): (KeyEvent) -> Boolean {
    val focus = LocalFocusManager.current
    val input = LocalInputModeManager.current
    return remember(focus, input) { { event: KeyEvent ->
        val direction = when (event.key) {
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            else -> null
        }
        if (direction == null || event.type != KeyEventType.KeyDown || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) false
        else {
            input.requestInputMode(InputMode.Keyboard)
            focus.moveFocus(direction)
        }
    } }
}

internal fun Modifier.directionalNavigation(): Modifier = composed {
    // Resolve the owner where attached: AlertDialog has its own Compose focus manager.
    val navigation = rememberDirectionalNavigation()
    onKeyEvent(navigation)
}
