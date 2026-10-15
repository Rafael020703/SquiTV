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
    val qualities: Map<String, Int> = emptyMap(),
    val categoryId: String? = null,
    val progress: Float? = null
)

@Immutable
data class DashboardRow(
    val title: String,
    val items: List<IptvItem>,
    val isLocked: Boolean = false,
    val catId: String? = null
)

enum class SessionStatus {
    UNKNOWN,
    VALID,
    INVALID,
    EXPIRED,
    OFFLINE
}
