package rsv.squitv.core.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object Initial : Route

    @Serializable
    data object Dashboard : Route

    @Serializable
    data object Login : Route

    @Serializable
    data object Gallery : Route

    @Serializable
    data class LiveChannels(val categoryId: String? = null) : Route

    @Serializable
    data class Movies(val categoryId: String? = null, val type: String = "movie") : Route

    @Serializable
    data class VodDetail(
        val vodId: Int,
        val vodName: String,
        val icon: String? = null
    ) : Route

    @Serializable
    data class Series(val categoryId: String? = null, val type: String = "series") : Route

    @Serializable
    data class SeriesDetail(
        val seriesId: Int,
        val seriesName: String,
        val cover: String? = null
    ) : Route

    @Serializable
    data object Account : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Updates : Route

    @Serializable
    data object DnsTester : Route

    @Serializable
    data object EpgGrid : Route

    @Serializable
    data class Search(val query: String? = null) : Route

    @Serializable
    data object Sync : Route

    @Serializable
    data object Downloads : Route

    @Serializable
    data object Favorites : Route

    @Serializable
    data object History : Route

    @Serializable
    data object MultiView : Route

    @Serializable
    data object LocalLogin : Route

    @Serializable
    data object DebugConsole : Route

    @Serializable
    data class ActorDetail(val name: String) : Route

    @Serializable
    data class Player(
        val streamId: Int,
        val streamName: String,
        val streamType: String,
        val container: String? = "ts",
        val epgId: String? = null,
        val seriesId: Int? = null,
        val seasonNumber: Int? = null,
        val qualitiesJson: String? = null,
        val categoryId: String? = null,
        val streamIcon: String? = null
    ) : Route
}
