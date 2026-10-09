package rsv.squitv.core.data.repository

import rsv.squitv.data.local.dao.IptvDao
import rsv.squitv.data.local.entities.*
import rsv.squitv.domain.model.IptvItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val iptvDao: IptvDao
) {
    suspend fun updateFavorite(item: IptvItem, isFav: Boolean) {
        val streamType = item.type.name
        if (isFav) {
            iptvDao.insertFavorite(FavoriteEntity(
                streamId = item.id.toIntOrNull() ?: 0,
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
            iptvDao.deleteFavorite(item.id.toIntOrNull() ?: 0, streamType)
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
        iptvDao.insertWatchProgress(WatchProgressEntity(streamId, type, position, duration, timestamp, seriesId))
    }

    suspend fun getWatchProgress(streamId: Int): WatchProgressEntity? = iptvDao.getWatchProgress(streamId)
    
    fun getAllWatchProgressFlow(): Flow<List<WatchProgressEntity>> = iptvDao.getAllWatchProgressFlow()
    
    suspend fun deleteWatchProgress(streamId: Int) = iptvDao.deleteWatchProgress(streamId)
    
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
