package rsv.squitv.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.nullable

// Portable Xtream DTOs and tolerant serializers originally implemented in Android.
@Serializable
data class XtreamVod(
    @Serializable(with = IntOrAnySerializer::class) val num: Int? = null,
    val name: String? = null,
    @SerialName("stream_type") val streamType: String? = null,
    @SerialName("stream_id") @Serializable(with = IntOrAnySerializer::class) val streamId: Int? = null,
    @SerialName("stream_icon") @Serializable(with = ImageTextSerializer::class) val streamIcon: String? = null,
    val added: String? = null,
    @SerialName("category_id") @Serializable(with = StringOrAnySerializer::class) val categoryId: String? = null,
    val container_extension: String? = null,
    @Serializable(with = StringOrAnySerializer::class) val rating: String? = null,
    val rating_5based: Double? = null,
    @Serializable(with = OptionalGenreTextSerializer::class) val genre: String? = null
)

/** An optional listing field must not make a previously readable VOD list fail. */
object OptionalGenreTextSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OptionalGenreText", PrimitiveKind.STRING)
    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): String? {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val value = input.decodeJsonElement()) {
            is JsonPrimitive -> value.takeIf { it.isString }?.contentOrNull
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.takeIf { token -> token.isString }?.contentOrNull }
                .joinToString(", ").takeIf { it.isNotBlank() }
            else -> null
        }
    }
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

@Serializable
data class XtreamSeries(
    @Serializable(with = IntOrAnySerializer::class) val num: Int? = null,
    val name: String? = null,
    @SerialName("series_id") @Serializable(with = IntOrAnySerializer::class) val seriesId: Int? = null,
    @Serializable(with = ImageTextSerializer::class) val cover: String? = null,
    @SerialName("plot") val plot: String? = null,
    @SerialName("cast") val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    @SerialName("last_modified") val lastModified: String? = null,
    @Serializable(with = StringOrAnySerializer::class) val rating: String? = null,
    val rating_5based: Double? = null,
    @SerialName("category_id") @Serializable(with = StringOrAnySerializer::class) val categoryId: String? = null,
    @SerialName("youtube_trailer") val youtubeTrailer: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: String? = null,
    @SerialName("backdrop_path") @Serializable(with = ImageListSerializer::class) val backdropPath: List<String>? = null,
    @SerialName("age") val age: String? = null,
    @SerialName("mpaa_rating") val mpaaRating: String? = null
)

@Serializable
data class XtreamSeason(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
    @Serializable(with = IntOrAnySerializer::class) val id: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("season_number") @Serializable(with = IntOrAnySerializer::class) val seasonNumber: Int? = null,
    @SerialName("cover") @Serializable(with = ImageTextSerializer::class) val cover: String? = null,
    @SerialName("cover_big") @Serializable(with = ImageTextSerializer::class) val coverBig: String? = null
)

@Serializable
data class XtreamSeriesInfo(
    @SerialName("episodes") @Serializable(with = EpisodesMapSerializer::class) val episodes: Map<String, List<XtreamEpisode>>? = null,
    @SerialName("info") @Serializable(with = XtreamSeriesSafeSerializer::class) val info: XtreamSeries? = null,
    @SerialName("seasons") @Serializable(with = SeasonsListSerializer::class) val seasons: List<XtreamSeason>? = null
)

object XtreamSeriesSafeSerializer : SafeObjectSerializer<XtreamSeries>(XtreamSeries.serializer())
object EpisodesMapSerializer : SafeMapSerializer<String, List<XtreamEpisode>>(String.serializer(), kotlinx.serialization.builtins.ListSerializer(XtreamEpisode.serializer()))
object SeasonsListSerializer : SafeListSerializer<XtreamSeason>(XtreamSeason.serializer())

open class SafeObjectSerializer<T>(private val dataSerializer: KSerializer<T>) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = dataSerializer.descriptor

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): T? {
        val input = decoder as? JsonDecoder ?: return null
        val element = input.decodeJsonElement()
        if (element is JsonArray && element.isEmpty()) return null
        return input.json.decodeFromJsonElement(dataSerializer, element)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: T?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(dataSerializer, value)
    }
}

open class SafeMapSerializer<K, V>(kSerializer: KSerializer<K>, vSerializer: KSerializer<V>) : KSerializer<Map<K, V>?> {
    private val mapSerializer = kotlinx.serialization.builtins.MapSerializer(kSerializer, vSerializer)
    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Map<K, V>? {
        val input = decoder as? JsonDecoder ?: return null
        val element = input.decodeJsonElement()
        if (element is JsonArray && element.isEmpty()) return null
        return input.json.decodeFromJsonElement(mapSerializer, element)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Map<K, V>?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(mapSerializer, value)
    }
}

open class SafeListSerializer<T>(dataSerializer: KSerializer<T>) : KSerializer<List<T>?> {
    private val listSerializer = kotlinx.serialization.builtins.ListSerializer(dataSerializer)
    override val descriptor: SerialDescriptor = listSerializer.descriptor

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): List<T>? {
        val input = decoder as? JsonDecoder ?: return null
        return when (val element = input.decodeJsonElement()) {
            is JsonArray -> input.json.decodeFromJsonElement(listSerializer, element)
            is JsonObject -> {
                if (element.isEmpty()) null
                else {
                    // Try to convert map values to list
                    val array = JsonArray(element.values.toList())
                    input.json.decodeFromJsonElement(listSerializer, array)
                }
            }
            else -> null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: List<T>?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(listSerializer, value)
    }
}

object StringOrAnySerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringOrAny", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): String? {
        val input = decoder as? JsonDecoder ?: return try { decoder.decodeString() } catch (e: Exception) { null }
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> element.contentOrNull
            else -> element.toString()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value)
        }
    }
}

object IntOrAnySerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IntOrAny", PrimitiveKind.INT)

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Int? {
        val input = decoder as? JsonDecoder ?: return try { decoder.decodeInt() } catch (e: Exception) { null }
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> element.intOrNull ?: element.contentOrNull?.toIntOrNull()
            else -> null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeInt(value)
        }
    }
}

@Serializable
data class XtreamEpisode(
    @Serializable(with = StringOrAnySerializer::class) val id: String? = null,
    @SerialName("episode_num") @Serializable(with = StringOrAnySerializer::class) val episodeNum: String? = null,
    val title: String? = null,
    val container_extension: String? = null,
    @SerialName("info") @Serializable(with = EpisodeInfoSafeSerializer::class) val info: EpisodeInfo? = null,
    @SerialName("custom_sid") val customSid: String? = null,
    @SerialName("added") val added: String? = null,
    @SerialName("season") @Serializable(with = IntOrAnySerializer::class) val season: Int? = null,
    @SerialName("direct_source") val directSource: String? = null
)

@Serializable
data class XtreamVodInfo(
    val info: VodDetails? = null,
    @SerialName("movie_data") val movieData: VodMovieData? = null
)

@Serializable
data class VodDetails(
    val name: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("last_modified") val lastModified: String? = null,
    @Serializable(with = StringOrAnySerializer::class) val rating: String? = null,
    @SerialName("rating_5based") val rating5Based: Double? = null,
    @SerialName("backdrop_path") @Serializable(with = ImageListSerializer::class) val backdropPath: List<String>? = null,
    @SerialName("youtube_trailer") val youtubeTrailer: String? = null,
    @SerialName("movie_image") @Serializable(with = ImageTextSerializer::class) val movieImage: String? = null,
    val duration: String? = null,
    @SerialName("age") val age: String? = null,
    @SerialName("mpaa_rating") val mpaaRating: String? = null
)

@Serializable
data class VodMovieData(
    @SerialName("stream_id") val streamId: Int? = null,
    val name: String? = null,
    val container_extension: String? = null
)

object EpisodeInfoSafeSerializer : SafeObjectSerializer<EpisodeInfo>(EpisodeInfo.serializer())

@Serializable
data class EpisodeInfo(
    val plot: String? = null,
    val duration: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @Serializable(with = StringOrAnySerializer::class) val rating: String? = null,
    @SerialName("movie_image") @Serializable(with = ImageTextSerializer::class) val movieImage: String? = null
)


/** Optional artwork cannot discard the surrounding details/episodes for a provider's JSON shape. */
object ImageListSerializer : KSerializer<List<String>?> {
    private val delegate = kotlinx.serialization.builtins.ListSerializer(String.serializer()).nullable
    override val descriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): List<String>? {
        val input = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(delegate)
        return imageStrings(input.decodeJsonElement()).take(32).takeIf { it.isNotEmpty() }
    }
    override fun serialize(encoder: Encoder, value: List<String>?) = encoder.encodeSerializableValue(delegate, value)
}

object ImageTextSerializer : KSerializer<String?> {
    private val delegate = String.serializer().nullable
    override val descriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): String? {
        val input = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(delegate)
        return imageStrings(input.decodeJsonElement()).firstOrNull()
    }
    override fun serialize(encoder: Encoder, value: String?) = encoder.encodeSerializableValue(delegate, value)
}

private fun imageStrings(value: JsonElement): List<String> = when (value) {
    is JsonPrimitive -> value.takeIf { it.isString }?.contentOrNull
        ?.let(rsv.squitv.shared.catalog.ContentImages::clean)?.let(::listOf).orEmpty()
    is JsonArray -> value.take(32).mapNotNull { (it as? JsonPrimitive)?.takeIf { token -> token.isString }?.contentOrNull }
        .mapNotNull(rsv.squitv.shared.catalog.ContentImages::clean).distinct()
    else -> emptyList()
}
