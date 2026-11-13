package rsv.squitv.core.data.repository

import rsv.squitv.data.local.dao.IptvDao
import rsv.squitv.data.local.entities.*
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.shared.account.CloudProgress
import rsv.squitv.shared.account.CloudProgressJournal
import rsv.squitv.shared.account.FavoriteCloudSession
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.model.SettingsBackupCodec
import javax.inject.Inject
import javax.inject.Singleton

import timber.log.Timber

@Singleton
class UserRepository @Inject constructor(
    private val iptvDao: IptvDao,
    private val settingsRepository: SettingsRepository
) {
    suspend fun updateFavorite(item: IptvItem, isFav: Boolean, profileId: String? = null) {
        val settings = settingsRepository.settingsFlow.first()
        val credentials = settings.credentials ?: return
        val accountId = SettingsBackupCodec.fingerprint(SettingsBackupCodec.accountKey(credentials))
        val targetProfile = profileId ?: settings.activeProfileId
        iptvDao.claimLegacyFavorites(accountId, targetProfile)
        val streamType = item.type.name
        val sId = item.id.toIntOrNull() ?: return
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
                categoryId = item.categoryId,
                accountId = accountId, profileId = targetProfile
            ))
        } else {
            iptvDao.deleteScopedFavorite(accountId, targetProfile, sId, streamType)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getFavoritesListFlow(): Flow<List<FavoriteEntity>> = settingsRepository.settingsFlow.map { settings ->
        settings.credentials?.let { SettingsBackupCodec.fingerprint(SettingsBackupCodec.accountKey(it)) to settings.activeProfileId }
    }.distinctUntilChanged().flatMapLatest { scope ->
        if (scope == null) flowOf(emptyList()) else flow {
            iptvDao.claimLegacyFavorites(scope.first, scope.second)
            emitAll(iptvDao.getScopedFavoritesFlow(scope.first, scope.second))
        }
    }

    internal fun favoriteCloudStore(
        accountId: String,
        profileId: String,
        profileName: String,
        cloudScope: String,
    ): rsv.squitv.shared.account.FavoriteLocalStore {
        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$accountId/$profileId/$cloudScope".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return object : rsv.squitv.shared.account.FavoriteLocalStore {
            override val label = profileName
            override suspend fun checkpoint() = settingsRepository.loadFavoriteCheckpoint(key)
            override suspend fun saveCheckpoint(checkpoint: rsv.squitv.shared.account.FavoriteCheckpoint) =
                settingsRepository.saveFavoriteCheckpoint(key, checkpoint)
            override suspend fun snapshot() = iptvDao.getScopedFavoritesSnapshot(accountId, profileId).mapNotNull { row ->
                val kind = when (row.streamType.lowercase()) {
                    "movie", "vod" -> "movie"
                    "series" -> "series"
                    "live" -> "live"
                    else -> return@mapNotNull null
                }
                val title = row.name.filterNot(Char::isISOControl).take(180).trimEnd { Character.isHighSurrogate(it) }
                rsv.squitv.shared.account.CloudFavorite(kind, row.streamId, title)
            }
            override suspend fun applyProfile(profile: rsv.squitv.shared.account.CloudProfile) =
                settingsRepository.applyCloudProfileName(accountId, profileId, profile.name)
            override suspend fun progressJournal() = settingsRepository.loadCloudProgress(key)
            override suspend fun saveProgressJournal(journal: CloudProgressJournal) = settingsRepository.saveCloudProgress(key, journal)
            override suspend fun applyProgress(boundAt: Long, rows: List<CloudProgress>) {
                // Android keeps the downloaded progress in a scoped overlay, never in the legacy table.
                categorySnapshot()
            }
            override suspend fun categorySnapshot(): List<rsv.squitv.shared.account.CloudCategories> {
                val settings = settingsRepository.settingsFlow.first()
                val accountKey = settings.credentials?.let(SettingsBackupCodec::accountKey)
                    ?: throw IllegalStateException("Entre novamente para sincronizar.")
                if (SettingsBackupCodec.fingerprint(accountKey) != accountId || settings.activeProfileId != profileId)
                    throw IllegalStateException("O perfil ativo mudou.")
                return settingsRepository.cloudCategories(settings, accountKey, profileId)
            }
            override suspend fun applyCategories(
                expected: List<rsv.squitv.shared.account.CloudCategories>,
                cloudRows: List<rsv.squitv.shared.account.CloudCategories>,
            ) = settingsRepository.applyCloudCategories(accountId, profileId, expected, cloudRows)
            override suspend fun applyChanges(
                expected: Set<Pair<String, Int>>,
                cloudRows: List<rsv.squitv.shared.account.CloudFavorite>,
            ): Boolean {
                val settings = settingsRepository.settingsFlow.first()
                val current = settings.credentials?.let { SettingsBackupCodec.fingerprint(SettingsBackupCodec.accountKey(it)) }
                return current == accountId && settings.activeProfileId == profileId &&
                    iptvDao.applyCloudFavorites(accountId, profileId, expected, cloudRows)
            }
        }
    }

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

    private fun activeProgressStore(settings: SettingsRepository.AppSettings): rsv.squitv.shared.account.FavoriteLocalStore? {
        val active = FavoriteCloudSession.state.value ?: return null
        val account = settings.credentials?.let { SettingsBackupCodec.fingerprint(SettingsBackupCodec.accountKey(it)) }
        if (account != active.localAccount) return null
        return favoriteCloudStore(active.localAccount, settings.activeProfileId, "", active.client.scopeId)
    }

    class CloudPlaybackScope internal constructor(
        val account: String, val profile: String, val link: String,
    )

    internal suspend fun cloudPlaybackScope(credentials: rsv.squitv.data.model.XtreamCredentials): CloudPlaybackScope? {
        val active = FavoriteCloudSession.state.value ?: return null
        val account = SettingsBackupCodec.fingerprint(SettingsBackupCodec.accountKey(credentials))
        val settings = settingsRepository.settingsFlow.first()
        val current = settings.credentials?.let { SettingsBackupCodec.fingerprint(SettingsBackupCodec.accountKey(it)) }
        if (account != active.localAccount || current != account) return null
        return CloudPlaybackScope(account, settings.activeProfileId, active.client.scopeId)
    }

    internal suspend fun recordNewCloudProgress(
        streamId: Int, type: String, position: Long, duration: Long,
        seriesId: Int?, timestamp: Long, completed: Boolean, scope: CloudPlaybackScope, title: String?,
    ) {
        val kind = when (type.lowercase()) {
            "vod", "movie" -> "movie"
            "episode", "series" -> "episode"
            else -> return
        }
        if (streamId <= 0 || duration !in 1..172800000) return
        val active = FavoriteCloudSession.state.value ?: return
        if (active.localAccount != scope.account || active.client.scopeId != scope.link) return
        val local = favoriteCloudStore(scope.account, scope.profile, "", scope.link)
        FavoriteCloudSession.recordProgress(local, CloudProgress(
            kind = kind, id = streamId, catalogId = if (kind == "movie") streamId else seriesId?.takeIf { it > 0 },
            title = title?.filterNot(Char::isISOControl)?.take(180)?.trimEnd { Character.isHighSurrogate(it) },
            positionMs = position.coerceIn(0, duration), durationMs = duration,
            completed = completed, observedAt = timestamp,
        ))
    }

    private suspend fun mergeCloudProgress(
        entries: List<WatchProgressEntity>,
        settings: SettingsRepository.AppSettings,
    ): List<WatchProgressEntity> {
        val local = activeProgressStore(settings) ?: return entries
        val (checkpoint, journal) = try {
            (local.checkpoint() ?: return entries) to local.progressJournal()
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (_: Exception) {
            return entries
        }
        val newest = (journal.downloaded + journal.pending).groupBy { it.id }
            .mapValues { (_, rows) -> rows.maxBy { it.observedAt } }
        val result = entries.associateBy { it.streamId }.toMutableMap()
        for ((id, row) in newest) {
            val old = result[id]
            if (old != null && old.lastWatched > row.observedAt) continue
            val duration = row.durationMs
            if (row.completed || row.deleted) {
                if (old != null && checkpoint.boundAt > 0 && old.lastWatched >= checkpoint.boundAt) result.remove(id)
            } else if (duration != null && duration > 0) {
                result[id] = WatchProgressEntity(
                    id, if (row.kind == "movie") "VOD" else "SERIES", row.positionMs,
                    duration, row.observedAt, row.catalogId.takeIf { row.kind == "episode" },
                )
            }
        }
        return result.values.toList()
    }

    suspend fun getWatchProgress(streamId: Int): WatchProgressEntity? {
        val local = iptvDao.getWatchProgress(streamId)
        val res = mergeCloudProgress(listOfNotNull(local), settingsRepository.settingsFlow.first())
            .firstOrNull { it.streamId == streamId }
        Timber.tag("SquiTV_E2E").d("[PROGRESS_ROOM_GET] streamId=$streamId -> pos=${res?.position}ms dur=${res?.duration}ms seriesId=${res?.seriesId}")
        return res
    }
    
    suspend fun getEpisodeByStreamId(streamId: Int): EpisodeEntity? = iptvDao.getEpisodeByStreamId(streamId)
    
    fun getAllWatchProgressFlow(): Flow<List<WatchProgressEntity>> = combine(
        iptvDao.getAllWatchProgressFlow(), settingsRepository.settingsFlow,
        FavoriteCloudSession.state, FavoriteCloudSession.progressUpdates,
    ) { entries, settings, _, _ -> mergeCloudProgress(entries, settings) }
    
    suspend fun deleteWatchProgress(streamId: Int) {
        Timber.tag("SquiTV_E2E").i("[PROGRESS_ROOM_DELETE] streamId=$streamId")
        iptvDao.deleteWatchProgress(streamId)
    }
    
    suspend fun clearAllWatchProgress() = iptvDao.clearAllWatchProgress()

    suspend fun insertSearchHistory(query: String) = iptvDao.insertSearchHistory(SearchHistoryEntity(query))
    
    fun getSearchHistoryFlow(): Flow<List<SearchHistoryEntity>> = iptvDao.getSearchHistoryFlow()
    
    suspend fun clearSearchHistory() = iptvDao.clearSearchHistory()

    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>> = iptvDao.getAllDownloadsFlow()
    
    suspend fun getDownload(streamId: Int, type: String? = null): DownloadEntity? {
        return if (!type.isNullOrBlank()) iptvDao.getDownload(streamId, type)
               else iptvDao.getDownloadByStreamId(streamId)
    }
    
    suspend fun insertDownload(download: DownloadEntity) = iptvDao.insertDownload(download)
    
    suspend fun deleteDownload(streamId: Int, type: String? = null) {
        if (!type.isNullOrBlank()) iptvDao.deleteDownload(streamId, type)
        else iptvDao.deleteDownloadByStreamId(streamId)
    }

    suspend fun insertPendingSync(pending: PendingSyncEntity) = iptvDao.insertPendingSync(pending)
    
    suspend fun getAllPendingSyncs(): List<PendingSyncEntity> = iptvDao.getAllPendingSyncs()
    
    suspend fun deletePendingSync(type: String, providerHash: String, contentId: String) = 
        iptvDao.deletePendingSync(type, providerHash, contentId)
}
