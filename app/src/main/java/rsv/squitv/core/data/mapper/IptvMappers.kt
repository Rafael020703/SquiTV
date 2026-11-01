package rsv.squitv.core.data.mapper

import rsv.squitv.data.model.XtreamStream
import rsv.squitv.data.model.XtreamVod
import rsv.squitv.data.model.XtreamSeries
import rsv.squitv.data.local.entities.FavoriteEntity
import rsv.squitv.data.local.entities.IptvStreamEntity
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem

fun XtreamStream.toIptvItem() = IptvItem(
    id = streamId.toString(),
    name = name ?: "",
    icon = streamIcon,
    type = ContentType.LIVE,
    epgId = epgChannelId,
    categoryId = categoryId,
    added = added
)

fun IptvStreamEntity.toIptvItem() = IptvItem(
    id = id.toString(),
    name = name,
    icon = logo,
    type = ContentType.fromString(streamType),
    epgId = if (streamType.equals("LIVE", true)) url else null,
    containerExtension = containerExtension,
    rating = rating,
    releaseDate = releaseDate,
    categoryId = categoryId,
    backdropUrl = backdrop,
    plot = plot,
    genre = genre,
    duration = duration,
    cast = cast,
    director = director,
    added = added
)

fun XtreamVod.toIptvItem() = IptvItem(
    id = streamId.toString(),
    name = name ?: "",
    icon = streamIcon,
    type = ContentType.MOVIE,
    containerExtension = container_extension,
    rating = rating,
    added = added,
    categoryId = categoryId
)

fun XtreamSeries.toIptvItem() = IptvItem(
    id = seriesId.toString(),
    name = name ?: "",
    icon = cover,
    type = ContentType.SERIES,
    rating = rating,
    releaseDate = releaseDate,
    added = lastModified,
    categoryId = categoryId,
    plot = plot,
    genre = genre,
    cast = cast,
    director = director,
    duration = episodeRunTime
)

fun FavoriteEntity.favoriteToIptvItem() = IptvItem(
    id = streamId.toString(),
    name = name,
    icon = logo,
    type = ContentType.fromString(streamType),
    epgId = url,
    containerExtension = containerExtension,
    categoryId = categoryId,
    rating = rating,
    releaseDate = releaseDate
)
