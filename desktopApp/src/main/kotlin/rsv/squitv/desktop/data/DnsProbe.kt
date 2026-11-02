package rsv.squitv.desktop.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*

class DnsProbe : AutoCloseable {
    private val client = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun latency(url: String): Long {
        val call = client.newCall(Request.Builder().url(url).head().build())
        val start = System.nanoTime()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(-1)
                }
                override fun onResponse(call: Call, response: Response) {
                    val elapsed = response.use {
                        if (it.code in 200..499) ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1) else -1
                    }
                    if (continuation.isActive) continuation.resume(elapsed)
                }
            })
        }
    }

    override fun close() {
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
