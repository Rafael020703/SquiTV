package rsv.squitv.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class XtreamCredentials(val username: String, val password: String, val baseUrl: String) {
    val providerHash: String
        get() = baseUrl.trim()
            .lowercase()
            .removeSuffix("/")
            .let { url ->
                // Simple fast hash for UI/ID purposes. 
                // We avoid using password here for privacy.
                UUID.nameUUIDFromBytes(url.toByteArray()).toString().take(8)
            }

    override fun toString(): String {
        return "XtreamCredentials(username='$username', password='[REDACTED]', baseUrl='$baseUrl')"
    }
}
