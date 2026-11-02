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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.coroutines.resume
import kotlin.math.max

internal class ChannelLogos(private val maximumSide: Int = 128, private val maximumBytes: Int = 512 * 1024) : AutoCloseable {
    private val client = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val slots = Semaphore(4)
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>) = size > 64
    }

    suspend fun load(address: String?): ImageBitmap? {
        if (address.isNullOrBlank()) return null
        synchronized(cache) { if (cache.containsKey(address)) return cache[address] }
        return slots.withPermit {
            synchronized(cache) { if (cache.containsKey(address)) return@withPermit cache[address] }
            val request = runCatching { Request.Builder().url(address).build() }.getOrNull() ?: return@withPermit null
            if (request.url.scheme !in setOf("http", "https") || request.url.username.isNotEmpty() || request.url.password.isNotEmpty())
                return@withPermit null
            val bytes = suspendCancellableCoroutine<ByteArray?> { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val data = runCatching { response.use {
                            if (!it.isSuccessful || it.body.contentLength() > maximumBytes) return@use null
                            it.body.byteStream().use { stream ->
                                stream.readNBytes(maximumBytes + 1).takeIf { value -> value.size <= maximumBytes }
                            }
                        } }.getOrNull()
                        if (continuation.isActive) continuation.resume(data)
                    }
                })
            }
            val bitmap = if (bytes == null) null else withContext(Dispatchers.IO) {
                runCatching {
                    ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                        val readers = ImageIO.getImageReaders(input)
                        if (!readers.hasNext()) return@use null
                        val reader = readers.next()
                        try {
                            reader.input = input
                            val width = reader.getWidth(0); val height = reader.getHeight(0)
                            if (width <= 0 || height <= 0 || width.toLong() * height > 16_000_000) return@use null
                            val sample = max(1, max(width, height) / maximumSide)
                            val params = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                            reader.read(0, params).toComposeImageBitmap()
                        } finally { reader.dispose() }
                    }
                }.getOrNull()
            }
            synchronized(cache) { cache[address] = bitmap }
            bitmap
        }
    }

    override fun close() {
        client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        synchronized(cache) { cache.clear() }
    }
}

@Composable
internal fun ChannelLogo(address: String?, logos: ChannelLogos, modifier: Modifier = Modifier.size(40.dp)) {
    val bitmap by produceState<ImageBitmap?>(null, address, logos) { value = logos.load(address) }
    bitmap?.let { Image(it, null, modifier, contentScale = ContentScale.Fit) }
        ?: Icon(Icons.Rounded.Monitor, null, tint = palette.primary.copy(alpha = .45f), modifier = modifier)
}
