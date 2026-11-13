package rsv.squitv.desktop.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import rsv.squitv.shared.xtream.*
import rsv.squitv.data.model.*
import kotlinx.serialization.json.Json
import rsv.squitv.desktop.liveCategoryScope
import rsv.squitv.shared.account.FavoriteCloudSession

class ServerProblem(message: String) : IOException(message)

class XtreamClient(private val client: OkHttpClient = OkHttpClient.Builder()
    .dns(rsv.squitv.shared.network.AppDns)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
    .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    .also(rsv.squitv.shared.network.AppDns::register)) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun catalogCategories(credentials: ServerCredentials, series: Boolean): List<LiveCategory> =
        XtreamCatalogParser.categories(request(credentials, if (series) "get_series_categories" else "get_vod_categories"))

    suspend fun movies(credentials: ServerCredentials, categoryId: String?): List<XtreamVod> =
        json.decodeFromString(request(credentials, "get_vod_streams", categoryId))

    suspend fun series(credentials: ServerCredentials, categoryId: String?): List<XtreamSeries> =
        json.decodeFromString(request(credentials, "get_series", categoryId))

    suspend fun movieInfo(credentials: ServerCredentials, id: Int): XtreamVodInfo =
        cloudCatalog(credentials)?.catalogDetail("movie", id, XtreamVodInfo.serializer())
            ?: json.decodeFromString(request(credentials, "get_vod_info", itemParameter = "vod_id" to id))

    suspend fun seriesInfo(credentials: ServerCredentials, id: Int): XtreamSeriesInfo =
        cloudCatalog(credentials)?.catalogDetail("series", id, XtreamSeriesInfo.serializer())
            ?: json.decodeFromString(request(credentials, "get_series_info", itemParameter = "series_id" to id))

    suspend fun login(credentials: ServerCredentials): ServerAccount =
        XtreamCatalogParser.account(request(credentials), credentials.username, System.currentTimeMillis() / 1000)

    suspend fun categories(credentials: ServerCredentials): List<LiveCategory> =
        XtreamCatalogParser.categories(request(credentials, "get_live_categories"))

    suspend fun channels(credentials: ServerCredentials, categoryId: String?): List<LiveChannel> =
        XtreamCatalogParser.channels(request(credentials, "get_live_streams", categoryId))

    suspend fun epg(credentials: ServerCredentials, streamId: Int): List<LiveProgramme> =
        XtreamCatalogParser.epg(cloudCatalog(credentials)?.catalogDetail(
            "live", streamId, kotlinx.serialization.json.JsonObject.serializer(),
        )?.toString() ?: request(credentials, "get_short_epg", streamId = streamId))

    private fun cloudCatalog(credentials: ServerCredentials) =
        FavoriteCloudSession.catalogClient(liveCategoryScope(credentials))

    private suspend fun request(credentials: ServerCredentials, action: String? = null, category: String? = null,
        streamId: Int? = null, itemParameter: Pair<String, Int>? = null): String {
        val url = credentials.baseUrl.toHttpUrl().newBuilder().addPathSegment("player_api.php")
            .addQueryParameter("username", credentials.username).addQueryParameter("password", credentials.password)
            .apply {
                action?.let { addQueryParameter("action", it) }
                category?.let { addQueryParameter("category_id", it) }
                streamId?.let { addQueryParameter("stream_id", it.toString()); addQueryParameter("limit", "5") }
                itemParameter?.let { (name, id) -> addQueryParameter(name, id.toString()) }
            }.build()
        return withTimeoutOrNull(45_000L) { followRedirects(url) }
            ?: throw ServerProblem("O servidor demorou para responder. Tente novamente.")
    }

    private suspend fun followRedirects(url: HttpUrl): String {
        var current = url
        val visited = mutableSetOf<HttpUrl>()
        repeat(4) {
            if (!visited.add(current)) throw ServerProblem("O servidor repetiu o redirecionamento. Tente novamente mais tarde.")
            when (val result = fetch(current)) {
                is ApiResponse.Body -> return result.text
                is ApiResponse.Redirect -> {
                    val next = current.resolve(result.location)
                        ?: throw ServerProblem("O servidor informou um redirecionamento inválido.")
                    if (!allowsRedirect(url, current, next)) throw ServerProblem(
                        "O servidor redirecionou a consulta para outro endereço. Confira o DNS selecionado.")
                    current = next
                }
            }
        }
        throw ServerProblem("O servidor excedeu o limite de redirecionamentos. Tente novamente mais tarde.")
    }

    // Follow only the server selected by the user; never copy credentials to another host,
    // accept user-info URLs, or downgrade an HTTPS request. Keep OkHttp's global redirect off.
    private fun allowsRedirect(original: HttpUrl, current: HttpUrl, next: HttpUrl): Boolean =
        next.host == original.host && next.scheme in setOf("http", "https") &&
            !(current.isHttps && !next.isHttps) && next.username.isEmpty() && next.password.isEmpty() &&
            listOf("username", "password").all { key ->
                next.queryParameterValues(key).all { it == original.queryParameter(key) }
            }

    private sealed interface ApiResponse {
        data class Body(val text: String) : ApiResponse
        data class Redirect(val location: String) : ApiResponse
    }

    private suspend fun fetch(url: HttpUrl): ApiResponse {
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "IPTVSmarters").build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        ServerProblem("Não foi possível acessar o servidor. Confira o endereço e a conexão."))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (it.code in setOf(301, 302, 303, 307, 308)) {
                                return@use ApiResponse.Redirect(it.header("Location")
                                    ?: throw ServerProblem("O servidor não informou o destino do redirecionamento."))
                            }
                            if (!it.isSuccessful) throw ServerProblem(when (it.code) {
                                401, 403 -> "O servidor recusou o acesso. Confira usuário e senha."
                                429 -> "O servidor está limitando as consultas. Aguarde e tente novamente."
                                in 300..399 -> "O servidor redirecionou o acesso. Informe o endereço final do servidor."
                                else -> "O servidor não respondeu corretamente. Tente novamente."
                            })
                            val body = it.body
                            val limit = 16 * 1024 * 1024
                            if (body.contentLength() > limit) throw ServerProblem("A resposta do servidor excedeu o limite permitido.")
                            body.byteStream().use { stream ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > limit) throw ServerProblem("A resposta do servidor excedeu o limite permitido.")
                                    output.write(buffer, 0, count)
                                }
                                ApiResponse.Body(output.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
                            }
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            e as? ServerProblem ?: ServerProblem("Não foi possível ler a resposta do servidor."))
                    }
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
