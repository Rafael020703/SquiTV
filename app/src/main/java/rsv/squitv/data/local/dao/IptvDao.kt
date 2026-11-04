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
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<IptvCategoryEntity>)

    @Query("UPDATE iptv_categories SET name = :name WHERE id = :id AND type = :type")
    suspend fun updateCategoryName(id: String, type: String, name: String)

    @Transaction
    suspend fun upsertCategories(categories: List<IptvCategoryEntity>) {
        insertCategories(categories)
        categories.forEach { updateCategoryName(it.id, it.type, it.name) }
    }

    @Query("DELETE FROM iptv_categories WHERE id = :id AND type = :type")
    suspend fun deleteCatalogCategory(id: String, type: String)

    @Query("DELETE FROM iptv_streams WHERE id IN (:ids) AND streamType = :type")
    suspend fun deleteCatalogStreams(ids: List<Int>, type: String)

    /** Publish a fully downloaded type without replacing local preferences or detail metadata. */
    @Transaction
    suspend fun publishCatalogSnapshot(
        type: String,
        categories: List<IptvCategoryEntity>,
        streams: List<IptvStreamEntity>
    ) {
        require(categories.all { it.type == type } && streams.all { it.streamType == type })
        val categoryIds = categories.map { it.id }.toSet()
        val streamIds = streams.map { it.id }.toSet()
        val removedCategories = getCategoriesByType(type).filter { it.id !in categoryIds }
        val removedStreamIds = getStreamsByType(type).filter { it.id !in streamIds }.map { it.id }
        upsertCategories(categories)
        streams.chunked(500).forEach { upsertStreams(it) }
        removedStreamIds.chunked(500).forEach { deleteCatalogStreams(it, type) }
        removedCategories.forEach { deleteCatalogCategory(it.id, type) }
    }

    @Query("SELECT * FROM iptv_categories WHERE type = :type ORDER BY isPinned DESC, name ASC")
    suspend fun getCategoriesByType(type: String): List<IptvCategoryEntity>

    @Query("SELECT * FROM iptv_categories WHERE type = :type ORDER BY isPinned DESC, name ASC")
    fun observeCategoriesByType(type: String): Flow<List<IptvCategoryEntity>>

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
                containerExtension = stream.containerExtension,
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
        rating = COALESCE(:rating, rating),
        containerExtension = COALESCE(:containerExtension, containerExtension),
        added = :added, 
        releaseDate = COALESCE(:releaseDate, releaseDate),
        genre = COALESCE(:genre, genre)
        WHERE id = :id AND streamType = :type
    """)
    suspend fun updateBasicStreamFields(id: Int, type: String, name: String, categoryId: String, url: String?, logo: String?, rating: String?, containerExtension: String?, added: String?, releaseDate: String?, genre: String?)

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
        JOIN iptv_streams_fts f ON s.rowid = f.rowid
        WHERE iptv_streams_fts MATCH :query || '*'
        AND s.streamType = :type
        ORDER BY s.name ASC
    """)
    fun searchStreamsFtsPaged(query: String, type: String): androidx.paging.PagingSource<Int, IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE streamType = :type")
    suspend fun getStreamsByType(type: String): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE streamType = :type ORDER BY name ASC, id ASC")
    fun observeStreamsByType(type: String): Flow<List<IptvStreamEntity>>

    @Query("SELECT * FROM iptv_streams WHERE streamType = :type AND added IS NOT NULL AND added != '' ORDER BY added DESC, id DESC LIMIT :limit")
    suspend fun getRecentStreamsCandidatesByType(type: String, limit: Int): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE added IS NOT NULL AND added != '' ORDER BY added DESC, id DESC LIMIT :limit")
    suspend fun getRecentStreamsCandidates(limit: Int): List<IptvStreamEntity>

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
        JOIN iptv_streams_fts f ON s.rowid = f.rowid
        WHERE iptv_streams_fts MATCH :query || '*'
        AND s.streamType = :type
    """)
    suspend fun searchStreamsFts(query: String, type: String): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE name LIKE '%' || :query || '%'")
    suspend fun searchAllStreams(query: String): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE id IN (:ids)")
    suspend fun getStreamsByIds(ids: List<Int>): List<IptvStreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: rsv.squitv.data.local.entities.FavoriteEntity)

    @Query("DELETE FROM favorites WHERE accountId = :accountId AND profileId = :profileId AND streamId = :streamId AND streamType = :type")
    suspend fun deleteScopedFavorite(accountId: String, profileId: String, streamId: Int, type: String)

    @Query("SELECT * FROM favorites WHERE accountId = :accountId AND profileId = :profileId ORDER BY timestamp DESC")
    fun getScopedFavoritesFlow(accountId: String, profileId: String): kotlinx.coroutines.flow.Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    suspend fun getAllFavoritesForBackup(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<FavoriteEntity>)

    @Query("""INSERT OR IGNORE INTO favorites
        (streamId, streamType, name, logo, rating, releaseDate, url, containerExtension, categoryId, timestamp, accountId, profileId)
        SELECT streamId, streamType, name, logo, rating, releaseDate, url, containerExtension, categoryId, timestamp, :accountId, :profileId
        FROM favorites WHERE accountId = '' AND profileId = ''""")
    suspend fun copyLegacyFavorites(accountId: String, profileId: String)

    @Query("DELETE FROM favorites WHERE accountId = '' AND profileId = ''")
    suspend fun deleteLegacyFavorites()

    @Transaction
    suspend fun claimLegacyFavorites(accountId: String, profileId: String) {
        require(accountId.isNotBlank() && profileId.isNotBlank())
        copyLegacyFavorites(accountId, profileId)
        deleteLegacyFavorites()
    }

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(seasons: List<SeasonEntity>)

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY seasonNumber ASC")
    suspend fun getSeasons(seriesId: Int): List<SeasonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber ORDER BY episodeNum ASC, episodeId ASC")
    suspend fun getEpisodes(seriesId: Int, seasonNumber: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE streamId = :streamId LIMIT 1")
    suspend fun getEpisodeByStreamId(streamId: Int): EpisodeEntity?

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

    // Progress must never recreate a download removed while persistence was pending.
    @Query("""
        UPDATE downloads
        SET status = :status, progress = :progress, size = :size,
            downloadedBytes = :downloadedBytes, downloadSpeedMbps = :downloadSpeedMbps
        WHERE streamId = :streamId AND (
            status != :status OR ABS(progress - :progress) >= 0.5
            OR downloadedBytes != :downloadedBytes OR :status IN ('COMPLETED', 'FAILED')
        )
    """)
    suspend fun updateDownloadProgress(
        streamId: Int,
        status: String,
        progress: Float,
        size: Long,
        downloadedBytes: Long,
        downloadSpeedMbps: Double
    )

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

    data class CategoryCountDto(
        @androidx.room.ColumnInfo(name = "categoryId") val categoryId: String,
        @androidx.room.ColumnInfo(name = "count") val count: Int
    )

    @Query("SELECT categoryId, COUNT(*) as count FROM iptv_streams WHERE streamType = :type GROUP BY categoryId")
    suspend fun getCategoryCounts(type: String): List<CategoryCountDto>

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

    @Query("SELECT DISTINCT genre FROM iptv_streams WHERE (UPPER(streamType) = UPPER(:type) OR (:type = 'VOD' AND UPPER(streamType) = 'MOVIE') OR (:type = 'MOVIE' AND UPPER(streamType) = 'VOD')) AND genre IS NOT NULL AND genre != ''")
    suspend fun getAvailableGenresByType(type: String): List<String>

    @Query("SELECT * FROM iptv_streams WHERE genre LIKE '%' || :genre || '%' LIMIT :limit")
    suspend fun getStreamsByGenre(genre: String, limit: Int): List<IptvStreamEntity>

    @Query("SELECT * FROM iptv_streams WHERE (UPPER(streamType) = UPPER(:type) OR (:type = 'VOD' AND UPPER(streamType) = 'MOVIE') OR (:type = 'MOVIE' AND UPPER(streamType) = 'VOD')) AND genre LIKE '%' || :genre || '%' ORDER BY name ASC")
    suspend fun getStreamsByGenreType(type: String, genre: String): List<IptvStreamEntity>

    @Update
    suspend fun updateStream(stream: IptvStreamEntity)

    @Query("UPDATE iptv_streams SET genre = COALESCE(:genre, genre), `cast` = COALESCE(:cast, `cast`), director = COALESCE(:director, director), plot = COALESCE(:plot, plot), duration = COALESCE(:duration, duration), backdrop = COALESCE(:backdrop, backdrop), youtubeTrailer = COALESCE(:trailer, youtubeTrailer) WHERE id = :id AND (UPPER(streamType) = UPPER(:type) OR (UPPER(:type) = 'VOD' AND UPPER(streamType) = 'MOVIE') OR (UPPER(:type) = 'MOVIE' AND UPPER(streamType) = 'VOD'))")
    suspend fun updateDetailedStreamMetadata(id: Int, type: String, genre: String?, cast: String?, director: String?, plot: String?, duration: String?, backdrop: String?, trailer: String?)

    @Query("UPDATE iptv_streams SET `cast` = COALESCE(:cast, `cast`), director = COALESCE(:director, director) WHERE id = :id AND streamType = :type")
    suspend fun updateStreamMetadata(id: Int, type: String, cast: String?, director: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMetadataCache(entry: rsv.squitv.data.local.entities.CatalogMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMetadataCacheList(entries: List<rsv.squitv.data.local.entities.CatalogMetadataEntity>)

    @Query("SELECT * FROM catalog_metadata_cache WHERE accountKey = :accountKey AND streamType = :type AND streamId = :id")
    suspend fun getMetadataCache(accountKey: String, type: String, id: Int): rsv.squitv.data.local.entities.CatalogMetadataEntity?

    @Query("SELECT * FROM catalog_metadata_cache WHERE accountKey = :accountKey AND streamType = :type")
    suspend fun getAllMetadataCacheForAccount(accountKey: String, type: String): List<rsv.squitv.data.local.entities.CatalogMetadataEntity>

    @Query("""
        SELECT DISTINCT c.* FROM catalog_metadata_cache c
        INNER JOIN iptv_streams s ON c.streamId = s.id AND (c.streamType = s.streamType OR (c.streamType = 'VOD' AND s.streamType = 'MOVIE') OR (c.streamType = 'SERIES' AND s.streamType = 'SERIES'))
        WHERE c.accountKey = :accountKey
          AND (c.streamType = :type OR (:type = 'VOD' AND s.streamType = 'MOVIE') OR (:type = 'SERIES' AND s.streamType = 'SERIES'))
          AND c.status IN ('PENDING', 'FAILED_TEMPORARY', 'NO_DATA')
          AND (c.status != 'FAILED_TEMPORARY' OR c.attempts < 5)
          AND c.nextEligibleAt <= :now
        ORDER BY s.name ASC, c.streamId ASC
        LIMIT :limit
    """)
    suspend fun getPendingMetadataItems(accountKey: String, type: String, now: Long, limit: Int): List<rsv.squitv.data.local.entities.CatalogMetadataEntity>

    @Query("UPDATE catalog_metadata_cache SET status = 'PENDING', attempts = 0, nextEligibleAt = 0, errorMessage = NULL WHERE accountKey = :accountKey AND status = 'FAILED_TEMPORARY'")
    suspend fun retryFailedMetadata(accountKey: String)

    @Query("SELECT COUNT(*) FROM catalog_metadata_cache WHERE accountKey = :accountKey AND streamType = :type AND status = :status")
    suspend fun getMetadataCountByStatus(accountKey: String, type: String, status: String): Int

    @Query("SELECT COUNT(*) FROM catalog_metadata_cache WHERE accountKey = :accountKey AND streamType = :type")
    suspend fun getTotalMetadataCount(accountKey: String, type: String): Int

    @Query("""
        SELECT c.streamType, CASE WHEN c.status = 'NO_DATA' AND c.nextEligibleAt <= :now
            THEN 'PENDING' ELSE c.status END AS status, COUNT(*) AS itemCount FROM catalog_metadata_cache c
        WHERE c.accountKey = :accountKey AND EXISTS (
            SELECT 1 FROM iptv_streams s WHERE s.id = c.streamId AND
            (s.streamType = c.streamType OR (c.streamType = 'VOD' AND s.streamType = 'MOVIE'))
        ) GROUP BY c.streamType, CASE WHEN c.status = 'NO_DATA' AND c.nextEligibleAt <= :now
            THEN 'PENDING' ELSE c.status END
    """)
    suspend fun getMetadataProgress(accountKey: String, now: Long): List<CatalogMetadataCount>

    @Transaction
    suspend fun applyPreparationBatch(rows: List<CatalogMetadataEntity>, details: List<CatalogMetadataUpdate>) {
        details.forEach { item ->
            updateDetailedStreamMetadata(item.id, item.type, item.genre, item.cast, item.director,
                item.plot, item.duration, item.backdrop, item.trailer)
        }
        insertOrUpdateMetadataCacheList(rows)
    }

    @Query("SELECT * FROM iptv_streams WHERE streamType IN ('VOD', 'MOVIE', 'SERIES')")
    suspend fun getVodAndSeriesStreams(): List<IptvStreamEntity>

    @Query("SELECT id, streamType, genre FROM iptv_streams WHERE streamType IN ('VOD', 'MOVIE', 'SERIES')")
    suspend fun getMetadataSeedStreams(): List<CatalogMetadataSeed>

    @Query("SELECT streamType, streamId FROM catalog_metadata_cache WHERE accountKey = :accountKey")
    suspend fun getMetadataKeys(accountKey: String): List<CatalogMetadataKey>

    @Transaction
    @Query("SELECT * FROM iptv_streams WHERE (streamType = 'VOD' OR streamType = 'SERIES') AND `cast` LIKE '%' || :actor || '%'")
    suspend fun getStreamsByActor(actor: String): List<IptvStreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE stopTime < :currentTime")
    suspend fun deleteOldEpgPrograms(currentTime: Long)

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND stopTime > :currentTime ORDER BY startTime ASC")
    fun getEpgForChannel(channelId: String, currentTime: Long): kotlinx.coroutines.flow.Flow<List<EpgProgramEntity>>

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
