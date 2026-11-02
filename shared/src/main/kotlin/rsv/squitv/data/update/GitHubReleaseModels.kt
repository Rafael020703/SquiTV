package rsv.squitv.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseResponse(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("draft") val isDraft: Boolean = false,
    @SerialName("prerelease") val isPrerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("assets") val assets: List<GitHubAssetResponse> = emptyList()
)

@Serializable
data class GitHubAssetResponse(
    @SerialName("name") val name: String = "",
    @SerialName("size") val size: Long = 0L,
    @SerialName("browser_download_url") val downloadUrl: String = "",
    @SerialName("content_type") val contentType: String = "",
    @SerialName("digest") val digest: String? = null
)
