package rsv.squitv.core.data.repository

import rsv.squitv.data.model.XtreamCredentials
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
        val ext = when (normalizedType) {
            "live" -> ".ts"
            else -> if (container.isNullOrBlank()) ".mp4" else if (container.startsWith(".")) container else ".$container"
        }
        var baseUrl = credentials.baseUrl.trim()
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) baseUrl = "http://$baseUrl"
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length - 1)
        return "$baseUrl/$action/${credentials.username}/${credentials.password}/$streamId$ext"
    }
}
