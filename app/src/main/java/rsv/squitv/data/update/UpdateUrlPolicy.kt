package rsv.squitv.data.update

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Tests use their own HTTPS origin; the production origin is always github.com:443. */
class UpdateUrlPolicy(private val host: String = "github.com", private val port: Int = 443) {
    fun assetUrl(value: String, repository: String, tag: String, name: String): HttpUrl {
        val url = requireNotNull(value.toHttpUrlOrNull()) { "Invalid update URL" }
        require(url.isHttps && url.host == host && url.port == port &&
            url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null &&
            url.pathSegments == listOf(GitHubUpdateConfig.OWNER, repository, "releases", "download", tag, name)) {
            "Update asset does not belong to the expected HTTPS release"
        }
        return url
    }
}
