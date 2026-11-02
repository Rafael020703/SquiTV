package rsv.squitv.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Existing noninteractive content can drag the window without reserving a title strip. */
internal val LocalWindowDragArea = staticCompositionLocalOf<@Composable (Modifier, @Composable () -> Unit) -> Unit> {
    { modifier, content -> Box(modifier) { content() } }
}

internal val LocalDesktopWindow = staticCompositionLocalOf<java.awt.Window?> { null }
