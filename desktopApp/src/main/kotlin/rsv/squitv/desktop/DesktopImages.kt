package rsv.squitv.desktop

import androidx.compose.runtime.*

internal enum class DesktopImageKind { CHANNELS, POSTERS, DETAILS }

/** Mirrors the Android application image-loader lifetime, isolated to the current Windows account. */
internal class DesktopImages(private val baseUrl: String? = null) : AutoCloseable {
    private val loaders = mutableMapOf<DesktopImageKind, ChannelLogos>()
    fun loader(kind: DesktopImageKind): ChannelLogos = loaders.getOrPut(kind) { createImageLoader(kind, baseUrl) }
    override fun close() {
        loaders.values.forEach(ChannelLogos::close)
        loaders.clear()
    }
}

private fun createImageLoader(kind: DesktopImageKind, baseUrl: String? = null): ChannelLogos = when (kind) {
    DesktopImageKind.CHANNELS -> ChannelLogos(128, 2 * 1024 * 1024, 4L * 1024 * 1024, baseUrl)
    DesktopImageKind.POSTERS -> ChannelLogos(384, 4 * 1024 * 1024, 16L * 1024 * 1024, baseUrl)
    DesktopImageKind.DETAILS -> ChannelLogos(512, 6 * 1024 * 1024, 8L * 1024 * 1024, baseUrl)
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
