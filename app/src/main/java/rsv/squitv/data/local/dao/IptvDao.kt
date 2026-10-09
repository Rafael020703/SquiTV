package rsv.squitv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import rsv.squitv.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<IptvCategoryEntity>)

    @Query("SELECT * FROM iptv_categories WHERE type = :type ORDER BY isPinned DESC, name ASC")
    suspend fun getCategoriesByType(type: String): List<IptvCategoryEntity>

    @Query("UPDATE iptv_categories SET isLocked = :locked WHERE id = :id AND type = :type")
    suspend fun updateCategoryLock(id: String, type: String, locked: Boolean)

    @Query("UPDATE iptv_categories SET isPinned = :pinned WHERE id = :id AND type = :type")
    suspend fun updateCategoryPinned(id: String, type: String, pinned: Boolean)

    @Query("SELECT COUNT(*) FROM iptv_streams WHERE categoryId = :categoryId AND streamType = :type")
    suspend fun getStreamCountByCategory(categoryId: String, type: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM iptv_streams LIMIT 1)")
    suspend fun hasAnyContent(): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreamsBatch(streams: List<IptvStreamEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStreamsIgnore(streams: List<IptvStreamEntity>): List<Long>

    @Transaction
    suspend fun upsertStreams(streams: List<IptvStreamEntity>) {
        val insertResults = insertStreamsIgnore(streams)
        val updateList = mutableListOf<IptvStreamEntity>()
        
        for (i in insertResults.indices) {
            if (insertResults[i] == -1L) {
                updateList.add(streams[i])
            }
        }
        
        if (updateList.isNotEmpty()) {
            updateBasicStreamInfo(updateList)
        }
    }

    @Transaction
    suspend fun updateBasicStreamInfo(streams: List<IptvStreamEntity>) {
        streams.forEach { stream ->
            updateBasicStreamFields(
                id = stream.id,
                type = stream.streamType,
                name = stream.name,
                categoryId = stream.categoryId,
                url = stream.url,
                logo = stream.logo,
                rating = stream.rating,
                added = stream.added,
                releaseDate = stream.releaseDate,
                genre = stream.genre
            )
        }
    }

    @Query("""
        UPDATE iptv_streams SET 
        name = :name, 
        categoryId = :categoryId, 
        url = :url, 
        logo = :logo, 
        rating = :rating, 
        added = :added, 
        releaseDate = :releaseDate, 
        genre = :genre 
        WHERE id = :id AND streamType = :type
    """)
    suspend fun updateBasicStreamFields(id: Int, type: String, name: String, categoryId: String, url: String?, logo: String?, rating: String?, added: String?, releaseDate: String?, genre: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<IptvStreamEntity>)

    @Query("SELECT * FROM iptv_streams WHERE categoryId = :categoryId AND streamType = :type ORDER BY name ASC")
    fun getStreamsByCategoryPaged(categoryId: String, type: String): androidx.paging.PagingSource<Int, IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE streamType = :type ORDER BY name ASC")
    fun getStreamsByTypePaged(type: String): androidx.paging.PagingSource<Int, IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE name LIKE '%' || :query || '%' AND streamType = :type ORDER BY name ASC")
    fun searchStreamsPaged(query: String, type: String): androidx.paging.PagingSource<Int, IptvStreamEntity>

    @Query("""
        SELECT s.* FROM iptv_streams s
        JOIN iptv_streams_fts f ON s.name = f.name
        WHERE iptv_streams_fts MATCH :query || '*'
        AND s.streamType = :type
        ORDER BY s.name ASC
    """)
    fun searchStreamsFtsPaged(query: String, type: String): androidx.paging.PagingSource<Int, IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE streamType = :type")
    suspend fun getStreamsByType(type: String): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE categoryId = :categoryId AND streamType = :type")
    suspend fun getStreamsByCategory(categoryId: String, type: String): List<IptvStreamEntity>

    @Query("DELETE FROM iptv_categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM iptv_streams")
    suspend fun deleteAllStreams()

    @Query("DELETE FROM iptv_categories WHERE type = :type")
    suspend fun deleteAllCategoriesByType(type: String)

    @Query("""
        SELECT s.* FROM iptv_streams s
        JOIN iptv_streams_fts f ON s.name = f.name
        WHERE iptv_streams_fts MATCH :query || '*'
        AND s.streamType = :type
        AND s.categoryId NOT IN (:blockedIds)
    """)
    suspend fun searchStreamsFtsFiltered(query: String, type: String, blockedIds: List<String>): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE name LIKE '%' || :query || '%' AND categoryId NOT IN (:blockedIds)")
    suspend fun searchAllStreamsFiltered(query: String, blockedIds: List<String>): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE id IN (:ids)")
    suspend fun getStreamsByIds(ids: List<Int>): List<IptvStreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: rsv.squitv.data.local.entities.FavoriteEntity)

    @Query("DELETE FROM favorites WHERE streamId = :streamId AND streamType = :type")
    suspend fun deleteFavorite(streamId: Int, type: String)

    @Query("DELETE FROM favorites WHERE streamId = :streamId")
    suspend fun deleteFavorite(streamId: Int)

    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    fun getFavoritesListFlow(): kotlinx.coroutines.flow.Flow<List<rsv.squitv.data.local.entities.FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE streamType = :type ORDER BY timestamp DESC")
    fun getFavoritesByTypeFlow(type: String): kotlinx.coroutines.flow.Flow<List<rsv.squitv.data.local.entities.FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE streamId = :streamId AND streamType = :type)")
    suspend fun isFavorite(streamId: Int, type: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE streamId = :streamId)")
    suspend fun isFavorite(streamId: Int): Boolean

    @Transaction
    @Query("SELECT s.* FROM iptv_streams s INNER JOIN favorites f ON s.id = f.streamId AND s.streamType = f.streamType")
    fun getFavoritesFlow(): kotlinx.coroutines.flow.Flow<List<IptvStreamEntity>>

    @Query("SELECT * FROM episodes WHERE title LIKE '%' || :query || '%'")
    suspend fun searchEpisodes(query: String): List<EpisodeEntity>

    @Transaction
    @Query("""
        SELECT e.* FROM episodes e 
        INNER JOIN iptv_streams s ON e.seriesId = s.id AND e.streamType = s.streamType 
        WHERE e.title LIKE '%' || :query || '%' AND s.categoryId NOT IN (:blockedIds)
    """)
    suspend fun searchEpisodesFiltered(query: String, blockedIds: List<String>): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(seasons: List<SeasonEntity>)

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY seasonNumber ASC")
    suspend fun getSeasons(seriesId: Int): List<SeasonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber ORDER BY episodeNum ASC, episodeId ASC")
    suspend fun getEpisodes(seriesId: Int, seasonNumber: Int): List<EpisodeEntity>

    @Query("DELETE FROM seasons WHERE seriesId = :seriesId")
    suspend fun deleteSeasons(seriesId: Int)

    @Query("DELETE FROM seasons")
    suspend fun deleteAllSeasons()

    @Query("DELETE FROM episodes")
    suspend fun deleteAllEpisodes()

    @Query("DELETE FROM favorites")
    suspend fun deleteAllFavorites()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchProgress(progress: rsv.squitv.data.local.entities.WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE streamId = :streamId")
    suspend fun getWatchProgress(streamId: Int): rsv.squitv.data.local.entities.WatchProgressEntity?

    @Query("SELECT * FROM watch_progress")
    fun getAllWatchProgressFlow(): kotlinx.coroutines.flow.Flow<List<rsv.squitv.data.local.entities.WatchProgressEntity>>

    @Query("DELETE FROM watch_progress WHERE streamId = :streamId")
    suspend fun deleteWatchProgress(streamId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads")
    fun getAllDownloadsFlow(): kotlinx.coroutines.flow.Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE streamId = :streamId")
    suspend fun getDownload(streamId: Int): DownloadEntity?

    @Query("DELETE FROM downloads WHERE streamId = :streamId")
    suspend fun deleteDownload(streamId: Int)

    @Query("DELETE FROM watch_progress")
    suspend fun clearAllWatchProgress()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(item: rsv.squitv.data.local.entities.SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getSearchHistoryFlow(): kotlinx.coroutines.flow.Flow<List<rsv.squitv.data.local.entities.SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("SELECT COUNT(*) FROM iptv_streams")
    fun getStreamsCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM iptv_streams WHERE streamType = 'LIVE'")
    suspend fun getLiveCount(): Int

    @Query("SELECT COUNT(*) FROM iptv_streams WHERE streamType = 'VOD'")
    suspend fun getMovieCount(): Int

    @Query("SELECT COUNT(*) FROM iptv_streams WHERE streamType = 'SERIES'")
    suspend fun getSeriesCount(): Int

    @Query("SELECT * FROM iptv_streams WHERE streamType = 'VOD' AND added > :timestamp")
    suspend fun getNewMovies(timestamp: String): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE streamType = 'SERIES' AND added > :timestamp")
    suspend fun getNewSeries(timestamp: String): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams ORDER BY added DESC LIMIT :limit")
    suspend fun getRecentStreams(limit: Int): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE (streamType = 'VOD' OR streamType = 'SERIES') AND rating >= :minRating ORDER BY rating DESC LIMIT :limit")
    suspend fun getTopRatedStreams(minRating: String, limit: Int): List<IptvStreamEntity>

    @Query("SELECT DISTINCT genre FROM iptv_streams WHERE streamType = 'SERIES' AND genre IS NOT NULL AND genre != ''")
    suspend fun getAvailableGenres(): List<String>

    @Query("SELECT * FROM iptv_streams WHERE genre LIKE '%' || :genre || '%' LIMIT :limit")
    suspend fun getStreamsByGenre(genre: String, limit: Int): List<IptvStreamEntity>

    @Update
    suspend fun updateStream(stream: IptvStreamEntity)

    @Query("UPDATE iptv_streams SET `cast` = :cast, director = :director, plot = :plot, duration = :duration, backdrop = :backdrop, youtubeTrailer = :trailer WHERE id = :id AND streamType = :type")
    suspend fun updateDetailedStreamMetadata(id: Int, type: String, cast: String?, director: String?, plot: String?, duration: String?, backdrop: String?, trailer: String?)

    @Query("UPDATE iptv_streams SET `cast` = :cast, director = :director WHERE id = :id AND streamType = :type")
    suspend fun updateStreamMetadata(id: Int, type: String, cast: String?, director: String?)

    @Transaction
    @Query("SELECT * FROM iptv_streams WHERE (streamType = 'VOD' OR streamType = 'SERIES') AND `cast` LIKE '%' || :actor || '%'")
    suspend fun getStreamsByActor(actor: String): List<IptvStreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: EpgReminderEntity)

    @Query("SELECT * FROM epg_reminders")
    fun getAllRemindersFlow(): kotlinx.coroutines.flow.Flow<List<EpgReminderEntity>>

    @Query("DELETE FROM epg_reminders WHERE streamId = :streamId AND startTime = :startTime")
    suspend fun deleteReminder(streamId: Int, startTime: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM epg_reminders WHERE streamId = :streamId AND startTime = :startTime)")
    suspend fun hasReminder(streamId: Int, startTime: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE stopTime < :currentTime")
    suspend fun deleteOldEpgPrograms(currentTime: Long)

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND stopTime > :currentTime ORDER BY startTime ASC")
    fun getEpgForChannel(channelId: String, currentTime: Long): kotlinx.coroutines.flow.Flow<List<EpgProgramEntity>>

    @Query("SELECT * FROM epg_programs WHERE stopTime > :startTime AND startTime < :stopTime ORDER BY startTime ASC")
    suspend fun getEpgInRange(startTime: Long, stopTime: Long): List<EpgProgramEntity>

    @Query("DELETE FROM epg_programs")
    suspend fun clearAllEpg()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgStaging(programs: List<EpgProgramStagingEntity>)

    @Query("DELETE FROM epg_programs_staging")
    suspend fun clearEpgStaging()

    @Query("INSERT INTO epg_programs SELECT * FROM epg_programs_staging")
    suspend fun copyStagingToMain()

    @Transaction
    suspend fun publishStagingEpg() {
        clearAllEpg()
        copyStagingToMain()
        clearEpgStaging()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingSync(pendingSync: PendingSyncEntity)

    @Query("SELECT * FROM pending_syncs ORDER BY timestamp ASC")
    suspend fun getAllPendingSyncs(): List<PendingSyncEntity>

    @Query("DELETE FROM pending_syncs WHERE type = :type AND providerHash = :providerHash AND contentId = :contentId")
    suspend fun deletePendingSync(type: String, providerHash: String, contentId: String)
}
