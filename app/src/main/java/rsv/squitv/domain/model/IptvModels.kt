package rsv.squitv.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class IptvItem(
    val id: String,
    val name: String,
    val icon: String?,
    val type: ContentType,
    val epgId: String? = null,
    val containerExtension: String? = "ts",
    val previewUrl: String? = null,
    val isFavorite: Boolean = false,
    val rating: String? = null,
    val backdropUrl: String? = null,
    val releaseDate: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val added: String? = null,
    val versions: List<ChannelVersion> = emptyList(),
    val groupKey: String? = null,
    val categoryId: String? = null,
    val progress: Float? = null
) {
    val stableKey: String get() = groupKey ?: "${type.name}:$id"
    fun containsStream(streamId: String?) = id == streamId || versions.any { it.streamId.toString() == streamId }
    fun matchesStreamKey(key: String) = key.startsWith("${type.name}:") && containsStream(key.substringAfter(':'))
}

@Immutable
data class DashboardRow(
    val title: String,
    val items: List<IptvItem>,
    val catId: String? = null
)

enum class SessionStatus {
    UNKNOWN,
    VALID,
    INVALID,
    EXPIRED,
    OFFLINE
}
