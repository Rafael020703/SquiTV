package rsv.squitv.core.data.repository

import rsv.squitv.data.local.dao.IptvDao
import rsv.squitv.data.local.entities.*
import rsv.squitv.domain.model.IptvItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

import timber.log.Timber

@Singleton
class UserRepository @Inject constructor(
    private val iptvDao: IptvDao
) {
    suspend fun updateFavorite(item: IptvItem, isFav: Boolean) {
        val streamType = item.type.name
        val sId = item.id.toIntOrNull() ?: 0
        Timber.tag("SquiTV_E2E").i("[FAVORITE_ROOM] updateFavorite streamId=$sId streamType=$streamType name='${item.name}' isFav=$isFav")
        if (isFav) {
            iptvDao.insertFavorite(FavoriteEntity(
                streamId = sId,
                streamType = streamType,
                name = item.name,
                logo = item.icon,
                rating = item.rating,
                releaseDate = item.releaseDate,
                url = item.epgId,
                containerExtension = item.containerExtension,
                categoryId = item.categoryId
            ))
        } else {
            iptvDao.deleteFavorite(sId, streamType)
        }
    }

    fun getFavoritesListFlow(): Flow<List<FavoriteEntity>> = iptvDao.getFavoritesListFlow()

    suspend fun saveWatchProgress(
        streamId: Int,
        type: String,
        position: Long,
        duration: Long,
        seriesId: Int? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        Timber.tag("SquiTV_E2E").i("[PROGRESS_ROOM_SAVE] streamId=$streamId type=$type position=${position}ms duration=${duration}ms seriesId=$seriesId")
        iptvDao.insertWatchProgress(WatchProgressEntity(streamId, type, position, duration, timestamp, seriesId))
    }

    suspend fun getWatchProgress(streamId: Int): WatchProgressEntity? {
        val res = iptvDao.getWatchProgress(streamId)
        Timber.tag("SquiTV_E2E").d("[PROGRESS_ROOM_GET] streamId=$streamId -> pos=${res?.position}ms dur=${res?.duration}ms seriesId=${res?.seriesId}")
        return res
    }
    
    suspend fun getEpisodeByStreamId(streamId: Int): EpisodeEntity? = iptvDao.getEpisodeByStreamId(streamId)
    
    fun getAllWatchProgressFlow(): Flow<List<WatchProgressEntity>> = iptvDao.getAllWatchProgressFlow()
    
    suspend fun deleteWatchProgress(streamId: Int) {
        Timber.tag("SquiTV_E2E").i("[PROGRESS_ROOM_DELETE] streamId=$streamId")
        iptvDao.deleteWatchProgress(streamId)
    }
    
    suspend fun clearAllWatchProgress() = iptvDao.clearAllWatchProgress()

    suspend fun insertSearchHistory(query: String) = iptvDao.insertSearchHistory(SearchHistoryEntity(query))
    
    fun getSearchHistoryFlow(): Flow<List<SearchHistoryEntity>> = iptvDao.getSearchHistoryFlow()
    
    suspend fun clearSearchHistory() = iptvDao.clearSearchHistory()

    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>> = iptvDao.getAllDownloadsFlow()
    
    suspend fun getDownload(streamId: Int): DownloadEntity? = iptvDao.getDownload(streamId)
    
    suspend fun insertDownload(download: DownloadEntity) = iptvDao.insertDownload(download)
    
    suspend fun deleteDownload(streamId: Int) = iptvDao.deleteDownload(streamId)

    suspend fun insertPendingSync(pending: PendingSyncEntity) = iptvDao.insertPendingSync(pending)
    
    suspend fun getAllPendingSyncs(): List<PendingSyncEntity> = iptvDao.getAllPendingSyncs()
    
    suspend fun deletePendingSync(type: String, providerHash: String, contentId: String) = 
        iptvDao.deletePendingSync(type, providerHash, contentId)
}
