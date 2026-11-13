package rsv.squitv.core.data.repository

import rsv.squitv.data.model.XtreamCredentials
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamRepository @Inject constructor() {

    fun getStreamUrl(credentials: XtreamCredentials, streamId: Int, type: String, container: String? = "ts"): String {
        val normalizedType = type.lowercase()
        if (normalizedType == "live" && streamId > 0) return rsv.squitv.shared.xtream.LiveStreamAddress.build(
            credentials.baseUrl, credentials.username, credentials.password, streamId)
        val action = when (normalizedType) {
            "live" -> "live"
            "movie", "vod" -> "movie"
            "series" -> "series"
            else -> "live"
        }
        val cleanExt = container?.trim()?.lowercase().orEmpty().removePrefix(".")
        val ext = when (normalizedType) {
            "live" -> ".ts"
            else -> if (cleanExt.isBlank()) ".mp4" else ".$cleanExt"
        }
        var baseUrl = credentials.baseUrl.trim()
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) baseUrl = "http://$baseUrl"
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length - 1)
        val userEnc = URLEncoder.encode(credentials.username, "UTF-8")
        val passEnc = URLEncoder.encode(credentials.password, "UTF-8")
        return "$baseUrl/$action/$userEnc/$passEnc/$streamId$ext"
    }
}
