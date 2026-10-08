package rsv.squitv.data.network

import rsv.squitv.data.api.XtreamService
import nl.adaptivity.xmlutil.serialization.XML
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import timber.log.Timber
import rsv.squitv.core.debug.DebugNetworkInterceptor

object RetrofitClient {
    private const val USER_AGENT = "IPTVSmarters"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val baseUrlInterceptor = BaseUrlInterceptor()

    val xml = XML {
        // XML configuration for version 1.0.2.1
    }

    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(DebugNetworkInterceptor(isPlayerStream = false))
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor { message ->
            // Sanitize logs: Redact password and sensitive query params
            val redactedMessage = message
                .replace(Regex("password=[^&]*"), "password=[REDACTED]")
                .replace(Regex("username=[^&]*"), "username=[REDACTED]")
                .replace(Regex("token=[^&]*"), "token=[REDACTED]")
            Timber.tag("OkHttp").d(redactedMessage)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val streamRequestCounter = java.util.concurrent.atomic.AtomicInteger(100)

    val playerOkHttpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(0, 1, java.util.concurrent.TimeUnit.NANOSECONDS))
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .eventListener(object : okhttp3.EventListener() {
            override fun callStart(call: okhttp3.Call) {
                val path = call.request().url.encodedPath
                val sanitized = if (path.contains("/live/")) {
                    path.replace(Regex("/live/[^/]+/[^/]+/"), "/live/[REDACTED]/[REDACTED]/")
                } else path
                Timber.d("[SOCKET_EVENT] CALL_START path=$sanitized timestamp=${System.currentTimeMillis()}")
            }
            override fun connectStart(call: okhttp3.Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) {
                Timber.d("[SOCKET_EVENT] CONNECT_START addr=$inetSocketAddress timestamp=${System.currentTimeMillis()}")
            }
            override fun connectionAcquired(call: okhttp3.Call, connection: okhttp3.Connection) {
                Timber.d("[SOCKET_EVENT] CONNECTION_ACQUIRED timestamp=${System.currentTimeMillis()}")
            }
            override fun responseHeadersStart(call: okhttp3.Call) {
                Timber.d("[SOCKET_EVENT] RESPONSE_HEADERS_START timestamp=${System.currentTimeMillis()}")
            }
            override fun responseBodyStart(call: okhttp3.Call) {
                Timber.d("[SOCKET_EVENT] RESPONSE_BODY_START timestamp=${System.currentTimeMillis()}")
            }
            override fun connectionReleased(call: okhttp3.Call, connection: okhttp3.Connection) {
                Timber.d("[SOCKET_EVENT] CONNECTION_RELEASED timestamp=${System.currentTimeMillis()}")
            }
            override fun callEnd(call: okhttp3.Call) {
                Timber.d("[SOCKET_EVENT] CALL_END timestamp=${System.currentTimeMillis()}")
            }
            override fun callFailed(call: okhttp3.Call, ioe: java.io.IOException) {
                Timber.w(ioe, "[SOCKET_EVENT] CALL_FAILED exception=${ioe.javaClass.simpleName} msg=${ioe.message} timestamp=${System.currentTimeMillis()}")
            }
        })
        .addInterceptor { chain ->
            val reqId = streamRequestCounter.incrementAndGet()
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Connection", "close")
                .build()

            val urlPath = request.url.encodedPath
            val sanitizedPath = urlPath.replace(Regex("/live/[^/]+/[^/]+/"), "/live/[REDACTED]/[REDACTED]/")
            val startTime = System.currentTimeMillis()
            Timber.d("[STREAM_HTTP_START] requestId=$reqId path=$sanitizedPath timestamp=$startTime")

            try {
                val response = chain.proceed(request)
                val code = response.code
                val respTime = System.currentTimeMillis()
                val elapsedMs = respTime - startTime
                Timber.d("[STREAM_HTTP_RESPONSE] requestId=$reqId code=$code elapsedMs=${elapsedMs}ms timestamp=$respTime")
                response
            } catch (e: Exception) {
                val errTime = System.currentTimeMillis()
                val elapsedMs = errTime - startTime
                Timber.w(e, "[STREAM_HTTP_ERROR] requestId=$reqId exception=${e.javaClass.simpleName} elapsedMs=${elapsedMs}ms timestamp=$errTime")
                throw e
            }
        }
        .addInterceptor(DebugNetworkInterceptor(isPlayerStream = true))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        })
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun updateBaseUrl(newUrl: String) {
        var url = newUrl.trim()
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        Timber.i("[BASE_URL_CHANGED] Atualizando base URL para: %s", url)
        baseUrlInterceptor.updateBaseUrl(url)
    }

    fun createJsonService(baseUrl: String): XtreamService {
        var url = baseUrl.trim()
        if (url.isBlank()) {
            url = "http://localhost/"
        } else {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
        }
        if (!url.endsWith("/")) url += "/"
        
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(XtreamService::class.java)
    }

    fun createXmlService(baseUrl: String): EpgService {
        var url = baseUrl.trim()
        if (url.isBlank()) {
            url = "http://localhost/"
        } else {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
        }
        if (!url.endsWith("/")) url += "/"

        val xmlContentType = "application/xml".toMediaType()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(xml.asConverterFactory(xmlContentType))
            .build()
            .create(EpgService::class.java)
    }
}

class BaseUrlInterceptor : Interceptor {
    @Volatile
    private var baseUrl: String? = null

    fun updateBaseUrl(newUrl: String) {
        var url = newUrl.trim()
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (url.isNotEmpty() && !url.endsWith("/")) {
            url += "/"
        }
        this.baseUrl = url
    }

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        val currentBaseUrl = baseUrl

        // Do not alter scheme/host/port for external third-party services (GitHub API, GitHub CDN)
        if (isExternalHost(originalRequest.url.host)) {
            return chain.proceed(originalRequest)
        }

        val request = if (!currentBaseUrl.isNullOrBlank()) {
            val newUrl = currentBaseUrl.toHttpUrlOrNull()
            if (newUrl != null) {
                val updatedUrl = originalRequest.url.newBuilder()
                    .scheme(newUrl.scheme)
                    .host(newUrl.host)
                    .port(newUrl.port)
                    .build()
                originalRequest.newBuilder()
                    .url(updatedUrl)
                    .build()
            } else originalRequest
        } else originalRequest

        return chain.proceed(request)
    }

    private fun isExternalHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "api.github.com" ||
               h == "github.com" ||
               h == "githubusercontent.com" ||
               h.endsWith(".github.com") ||
               h.endsWith(".githubusercontent.com")
    }
}

interface EpgService {
    @retrofit2.http.GET
    suspend fun getEpg(@retrofit2.http.Url url: String): okhttp3.ResponseBody
}
