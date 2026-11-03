package rsv.squitv.desktop

import androidx.compose.runtime.*

internal enum class DesktopImageKind { CHANNELS, POSTERS, DETAILS }

/** Mirrors the Android application image-loader lifetime, isolated to the current Windows account. */
internal class DesktopImages : AutoCloseable {
    private val loaders = mutableMapOf<DesktopImageKind, ChannelLogos>()
    fun loader(kind: DesktopImageKind): ChannelLogos = loaders.getOrPut(kind) { createImageLoader(kind) }
    override fun close() {
        loaders.values.forEach(ChannelLogos::close)
        loaders.clear()
    }
}

private fun createImageLoader(kind: DesktopImageKind): ChannelLogos = when (kind) {
    DesktopImageKind.CHANNELS -> ChannelLogos(128, 512 * 1024, 4L * 1024 * 1024)
    DesktopImageKind.POSTERS -> ChannelLogos(384, 2 * 1024 * 1024, 16L * 1024 * 1024)
    DesktopImageKind.DETAILS -> ChannelLogos(512, 2 * 1024 * 1024, 8L * 1024 * 1024)
}

internal val LocalDesktopImages = staticCompositionLocalOf<DesktopImages?> { null }

@Composable
internal fun rememberDesktopImages(kind: DesktopImageKind): ChannelLogos {
    val owner = LocalDesktopImages.current
    if (owner != null) return remember(owner, kind) { owner.loader(kind) }
    // Standalone previews retain their own bounded loader and never acquire account state.
    val fallback = remember(kind) { createImageLoader(kind) }
    DisposableEffect(fallback) { onDispose { fallback.close() } }
    return fallback
}
