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
        iptvDao.insertCategories(entities)
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
            entities.chunked(1000).forEach { batch -> iptvDao.insertStreamsBatch(batch) }
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
        iptvDao.insertCategories(entities)
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
            entities.chunked(1000).forEach { batch -> iptvDao.insertStreamsBatch(batch) }
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
        iptvDao.insertCategories(entities)
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
        return xtreamService.getVodInfo(credentials.username, credentials.password, vodId = vodId)
    }

    suspend fun getSeriesMetadata(credentials: XtreamCredentials, seriesId: Int): XtreamSeries? {
        return try {
            val response = xtreamService.getSeriesEpisodes(credentials.username, credentials.password, seriesId = seriesId)
            response.info
        } catch (e: Exception) { null }
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

    suspend fun searchStreams(query: String, type: String, blockedIds: Set<String> = emptySet()): List<IptvStreamEntity> {
        val blockedList = blockedIds.toList()
        return if (type == "ALL") {
            if (blockedList.isEmpty()) iptvDao.searchAllStreamsFiltered(query, listOf("DUMMY_ID_THAT_DOES_NOT_EXIST")) 
            else iptvDao.searchAllStreamsFiltered(query, blockedList)
        } else {
            if (query.length >= 2) {
                iptvDao.searchStreamsFtsFiltered(query, type, blockedList)
            } else {
                if (blockedList.isEmpty()) iptvDao.searchStreamsFtsFiltered("", type, listOf("DUMMY_ID_THAT_DOES_NOT_EXIST"))
                else iptvDao.searchStreamsFtsFiltered("", type, blockedList)
            }
        }
    }
    
    suspend fun hasLocalContent(): Boolean = iptvDao.hasAnyContent()

    fun getStreamsCountFlow(): Flow<Int> = iptvDao.getStreamsCountFlow()

    suspend fun getStreamsByIds(ids: List<Int>): List<IptvStreamEntity> = iptvDao.getStreamsByIds(ids)

    suspend fun getStreamsByActor(actor: String): List<IptvStreamEntity> = iptvDao.getStreamsByActor(actor)

    suspend fun updateStreamMetadata(id: Int, type: String, cast: String?, director: String?) {
        iptvDao.updateStreamMetadata(id, type, cast, director)
    }

    suspend fun updateDetailedStreamMetadata(id: Int, type: String, cast: String?, director: String?, plot: String?, duration: String?, backdrop: String?, trailer: String?) {
        iptvDao.updateDetailedStreamMetadata(id, type, cast, director, plot, duration, backdrop, trailer)
    }

    suspend fun updateCategoryLock(id: String, type: String, locked: Boolean) = iptvDao.updateCategoryLock(id, type, locked)
    suspend fun updateCategoryPinned(id: String, type: String, pinned: Boolean) = iptvDao.updateCategoryPinned(id, type, pinned)
    suspend fun getStreamCountByCategory(categoryId: String, type: String): Int = iptvDao.getStreamCountByCategory(categoryId, type)

    suspend fun searchEpisodes(query: String, blockedIds: Set<String> = emptySet()): List<EpisodeEntity> {
        return if (blockedIds.isEmpty()) iptvDao.searchEpisodes(query) else iptvDao.searchEpisodesFiltered(query, blockedIds.toList())
    }

    suspend fun getStats(): Triple<Int, Int, Int> = Triple(iptvDao.getLiveCount(), iptvDao.getMovieCount(), iptvDao.getSeriesCount())
    
    suspend fun getNewItemsSince(timestamp: Long): Pair<List<String>, List<String>> {
        val ts = (timestamp / 1000).toString()
        val movies = iptvDao.getNewMovies(ts).map { it.name }
        val series = iptvDao.getNewSeries(ts).map { it.name }
        return Pair(movies, series)
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

    private fun sortAndFilterRecentStreams(streams: List<IptvStreamEntity>, limit: Int): List<IptvItem> {
        return streams
            .sortedWith(
                compareByDescending<IptvStreamEntity> { parseAddedTimestamp(it.added) }
                    .thenByDescending { it.id }
            )
            .take(limit)
            .map { it.toIptvItem() }
    }

    suspend fun getRecentMovies(limit: Int = 100): List<IptvItem> {
        val streams = iptvDao.getStreamsByType("VOD")
        return sortAndFilterRecentStreams(streams, limit)
    }

    suspend fun getRecentSeries(limit: Int = 100): List<IptvItem> {
        val streams = iptvDao.getStreamsByType("SERIES")
        return sortAndFilterRecentStreams(streams, limit)
    }

    suspend fun getRecentLiveStreams(limit: Int = 100): List<IptvItem> {
        val streams = iptvDao.getStreamsByType("LIVE")
        return sortAndFilterRecentStreams(streams, limit)
    }

    suspend fun getRecentStreams(limit: Int = 20): List<IptvItem> = sortAndFilterRecentStreams(iptvDao.getStreamsByType("VOD") + iptvDao.getStreamsByType("SERIES") + iptvDao.getStreamsByType("LIVE"), limit)
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
        } catch (e: Exception) { return Pair(emptyList(), emptyMap()) }
        val seasons = mutableListOf<SeasonEntity>()
        val episodesMap = mutableMapOf<Int, List<EpisodeEntity>>()
        response.seasons?.forEach { xtreamSeason ->
            val sn = xtreamSeason.seasonNumber ?: 0
            seasons.add(SeasonEntity(seriesId, sn, xtreamSeason.name ?: "Season $sn", xtreamSeason.cover ?: response.info?.cover))
            val eps = response.episodes?.get(sn.toString()) ?: response.episodes?.get(xtreamSeason.name) ?: emptyList()
            episodesMap[sn] = eps.map { EpisodeEntity(seriesId, sn, it.id?.toIntOrNull() ?: 0, it.episodeNum?.toIntOrNull() ?: 0, it.title ?: "", it.id?.toIntOrNull() ?: 0, it.container_extension, it.info?.movieImage, it.info?.plot, it.info?.duration, it.info?.rating) }
        }
        if (seasons.isNotEmpty()) {
            iptvDao.insertSeasons(seasons)
            episodesMap.values.forEach { iptvDao.insertEpisodes(it) }
        }
        return Pair(seasons.sortedBy { it.seasonNumber }, episodesMap)
    }

    fun groupLiveStreams(streams: List<XtreamStream>): List<IptvItem> {
        val qualityTags = listOf("4K", "FHD", "HD", "SD", "H265", "HEVC", "1080P", "720P", "LOW")
        val regex = Regex("\\s*(\\(|\\[|\\|)?\\s*(${qualityTags.joinToString("|")})\\s*(\\)|\\]|\\|)?\\s*", RegexOption.IGNORE_CASE)
        return streams.groupBy { it.name?.replace(regex, " ")?.trim()?.uppercase() ?: "" }.map { (baseName, group) ->
            val main = group.sortedByDescending { s -> val n = s.name?.uppercase() ?: ""; when { n.contains("4K") -> 4; n.contains("FHD") -> 3; n.contains("HD") -> 2; n.contains("SD") -> 1; else -> 0 } }.first()
            val qualities = group.associate { s -> val n = s.name?.uppercase() ?: ""; val q = when { n.contains("4K") -> "4K"; n.contains("FHD") -> "FHD"; n.contains("HD") -> "HD"; n.contains("SD") -> "SD"; else -> "AUTO" }; q to (s.streamId ?: 0) }
            IptvItem(main.streamId.toString(), baseName.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }, main.streamIcon, ContentType.LIVE, main.epgChannelId, qualities = qualities, categoryId = main.categoryId)
        }
    }
}
