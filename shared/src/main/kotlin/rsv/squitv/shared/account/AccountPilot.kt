package rsv.squitv.shared.account

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object AccountPilot {
    private const val ISSUER = "https://squitv.rafael020703.shop/auth/realms/squitv"
    private const val CLIENT = "squitv-tv"
    private const val MAX_LINKS = 20
    private const val LINK_URL = "https://squitv.rafael020703.shop/api/iptv/credentials"
    private const val PROVIDER = "https://u123.life"
    private const val SELECT_URL = "https://squitv.rafael020703.shop/api/iptv/select"
    private const val NATIVE_LOGIN_URL = "https://squitv.rafael020703.shop/api/iptv/native-login"
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(10, TimeUnit.SECONDS).build()
    val verificationPage = "$ISSUER/device"

    class LinkedLogin internal constructor(
        val username: String, val password: String, val baseUrl: String, val slot: Int,
        val displayName: String?,
        val favoriteClient: FavoriteCloudClient?,
    )

    data class LinkChoice(val slot: Int, val username: String)
    class LinkSelectionExpired : IOException("A seleção IPTV expirou. Entre novamente.")

    sealed interface LoginResult {
        class Connected internal constructor(val credentials: LinkedLogin) : LoginResult
        class Choose internal constructor(val choices: List<LinkChoice>, internal val token: String) : LoginResult
    }

    class Authorization internal constructor(
        val userCode: String, internal val deviceCode: String,
        internal val expiresAt: Long, internal val interval: Long,
    ) {
        val browserUrl: String get() = verificationPage.toHttpUrl().newBuilder()
            .addQueryParameter("user_code", userCode).build().toString()
    }

    suspend fun begin(): Authorization {
        val (status, body) = request("auth/device", "scope" to "openid profile")
        if (status != 200) throw IOException("Conta Squi TV indisponível (HTTP $status).")
        val code = body.value("user_code")?.takeIf { it.matches(Regex("[A-Za-z0-9-]{4,32}")) }
            ?: throw IOException("Código inválido.")
        val deviceCode = body.value("device_code")?.takeIf { it.length in 1..4096 }
            ?: throw IOException("Autorização inválida.")
        val seconds = body.value("expires_in")?.toLongOrNull()?.takeIf { it in 1..1800 }
            ?: throw IOException("Validade inválida.")
        return Authorization(code, deviceCode, System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds),
            (body.value("interval")?.toLongOrNull() ?: 5).coerceIn(1, 60))
    }

    suspend fun awaitLinkedLogin(authorization: Authorization): LoginResult? {
        var interval = authorization.interval
        while (authorization.expiresAt - System.nanoTime() > 0) {
            delay(minOf(interval * 1000, TimeUnit.NANOSECONDS.toMillis(
                authorization.expiresAt - System.nanoTime()).coerceAtLeast(1)))
            if (authorization.expiresAt - System.nanoTime() <= 0) return null
            val (status, body) = request("token",
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                "device_code" to authorization.deviceCode)
            if (status == 200) {
                val accessToken = body.value("access_token")?.takeIf { it.isNotBlank() && it.length <= 8192 }
                    ?: throw IOException("Autorização inválida.")
                return loadLinkedLogin(accessToken)
            }
            when (body.value("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token", "access_denied" -> return null
                else -> throw IOException("Falha ao confirmar a autorização (HTTP $status).")
            }
        }
        return null
    }

    suspend fun login(username: String, password: String): LoginResult {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        val request = Request.Builder().url(NATIVE_LOGIN_URL)
            .header("X-SquiTV-Select-Link", "2")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return parseLoginResult(execute(request))
    }

    private suspend fun loadLinkedLogin(accessToken: String): LoginResult {
        val request = Request.Builder().url(LINK_URL)
            .header("X-SquiTV-Select-Link", "2")
            .header("Authorization", "Bearer " + accessToken)
            .header("Cache-Control", "no-store")
            .build()
        return parseLoginResult(execute(request))
    }

    suspend fun selectLink(selection: LoginResult.Choose, slot: Int): LinkedLogin {
        require(selection.choices.any { it.slot == slot })
        val url = SELECT_URL.toHttpUrl().newBuilder().addQueryParameter("slot", slot.toString()).build()
        val request = Request.Builder().url(url)
            .header("Authorization", "SquiLink " + selection.token)
            .header("Cache-Control", "no-store")
            .build()
        val response = execute(request)
        if (response.first == 401) throw LinkSelectionExpired()
        return parseLinkedLogin(response).also {
            if (it.slot != slot) throw IOException("Vínculo IPTV inválido.")
        }
    }

    private fun parseLoginResult(response: Pair<Int, JsonObject>): LoginResult {
        val (status, body) = response
        val choices = body["choices"] as? JsonArray
        if (status == 200 && choices != null) {
            val links = choices.mapNotNull { item ->
                val entry = item as? JsonObject ?: return@mapNotNull null
                val slot = entry.value("slot")?.toIntOrNull()?.takeIf { it in 1..MAX_LINKS } ?: return@mapNotNull null
                val name = entry.value("username")?.takeIf { it.isNotBlank() && it.length <= 256 }
                    ?: return@mapNotNull null
                LinkChoice(slot, name)
            }
            val token = body.value("selectionToken")?.takeIf { it.isNotBlank() && it.length <= 1024 }
            if (links.size !in 2..MAX_LINKS || links.size != choices.size || links.map { it.slot }.toSet().size != links.size || token == null) {
                throw IOException("Lista de vínculos IPTV inválida.")
            }
            return LoginResult.Choose(links.sortedBy { it.slot }, token)
        }
        return LoginResult.Connected(parseLinkedLogin(response))
    }

    private fun parseLinkedLogin(response: Pair<Int, JsonObject>): LinkedLogin {
        val (status, body) = response
        if (status == 401) throw IOException("Usuário ou senha da Conta Squi TV inválidos.")
        if (status == 429) throw IOException("Muitas tentativas. Aguarde e tente novamente.")
        if (status == 503) throw IOException("O login da Conta Squi TV está temporariamente indisponível.")
        if (status == 404) throw IOException("Vincule sua conta IPTV no portal Squi TV antes de entrar.")
        if (status != 200) throw IOException("Não foi possível carregar o vínculo IPTV (HTTP $status).")
        val username = body.value("username")?.takeIf { it.isNotBlank() && it.length <= 256 }
        val password = body.value("password")?.takeIf { it.isNotBlank() && it.length <= 256 }
        val provider = body.value("baseUrl")?.takeIf { it == PROVIDER }
        if (username == null || password == null || provider == null) throw IOException("Vínculo IPTV inválido.")
        val slot = body.value("slot")?.toIntOrNull()?.takeIf { it in 1..MAX_LINKS } ?: 1
        val displayName = body.value("displayName")?.takeIf { it.isNotBlank() && it.length <= 255 }
        val token = body.value("syncToken")?.takeIf { it.length in 30..512 }
        val scope = body.value("syncScope")?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) }
        val favoriteClient = if (token != null && scope != null) FavoriteCloudClient(token, scope) else null
        return LinkedLogin(username, password, provider, slot, displayName, favoriteClient)
    }

    private fun JsonObject.value(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

    private suspend fun request(path: String, vararg fields: Pair<String, String>): Pair<Int, JsonObject> {
        val form = FormBody.Builder().add("client_id", CLIENT)
        fields.forEach { (key, value) -> form.add(key, value) }
        return execute(Request.Builder().url("$ISSUER/protocol/openid-connect/$path").post(form.build()).build())
    }

    internal suspend fun execute(request: Request, responseLimit: Long = 64 * 1024): Pair<Int, JsonObject> =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(IOException("Não foi possível acessar a Conta Squi TV."))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            val body = runCatching {
                                Json.parseToJsonElement(it.peekBody(responseLimit).string()) as JsonObject
                            }.getOrElse { JsonObject(emptyMap()) }
                            it.code to body
                        }
                    }
                    result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
}
