package rsv.squitv.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import rsv.squitv.shared.catalog.ContentImages
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import kotlin.coroutines.resume
import kotlin.math.max

internal class ChannelLogos(private val maximumSide: Int = 128, private val maximumBytes: Int = 512 * 1024,
    private val maximumCacheBytes: Long = 16L * 1024 * 1024, private val baseUrl: String? = null) : AutoCloseable {
    private val client = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val slots = Semaphore(4)
    private data class CachedImage(val bitmap: ImageBitmap?, val created: Long = System.nanoTime()) {
        val bytes: Long = bitmap?.let { it.width.toLong() * it.height * 4 } ?: 0L
    }
    private val cache = LinkedHashMap<String, CachedImage>(64, .75f, true)
    private var cacheBytes = 0L
    private val closed = AtomicBoolean(false)
    private class PendingImage(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val pending = mutableMapOf<String, PendingImage>()

    private fun cached(address: String): CachedImage? = synchronized(cache) {
        val entry = cache[address] ?: return@synchronized null
        if (entry.bitmap == null && System.nanoTime() - entry.created > TimeUnit.SECONDS.toNanos(30)) {
            cache.remove(address)
            null
        } else entry
    }

    private fun cache(address: String, bitmap: ImageBitmap?) = synchronized(cache) {
        if (closed.get()) return@synchronized
        if (bitmap == null && cache[address]?.bitmap != null) return@synchronized
        val entry = CachedImage(bitmap)
        cache.remove(address)?.let { cacheBytes -= it.bytes }
        cache[address] = entry
        cacheBytes += entry.bytes
        val iterator = cache.entries.iterator()
        while ((cache.size > 64 || cacheBytes > maximumCacheBytes) && iterator.hasNext()) {
            cacheBytes -= iterator.next().value.bytes
            iterator.remove()
        }
    }

    suspend fun load(rawAddress: String?): ImageBitmap? {
        if (closed.get()) return null
        val address = ContentImages.resolve(rawAddress, baseUrl) ?: return null
        cached(address)?.let { return it.bitmap }
        val request = synchronized(pending) {
            pending.getOrPut(address) { PendingImage() }.also { it.users++ }
        }
        return try {
            // A cancelled cell releases its lock; other cells can retry without inheriting its cancellation.
            request.mutex.withLock { loadImage(address) }
        } finally {
            synchronized(pending) {
                request.users--
                if (request.users == 0) pending.remove(address)
            }
        }
    }

    private suspend fun loadImage(address: String): ImageBitmap? = slots.withPermit {
        if (closed.get()) return@withPermit null
        cached(address)?.let { return@withPermit it.bitmap }
        val bytes = withTimeoutOrNull(12_000L) {
            var url = runCatching { Request.Builder().url(address).build().url }.getOrNull() ?: return@withTimeoutOrNull null
            val visited = mutableSetOf<HttpUrl>()
            repeat(4) {
                if (!visited.add(url) || url.username.isNotEmpty() || url.password.isNotEmpty()) return@withTimeoutOrNull null
                val response = fetch(url)
                val next = response.redirect
                if (next == null) return@withTimeoutOrNull response.bytes
                if (url.isHttps && !next.isHttps) return@withTimeoutOrNull null
                url = next
            }
            null
        }
        val bitmap = if (bytes == null) null else withContext(Dispatchers.IO) {
            val standard = runCatching {
                ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                    val readers = ImageIO.getImageReaders(input)
                    if (!readers.hasNext()) return@use null
                    val reader = readers.next()
                    try {
                        reader.input = input
                        val width = reader.getWidth(0); val height = reader.getHeight(0)
                        if (width <= 0 || height <= 0 || width.toLong() * height > 16_000_000) return@use null
                        val sample = max(1, ceil(max(width, height).toDouble() / maximumSide).toInt())
                        val params = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                        reader.read(0, params).toComposeImageBitmap()
                    } finally { reader.dispose() }
                }
            }.getOrNull()
            standard ?: runCatching { decodePortable(bytes) }.getOrNull()
        }
        cache(address, bitmap)
        bitmap ?: cached(address)?.bitmap
    }

    private data class ImageResponse(val bytes: ByteArray? = null, val redirect: HttpUrl? = null)

    private suspend fun fetch(url: HttpUrl): ImageResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("Accept", "image/webp,image/png,image/jpeg,image/svg+xml,image/*;q=0.8").build())
        continuation.invokeOnCancellation { call.cancel() }
        try { call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(ImageResponse())
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    if (it.code in setOf(301, 302, 303, 307, 308)) return@use ImageResponse(
                        redirect = it.header("Location")?.let(url::resolve))
                    if (!it.isSuccessful || it.body.contentLength() > maximumBytes) return@use ImageResponse()
                    ImageResponse(bytes = it.body.byteStream().use { input -> input.readNBytes(maximumBytes + 1)
                        .takeIf { bytes -> bytes.size <= maximumBytes } })
                } }.getOrDefault(ImageResponse())
                if (continuation.isActive) continuation.resume(result)
            }
        }) } catch (_: Exception) { if (continuation.isActive) continuation.resume(ImageResponse()) }
    }

    /** Skia ships with Compose already; use it for WebP and SVG missing from standard ImageIO. */
    private fun decodePortable(bytes: ByteArray): ImageBitmap? {
        val header = String(bytes, 0, minOf(bytes.size, 2048), Charsets.UTF_8)
        return org.jetbrains.skia.Data.makeFromBytes(bytes).use { data ->
            if (header.contains("<svg", ignoreCase = true)) {
                if (bytes.size > 512 * 1024 || String(bytes, Charsets.UTF_8).contains("<!ENTITY", true)) return@use null
                org.jetbrains.skia.svg.SVGDOM(data).use { svg ->
                    val root = svg.root ?: return@use null
                    val viewBox = root.viewBox
                    val width = viewBox?.width?.takeIf { it > 0 } ?: root.width.value.takeIf { it > 0 } ?: maximumSide.toFloat()
                    val height = viewBox?.height?.takeIf { it > 0 } ?: root.height.value.takeIf { it > 0 } ?: maximumSide.toFloat()
                    val ratio = minOf(1f, maximumSide / max(width, height))
                    val w = (width * ratio).roundToInt().coerceIn(1, maximumSide)
                    val h = (height * ratio).roundToInt().coerceIn(1, maximumSide)
                    org.jetbrains.skia.Surface.makeRasterN32Premul(w, h).use { surface ->
                        root.width = org.jetbrains.skia.svg.SVGLength(w.toFloat())
                        root.height = org.jetbrains.skia.svg.SVGLength(h.toFloat())
                        svg.setContainerSize(w.toFloat(), h.toFloat())
                        svg.render(surface.canvas)
                        surface.makeImageSnapshot().toComposeImageBitmap()
                    }
                }
            } else org.jetbrains.skia.Codec.makeFromData(data).use { codec ->
                val width = codec.width; val height = codec.height
                if (width <= 0 || height <= 0 || width.toLong() * height > 16_000_000) return@use null
                val ratio = minOf(1f, maximumSide.toFloat() / max(width, height))
                val w = (width * ratio).roundToInt().coerceAtLeast(1)
                val h = (height * ratio).roundToInt().coerceAtLeast(1)
                codec.readPixels().use { bitmap ->
                    org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { image ->
                        org.jetbrains.skia.Surface.makeRasterN32Premul(w, h).use { surface ->
                            surface.canvas.drawImageRect(image, org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()))
                            surface.makeImageSnapshot().toComposeImageBitmap()
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        synchronized(cache) { cache.clear(); cacheBytes = 0 }
    }
}

@Composable
internal fun ChannelLogo(address: String?, logos: ChannelLogos, modifier: Modifier = Modifier.size(40.dp)) {
    val bitmap by rememberRemoteBitmap(listOf(address), logos)
    bitmap?.let { Image(it, null, modifier, contentScale = ContentScale.Fit) }
        ?: Icon(Icons.Rounded.Monitor, null, tint = palette.primary.copy(alpha = .45f), modifier = modifier)
}


/** Reset reused cells immediately; a visible transient failure gets one bounded retry after cache expiry. */
@Composable
internal fun rememberRemoteBitmap(candidates: List<String?>, logos: ChannelLogos): State<ImageBitmap?> =
    produceState<ImageBitmap?>(null, candidates, logos) {
        value = null
        val addresses = candidates.mapNotNull(ContentImages::clean).distinct().take(3)
        if (addresses.isEmpty()) return@produceState
        repeat(2) { attempt ->
            for (address in addresses) {
                logos.load(address)?.let { value = it; return@produceState }
            }
            if (attempt == 0) delay(31_000)
        }
    }
