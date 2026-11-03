package rsv.squitv.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import rsv.squitv.core.data.mapper.toIptvItem
import rsv.squitv.data.api.XtreamService
import rsv.squitv.data.local.dao.IptvDao
import rsv.squitv.data.local.entities.*
import rsv.squitv.data.model.*
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val xtreamService: XtreamService,
    private val iptvDao: IptvDao,
    private val epgRepository: EpgRepository
) {

    suspend fun getLiveCategories(credentials: XtreamCredentials, forceRefresh: Boolean = false): List<IptvCategoryEntity> {
        if (!forceRefresh) {
            val cached = iptvDao.getCategoriesByType("LIVE")
            if (cached.isNotEmpty()) return cached
        }
        val remote = xtreamService.getLiveCategories(credentials.username, credentials.password)
        val entities = remote.map { IptvCategoryEntity(it.categoryId ?: "", it.categoryName ?: "", "LIVE") }
        iptvDao.upsertCategories(entities)
        return entities
    }

    suspend fun getLiveStreams(credentials: XtreamCredentials, categoryId: String? = null, forceRefresh: Boolean = false): List<XtreamStream> {
        val type = "LIVE"
        if (!forceRefresh) {
            val cached = if (!categoryId.isNullOrEmpty()) iptvDao.getStreamsByCategory(categoryId, type)
                         else iptvDao.getStreamsByType(type)
            if (cached.isNotEmpty()) {
                return cached.map {
                    XtreamStream(
                        streamId = it.id,
                        name = it.name,
                        categoryId = it.categoryId,
                        streamIcon = it.logo,
                        streamType = it.streamType,
                        epgChannelId = it.url,
                        added = it.added
                    )
                }
            }
        }
        val remote = xtreamService.getLiveStreams(credentials.username, credentials.password, categoryId = categoryId?.takeIf { it.isNotEmpty() })
        if (remote.isNotEmpty()) {
            val entities = remote.map {
                IptvStreamEntity(
                    id = it.streamId ?: 0,
                    name = it.name ?: "",
                    categoryId = it.categoryId ?: "",
                    streamType = type,
                    url = it.epgChannelId,
                    logo = it.streamIcon,
                    added = it.added
                )
            }
            entities.chunked(500).forEach { batch -> iptvDao.upsertStreams(batch) }
        }
        return remote
    }

    suspend fun getVodCategories(credentials: XtreamCredentials, forceRefresh: Boolean = false): List<IptvCategoryEntity> {
        if (!forceRefresh) {
            val cached = iptvDao.getCategoriesByType("VOD")
            if (cached.isNotEmpty()) return cached
        }
        val remote = xtreamService.getVodCategories(credentials.username, credentials.password)
        val entities = remote.map { IptvCategoryEntity(it.categoryId ?: "", it.categoryName ?: "", "VOD") }
        iptvDao.upsertCategories(entities)
        return entities
    }

    suspend fun getVodStreams(credentials: XtreamCredentials, categoryId: String? = null, forceRefresh: Boolean = false): List<XtreamVod> {
        val type = "VOD"
        if (!forceRefresh) {
            val cached = if (!categoryId.isNullOrEmpty()) iptvDao.getStreamsByCategory(categoryId, type)
                         else iptvDao.getStreamsByType(type)
            if (cached.isNotEmpty()) {
                return cached.map {
                    XtreamVod(
                        streamId = it.id,
                        name = it.name,
                        categoryId = it.categoryId,
                        streamIcon = it.logo,
                        streamType = type,
                        container_extension = it.containerExtension,
                        rating = it.rating,
                        added = it.added
                    )
                }
            }
        }
        val remote = xtreamService.getVodStreams(credentials.username, credentials.password, categoryId = categoryId?.takeIf { it.isNotEmpty() })
        if (remote.isNotEmpty()) {
            val entities = remote.map {
                IptvStreamEntity(
                    id = it.streamId ?: 0,
                    name = it.name ?: "",
                    categoryId = it.categoryId ?: "",
                    streamType = type,
                    url = null,
                    logo = it.streamIcon,
                    containerExtension = it.container_extension,
                    rating = it.rating,
                    added = it.added
                )
            }
            entities.chunked(500).forEach { batch -> iptvDao.upsertStreams(batch) }
        }
        return remote
    }

    suspend fun getSeriesCategories(credentials: XtreamCredentials, forceRefresh: Boolean = false): List<IptvCategoryEntity> {
        if (!forceRefresh) {
            val cached = iptvDao.getCategoriesByType("SERIES")
            if (cached.isNotEmpty()) return cached
        }
        val remote = xtreamService.getSeriesCategories(credentials.username, credentials.password)
        val entities = remote.map { IptvCategoryEntity(it.categoryId ?: "", it.categoryName ?: "", "SERIES") }
        iptvDao.upsertCategories(entities)
        return entities
    }

    suspend fun getSeries(credentials: XtreamCredentials, categoryId: String? = null, forceRefresh: Boolean = false): List<XtreamSeries> {
        val type = "SERIES"
        if (!forceRefresh) {
            val cached = if (!categoryId.isNullOrEmpty()) iptvDao.getStreamsByCategory(categoryId, type)
                         else iptvDao.getStreamsByType(type)
            if (cached.isNotEmpty()) {
                return cached.map {
                    XtreamSeries(
                        seriesId = it.id,
                        name = it.name,
                        categoryId = it.categoryId,
                        cover = it.logo,
                        rating = it.rating,
                        lastModified = it.added,
                        releaseDate = it.releaseDate,
                        genre = it.genre
                    )
                }
            }
        }
        val remote = xtreamService.getSeries(credentials.username, credentials.password, categoryId = categoryId?.takeIf { it.isNotEmpty() })
        if (remote.isNotEmpty()) {
            remote.chunked(500).forEach { chunk ->
                iptvDao.upsertStreams(chunk.map {
                    IptvStreamEntity(
                        id = it.seriesId ?: 0,
                        name = it.name ?: "",
                        categoryId = it.categoryId ?: "",
                        streamType = type,
                        url = null,
                        logo = it.cover,
                        rating = it.rating,
                        added = it.lastModified,
                        releaseDate = it.releaseDate,
                        genre = it.genre
                    )
                })
            }
        }
        return remote
    }

    /** Refresh already loaded UI lists without triggering another download for an empty type. */
    suspend fun getCachedLiveStreams(): List<XtreamStream> = iptvDao.getStreamsByType("LIVE").map {
        XtreamStream(streamId = it.id, name = it.name, categoryId = it.categoryId,
            streamIcon = it.logo, streamType = it.streamType, epgChannelId = it.url, added = it.added)
    }

    /** Both requests must succeed before any part of this type becomes visible in Room. */
    suspend fun syncCatalogType(
        credentials: XtreamCredentials,
        type: String,
        beforePublish: suspend () -> Unit = {}
    ): Int {
        val (remoteCategories, streams) = when (type) {
            "LIVE" -> xtreamService.getLiveCategories(credentials.username, credentials.password) to
                xtreamService.getLiveStreams(credentials.username, credentials.password).map {
                    IptvStreamEntity(it.streamId ?: 0, it.name.orEmpty(), it.categoryId.orEmpty(), type,
                        it.epgChannelId, it.streamIcon, added = it.added)
                }
            "VOD" -> xtreamService.getVodCategories(credentials.username, credentials.password) to
                xtreamService.getVodStreams(credentials.username, credentials.password).map {
                    IptvStreamEntity(it.streamId ?: 0, it.name.orEmpty(), it.categoryId.orEmpty(), type,
                        null, it.streamIcon, containerExtension = it.container_extension,
                        rating = it.rating, added = it.added)
                }
            "SERIES" -> xtreamService.getSeriesCategories(credentials.username, credentials.password) to
                xtreamService.getSeries(credentials.username, credentials.password).map {
                    IptvStreamEntity(it.seriesId ?: 0, it.name.orEmpty(), it.categoryId.orEmpty(), type,
                        null, it.cover, rating = it.rating, added = it.lastModified,
                        releaseDate = it.releaseDate, genre = it.genre)
                }
            else -> error("Unsupported catalog type: $type")
        }
        val categories = remoteCategories.map {
            IptvCategoryEntity(it.categoryId.orEmpty(), it.categoryName.orEmpty(), type)
        }
        // Typed Retrofit responses distinguish an authenticated empty list from malformed JSON.
        // Never silently drop invalid records and then use the partial list to delete local data.
        require(categories.all { it.id.isNotBlank() && it.name.isNotBlank() }) { "Categorias inválidas na resposta do servidor" }
        require(streams.all { it.id > 0 && it.name.isNotBlank() }) { "Itens inválidos na resposta do servidor" }
        require(categories.map { it.id }.distinct().size == categories.size) { "Categorias duplicadas na resposta do servidor" }
        require(streams.map { it.id }.distinct().size == streams.size) { "Itens duplicados na resposta do servidor" }
        beforePublish()
        iptvDao.publishCatalogSnapshot(type, categories, streams)
        return streams.size
    }

    suspend fun clearCatalogData() = withContext(Dispatchers.IO) {
        iptvDao.deleteAllCategories()
        iptvDao.deleteAllStreams()
        iptvDao.deleteAllSeasons()
        iptvDao.deleteAllEpisodes()
        epgRepository.clearAllEpg()
        Timber.i("Catalog data cleared")
    }

    suspend fun refreshData(credentials: XtreamCredentials) = withContext(Dispatchers.IO) {
        val liveCats = async { getLiveCategories(credentials, forceRefresh = true) }
        val liveStreams = async { getLiveStreams(credentials, forceRefresh = true) }
        val vodCats = async { getVodCategories(credentials, forceRefresh = true) }
        val vodStreams = async { getVodStreams(credentials, forceRefresh = true) }
        val seriesCats = async { getSeriesCategories(credentials, forceRefresh = true) }
        val series = async { getSeries(credentials, forceRefresh = true) }
        awaitAll(liveCats, liveStreams, vodCats, vodStreams, seriesCats, series)
    }

    suspend fun getVodInfo(credentials: XtreamCredentials, vodId: Int): XtreamVodInfo {
        val info = xtreamService.getVodInfo(credentials.username, credentials.password, vodId = vodId)
        val details = info.info
        if (details != null) {
            withContext(Dispatchers.IO) {
                try {
                    iptvDao.updateDetailedStreamMetadata(
                        id = vodId,
                        type = "VOD",
                        genre = details.genre,
                        cast = details.cast,
                        director = details.director,
                        plot = details.plot,
                        duration = details.duration,
                        backdrop = details.backdropPath?.firstOrNull(),
                        trailer = details.youtubeTrailer
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error updating VOD metadata in Room for ID $vodId")
                }
            }
        }
        return info
    }

    suspend fun getSeriesMetadata(credentials: XtreamCredentials, seriesId: Int): XtreamSeries? {
        return try {
            val response = xtreamService.getSeriesEpisodes(credentials.username, credentials.password, seriesId = seriesId)
            val info = response.info
            if (info != null) {
                withContext(Dispatchers.IO) {
                    try {
                        iptvDao.updateDetailedStreamMetadata(
                            id = seriesId,
                            type = "SERIES",
                            genre = info.genre,
                            cast = info.cast,
                            director = info.director,
                            plot = info.plot,
                            duration = null,
                            backdrop = info.backdropPath?.firstOrNull(),
                            trailer = info.youtubeTrailer
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Timber.e(e, "Error updating SERIES metadata in Room for ID $seriesId")
                    }
                }
            }
            info
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    fun getStreamsByCategoryPaged(categoryId: String, type: String): Flow<PagingData<IptvItem>> {
        return Pager(
            config = PagingConfig(pageSize = 40, enablePlaceholders = false),
            pagingSourceFactory = { iptvDao.getStreamsByCategoryPaged(categoryId, type.uppercase()) }
        ).flow.map { pagingData -> pagingData.map { it.toIptvItem() } }
    }

    fun getStreamsByTypePaged(type: String): Flow<PagingData<IptvItem>> {
        return Pager(
            config = PagingConfig(pageSize = 40, enablePlaceholders = false),
            pagingSourceFactory = { iptvDao.getStreamsByTypePaged(type.uppercase()) }
        ).flow.map { pagingData -> pagingData.map { it.toIptvItem() } }
    }

    fun searchStreamsPaged(query: String, type: String): Flow<PagingData<IptvItem>> {
        return Pager(
            config = PagingConfig(pageSize = 40, enablePlaceholders = false),
            pagingSourceFactory = { 
                if (query.length >= 2) iptvDao.searchStreamsFtsPaged(query, type.uppercase())
                else iptvDao.getStreamsByTypePaged(type.uppercase())
            }
        ).flow.map { pagingData -> pagingData.map { it.toIptvItem() } }
    }

    suspend fun searchStreams(query: String, type: String): List<IptvStreamEntity> =
        if (type == "ALL") iptvDao.searchAllStreams(query)
        else iptvDao.searchStreamsFts(query, type)
    
    suspend fun hasLocalContent(): Boolean = iptvDao.hasAnyContent()

    fun getStreamsCountFlow(): Flow<Int> = iptvDao.getStreamsCountFlow()

    suspend fun getStreamsByIds(ids: List<Int>): List<IptvStreamEntity> = iptvDao.getStreamsByIds(ids)

    suspend fun getStreamsByActor(actor: String): List<IptvStreamEntity> = iptvDao.getStreamsByActor(actor)

    suspend fun updateStreamMetadata(id: Int, type: String, cast: String?, director: String?) {
        iptvDao.updateStreamMetadata(id, type, cast, director)
    }

    suspend fun updateDetailedStreamMetadata(id: Int, type: String, genre: String? = null, cast: String? = null, director: String? = null, plot: String? = null, duration: String? = null, backdrop: String? = null, trailer: String? = null) {
        iptvDao.updateDetailedStreamMetadata(id, type, genre, cast, director, plot, duration, backdrop, trailer)
    }

    suspend fun updateCategoryPinned(id: String, type: String, pinned: Boolean) = iptvDao.updateCategoryPinned(id, type, pinned)
    suspend fun getStreamCountByCategory(categoryId: String, type: String): Int = iptvDao.getStreamCountByCategory(categoryId, type)

    suspend fun getCategoryCounts(type: String): Map<String, Int> {
        return iptvDao.getCategoryCounts(type.uppercase()).associate { it.categoryId to it.count }
    }

    suspend fun searchEpisodes(query: String): List<EpisodeEntity> = iptvDao.searchEpisodes(query)

    suspend fun getStats(): Triple<Int, Int, Int> = Triple(iptvDao.getLiveCount(), iptvDao.getMovieCount(), iptvDao.getSeriesCount())
    
    suspend fun getNewItemsSince(timestamp: Long): Pair<List<String>, List<String>> {
        val ts = (timestamp / 1000).toString()
        val movies = iptvDao.getNewMovies(ts).map { it.name }
        val series = iptvDao.getNewSeries(ts).map { it.name }
        return Pair(movies, series)
    }

    companion object {
        const val FOURTEEN_DAYS_MS = 14 * 24 * 60 * 60 * 1000L
    }

    fun parseAddedTimestamp(added: String?): Long {
        if (added.isNullOrBlank()) return 0L
        added.trim().toLongOrNull()?.let {
            return if (it < 10000000000L) it * 1000L else it
        }
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdf.parse(added)?.time?.let { return it }
        } catch (_: Exception) {}
        try {
            val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdfDate.parse(added)?.time?.let { return it }
        } catch (_: Exception) {}
        return 0L
    }

    fun sortAndFilterRecentStreams(
        streams: List<IptvStreamEntity>,
        limit: Int,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): List<IptvItem> {
        val minTimestamp = currentTimeMillis - FOURTEEN_DAYS_MS
        val maxTimestamp = currentTimeMillis + 300_000L // 5 min clock skew tolerance

        return streams
            .mapNotNull { stream ->
                val timestamp = parseAddedTimestamp(stream.added)
                if (timestamp in minTimestamp..maxTimestamp) {
                    stream to timestamp
                } else {
                    null
                }
            }
            .sortedWith(
                compareByDescending<Pair<IptvStreamEntity, Long>> { it.second }
                    .thenByDescending { it.first.id }
            )
            .take(limit)
            .map { it.first.toIptvItem() }
    }

    suspend fun getRecentMovies(limit: Int = 100, currentTimeMillis: Long = System.currentTimeMillis()): List<IptvItem> {
        val candidates = iptvDao.getRecentStreamsCandidatesByType("VOD", (limit * 5).coerceAtLeast(300))
        val streams = if (candidates.isNotEmpty()) candidates else iptvDao.getStreamsByType("VOD")
        return sortAndFilterRecentStreams(streams, limit, currentTimeMillis)
    }

    suspend fun getRecentSeries(limit: Int = 100, currentTimeMillis: Long = System.currentTimeMillis()): List<IptvItem> {
        val candidates = iptvDao.getRecentStreamsCandidatesByType("SERIES", (limit * 5).coerceAtLeast(300))
        val streams = if (candidates.isNotEmpty()) candidates else iptvDao.getStreamsByType("SERIES")
        return sortAndFilterRecentStreams(streams, limit, currentTimeMillis)
    }

    suspend fun getRecentLiveStreams(limit: Int = 100, currentTimeMillis: Long = System.currentTimeMillis()): List<IptvItem> {
        val candidates = iptvDao.getRecentStreamsCandidatesByType("LIVE", (limit * 5).coerceAtLeast(300))
        val streams = if (candidates.isNotEmpty()) candidates else iptvDao.getStreamsByType("LIVE")
        return sortAndFilterRecentStreams(streams, limit, currentTimeMillis)
    }

    suspend fun getRecentStreams(limit: Int = 20, currentTimeMillis: Long = System.currentTimeMillis()): List<IptvItem> {
        val candidates = iptvDao.getRecentStreamsCandidates((limit * 5).coerceAtLeast(300))
        val streams = if (candidates.isNotEmpty()) candidates else (iptvDao.getStreamsByType("VOD") + iptvDao.getStreamsByType("SERIES") + iptvDao.getStreamsByType("LIVE"))
        return sortAndFilterRecentStreams(streams, limit, currentTimeMillis)
    }
    suspend fun getTopRatedStreams(minRating: String = "8.0", limit: Int = 20): List<IptvItem> = iptvDao.getTopRatedStreams(minRating, limit).map { it.toIptvItem() }

    suspend fun getRandomGenreRow(): Pair<String, List<IptvItem>>? {
        val genres = iptvDao.getAvailableGenres()
        if (genres.isEmpty()) return null
        val genre = genres.random()
        val items = iptvDao.getStreamsByGenre(genre, 20).map { it.toIptvItem() }
        return if (items.isNotEmpty()) "Séries de $genre" to items else null
    }

    suspend fun getSeriesInfo(credentials: XtreamCredentials, seriesId: Int): Pair<List<SeasonEntity>, Map<Int, List<EpisodeEntity>>> {
        val cachedSeasons = iptvDao.getSeasons(seriesId)
        if (cachedSeasons.isNotEmpty()) {
            val episodesMap = mutableMapOf<Int, List<EpisodeEntity>>()
            cachedSeasons.forEach { season ->
                episodesMap[season.seasonNumber] = iptvDao.getEpisodes(seriesId, season.seasonNumber)
            }
            return Pair(cachedSeasons, episodesMap)
        }
        val response = try {
            xtreamService.getSeriesEpisodes(credentials.username, credentials.password, seriesId = seriesId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Pair(emptyList(), emptyMap())
        }

        val seasons = mutableListOf<SeasonEntity>()
        val episodesMap = mutableMapOf<Int, List<EpisodeEntity>>()
        val rawSeasons = response.seasons.orEmpty()
        val rawEpisodes = response.episodes ?: emptyMap()

        val seasonKeys = if (rawSeasons.isEmpty()) {
            rawEpisodes.keys.mapNotNull { it.toIntOrNull() }.sorted()
        } else {
            rawSeasons.mapNotNull { it.seasonNumber }
        }

        seasonKeys.forEach { sn ->
            val xtreamSeason = rawSeasons.find { it.seasonNumber == sn }
            seasons.add(SeasonEntity(seriesId, sn, xtreamSeason?.name ?: "Season $sn", xtreamSeason?.cover ?: response.info?.cover))
            val eps = rawEpisodes[sn.toString()] ?: rawEpisodes["Season $sn"] ?: rawEpisodes[xtreamSeason?.name.orEmpty()] ?: emptyList()
            episodesMap[sn] = eps.map {
                EpisodeEntity(
                    seriesId = seriesId,
                    seasonNumber = sn,
                    episodeId = it.id?.toIntOrNull() ?: it.episodeNum?.toIntOrNull() ?: 0,
                    episodeNum = it.episodeNum?.toIntOrNull() ?: 0,
                    title = it.title ?: "Episode ${it.episodeNum ?: 0}",
                    streamId = it.id?.toIntOrNull() ?: 0,
                    containerExtension = it.container_extension,
                    image = it.info?.movieImage,
                    plot = it.info?.plot,
                    duration = it.info?.duration,
                    rating = it.info?.rating
                )
            }
        }
        if (seasons.isNotEmpty()) {
            iptvDao.insertSeasons(seasons)
            episodesMap.values.forEach { iptvDao.insertEpisodes(it) }
        }
        return Pair(seasons.sortedBy { it.seasonNumber }, episodesMap)
    }

    fun groupLiveStreams(streams: List<XtreamStream>, scope: String = "", preferred: rsv.squitv.domain.model.ChannelQuality = rsv.squitv.domain.model.ChannelQuality.AUTO): List<IptvItem> =
        rsv.squitv.domain.model.LiveChannelGrouping.group(streams.map { it.toIptvItem() }, scope, preferred)

    /** Local content and metadata updates drive navigation; no per-category network requests. */
    fun observeBrowseCatalog(type: String) = kotlinx.coroutines.flow.combine(
        iptvDao.observeCategoriesByType(if (type == "movie") "VOD" else type.uppercase()),
        iptvDao.observeStreamsByType(if (type == "movie") "VOD" else type.uppercase())
    ) { categories, streams -> categories to streams }

    suspend fun getAvailableGenres(type: String): List<String> = withContext(Dispatchers.IO) {
        val streamType = if (type == "movie") "VOD" else type.uppercase()
        val rawList = iptvDao.getAvailableGenresByType(streamType)
        rawList.flatMap { rsv.squitv.domain.usecase.GenreUtils.extractNormalizedGenres(it) }
            .distinct()
            .sorted()
    }

    suspend fun getStreamsByGenreType(type: String, genre: String): List<IptvStreamEntity> = withContext(Dispatchers.IO) {
        val streamType = if (type == "movie") "VOD" else type.uppercase()
        iptvDao.getStreamsByGenreType(streamType, genre)
    }

    fun applyCategoryCustomization(
        categories: List<IptvCategoryEntity>,
        customOrder: List<String>? = null,
        hiddenCategories: Set<String>? = null
    ): List<IptvCategoryEntity> {
        return rsv.squitv.shared.catalog.CategoryCustomization.apply(categories, customOrder, hiddenCategories) { it.id }
    }
}
