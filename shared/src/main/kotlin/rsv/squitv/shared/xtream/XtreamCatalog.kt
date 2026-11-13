package rsv.squitv.shared.xtream

import java.net.URI
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ServerCredentials(val baseUrl: String, val username: String, val password: String) {
    override fun toString() = "ServerCredentials([redacted])"
}

data class ServerAccount(
    val username: String, val status: String, val expiresAt: Long?,
    val activeConnections: Int?, val maxConnections: Int?, val timezone: String?,
    val serverTime: String?, val outputFormats: List<String>
)
data class LiveCategory(val id: String, val name: String)
data class LiveChannel(val id: Int, val name: String, val categoryId: String?, val iconUrl: String?, val epgId: String?,
    val versions: List<LiveChannel> = emptyList(), val groupKey: String? = null, val added: String? = null) {
    val stableKey: String get() = groupKey ?: "LIVE:$id"
    fun containsStream(streamId: Int) = id == streamId || versions.any { it.id == streamId }
}
class ServerAccountException(message: String) : Exception(message)

object ServerAddress {
    fun normalize(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty() && value.length <= 2048) { "Informe um endereço de servidor válido." }
        val uri = try { URI(if ("://" in value) value else "http://$value") }
            catch (_: Exception) { throw IllegalArgumentException("Endereço de servidor inválido.") }
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.port in -1..65535 && uri.port != 0) { "Use apenas o endereço HTTP ou HTTPS do servidor, sem credenciais na URL." }
        val path = uri.rawPath.orEmpty().removeSuffix("/player_api.php").trimEnd('/') + "/"
        return URI(uri.scheme.lowercase(), null, uri.host.lowercase(), uri.port, null, null, null)
            .toASCIIString().trimEnd('/') + path
    }
}

/** Handles the string/number scalar variations used by Xtream providers. */
object XtreamCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }
    private fun JsonObject.text(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun obj(body: String) = json.parseToJsonElement(body) as? JsonObject
        ?: throw IllegalArgumentException("Resposta inválida do servidor.")
    private fun array(body: String) = json.parseToJsonElement(body) as? JsonArray
        ?: throw IllegalArgumentException("Lista inválida do servidor.")

    fun account(body: String, fallbackUsername: String, nowSeconds: Long): ServerAccount {
        val root = obj(body)
        val user = root["user_info"] as? JsonObject ?: throw IllegalArgumentException("Resposta de login inválida.")
        val status = user.text("status") ?: "Active"
        val expiry = user.text("exp_date")?.toLongOrNull()?.takeIf { it in 1L..253402300799L }
        validateProviderLogin(user.text("auth"), status, expiry, nowSeconds)
        val server = root["server_info"] as? JsonObject
        return ServerAccount(user.text("username") ?: fallbackUsername, status, expiry,
            user.text("active_cons")?.toIntOrNull(), user.text("max_connections")?.toIntOrNull(),
            server?.text("timezone"), server?.text("time_now"),
            (user["allowed_output_formats"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty())
    }

    fun validateProviderLogin(auth: String?, status: String?, expiry: Long?, nowSeconds: Long) {
        if (auth == null) throw IllegalArgumentException("Resposta de login inválida.")
        if (status.equals("Expired", ignoreCase = true) || expiry != null && expiry in 1..nowSeconds)
            throw ServerAccountException("A conta IPTV está expirada.")
        if (auth != "1") throw ServerAccountException("Usuário ou senha IPTV recusados pelo servidor.")
        if (!status.isNullOrBlank() && !status.equals("Active", ignoreCase = true))
            throw ServerAccountException("A conta IPTV não está ativa no servidor.")
    }

    fun categories(body: String): List<LiveCategory> = array(body).mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val id = item.text("category_id") ?: return@mapNotNull null
        LiveCategory(id, item.text("category_name") ?: "Categoria $id")
    }.distinctBy { it.id }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun channels(body: String): List<LiveChannel> = array(body).mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val id = item.text("stream_id")?.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        LiveChannel(id, item.text("name") ?: "Canal $id", item.text("category_id"),
            item.text("stream_icon"), item.text("epg_channel_id"), added = item.text("added"))
    }.distinctBy { it.id }

    fun epg(body: String): List<LiveProgramme> {
        val listings = obj(body)["epg_listings"] as? JsonArray ?: return emptyList()
        fun decode(value: String?): String? = value?.let {
            if (it.length % 4 != 0 || !it.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) return@let it
            runCatching {
                val text = Base64.getDecoder().decode(it).toString(Charsets.UTF_8)
                if (text.any { char -> char == '\uFFFD' || (char.isISOControl() && char != '\n' && char != '\t') }) it else text
            }.getOrDefault(it)
        }
        return listings.mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            val title = decode(item.text("title")) ?: return@mapNotNull null
            LiveProgramme(title, decode(item.text("description")), item.text("start_timestamp")?.toLongOrNull(),
                item.text("stop_timestamp")?.toLongOrNull())
        }.sortedBy { it.start ?: Long.MAX_VALUE }
    }
}
