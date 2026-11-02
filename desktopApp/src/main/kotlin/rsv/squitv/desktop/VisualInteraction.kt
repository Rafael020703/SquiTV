package rsv.squitv.desktop

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp

/** Mirrors Android adaptiveFocus emphasis while also responding to a desktop pointer. */
internal class VisualInteraction(val source: MutableInteractionSource, val emphasized: Boolean)

@Composable
internal fun rememberVisualInteraction(): VisualInteraction {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    return VisualInteraction(source, focused || hovered || pressed)
}

@Composable
internal fun desktopScrollbarStyle(): ScrollbarStyle = LocalScrollbarStyle.current.copy(
    thickness = 5.dp, unhoverColor = palette.onSurfaceVariant.copy(alpha = .25f),
    hoverColor = palette.primary.copy(alpha = .7f))
