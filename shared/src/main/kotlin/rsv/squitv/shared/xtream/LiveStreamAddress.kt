package rsv.squitv.shared.xtream

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object LiveStreamAddress {
    fun build(baseUrl: String, username: String, password: String, streamId: Int, format: String = "ts"): String {
        require(streamId > 0)
        require(format in setOf("ts", "m3u8"))
        // The charset-name overload also exists on the oldest supported Android versions.
        fun segment(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return ServerAddress.normalize(baseUrl) + "live/" + segment(username) + "/" + segment(password) + "/$streamId.$format"
    }
}
