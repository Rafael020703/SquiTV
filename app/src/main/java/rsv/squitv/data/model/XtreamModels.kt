package rsv.squitv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.parcelize.Parcelize
import android.os.Parcelable

@Serializable
data class XtreamResponse(
    @SerialName("user_info") val userInfo: UserInfo? = null,
    @SerialName("server_info") val serverInfo: ServerInfo? = null
)

@Serializable
data class UserInfo(
    val username: String? = null,
    val password: String? = null,
    val message: String? = null,
    val auth: Int? = null,
    val status: String? = null,
    @SerialName("exp_date") val expDate: String? = null,
    @SerialName("is_trial") val isTrial: String? = null,
    @SerialName("active_cons") val activeCons: String? = null,
    @SerialName("max_connections") val maxConnections: String? = null,
    val revoked: String? = null,
    @SerialName("allowed_output_formats") val allowedOutputFormats: List<String>? = null
)

@Serializable
data class ServerInfo(
    val url: String? = null,
    val port: String? = null,
    @SerialName("https_port") val httpsPort: String? = null,
    @SerialName("server_protocol") val serverProtocol: String? = null,
    @SerialName("rtmp_port") val rtmpPort: String? = null,
    val timezone: String? = null,
    @SerialName("timestamp_now") val timestampNow: Long? = null,
    @SerialName("time_now") val timeNow: String? = null
)

@Parcelize
@Serializable
data class XtreamCategory(
    @SerialName("category_id") @Serializable(with = StringOrAnySerializer::class) val categoryId: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("parent_id") val parentId: Int = 0
) : Parcelable

@Parcelize
@Serializable
data class XtreamStream(
    @Serializable(with = IntOrAnySerializer::class) val num: Int? = null,
    val name: String? = null,
    @SerialName("stream_type") val streamType: String? = null,
    @SerialName("stream_id") @Serializable(with = IntOrAnySerializer::class) val streamId: Int? = null,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("epg_channel_id") @Serializable(with = StringOrAnySerializer::class) val epgChannelId: String? = null,
    val added: String? = null,
    @SerialName("category_id") @Serializable(with = StringOrAnySerializer::class) val categoryId: String? = null,
    @SerialName("custom_sid") @Serializable(with = StringOrAnySerializer::class) val customSid: String? = null,
    @SerialName("tv_archive") @Serializable(with = IntOrAnySerializer::class) val tvArchive: Int? = null,
    @SerialName("direct_source") val directSource: String? = null,
    @SerialName("tv_archive_duration") @Serializable(with = IntOrAnySerializer::class) val tvArchiveDuration: Int? = null
) : Parcelable

@Serializable
data class XtreamShortEpg(
    @SerialName("epg_listings") val epgListings: List<EpgListing>? = null
)

@Serializable
data class EpgListing(
    val id: String? = null,
    @SerialName("epg_id") val epgId: String? = null,
    val title: String? = null,
    @SerialName("lang") val lang: String? = null,
    @SerialName("start") val start: String? = null,
    @SerialName("end") val end: String? = null,
    val description: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("start_timestamp") val startTimestamp: Long? = null,
    @SerialName("stop_timestamp") val stopTimestamp: Long? = null
)

data class M3uEntry(
    val name: String,
    val url: String,
    val id: String? = null,
    val logo: String? = null,
    val group: String? = null,
    val epgId: String? = null
)
