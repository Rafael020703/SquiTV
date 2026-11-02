package rsv.squitv.desktop.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import rsv.squitv.shared.xtream.*

class ServerProblem(message: String) : IOException(message)

class XtreamClient(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
    .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()) : AutoCloseable {

    suspend fun login(credentials: ServerCredentials): ServerAccount =
        XtreamCatalogParser.account(request(credentials), credentials.username, System.currentTimeMillis() / 1000)

    suspend fun categories(credentials: ServerCredentials): List<LiveCategory> =
        XtreamCatalogParser.categories(request(credentials, "get_live_categories"))

    suspend fun channels(credentials: ServerCredentials, categoryId: String): List<LiveChannel> =
        XtreamCatalogParser.channels(request(credentials, "get_live_streams", categoryId))

    private suspend fun request(credentials: ServerCredentials, action: String? = null, category: String? = null): String {
        val url = credentials.baseUrl.toHttpUrl().newBuilder().addPathSegment("player_api.php")
            .addQueryParameter("username", credentials.username).addQueryParameter("password", credentials.password)
            .apply {
                action?.let { addQueryParameter("action", it) }
                category?.let { addQueryParameter("category_id", it) }
            }.build()
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
                        val text = response.use {
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
                                output.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                            }
                        }
                        if (continuation.isActive) continuation.resume(text)
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
