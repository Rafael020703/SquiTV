package rsv.squitv.domain.usecase

import android.content.Context
import androidx.work.*
import timber.log.Timber
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.core.domain.interactor.AuthManager
import rsv.squitv.core.domain.interactor.DnsManager
import rsv.squitv.core.domain.state.AppSyncProgress
import rsv.squitv.core.domain.state.AppSyncStatus
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.repository.FirebaseRepository
import rsv.squitv.core.data.mapper.toIptvItem
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.SessionStatus
import rsv.squitv.worker.SyncWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID

class SyncDataUseCase @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val epgRepository: EpgRepository,
    private val dnsManager: DnsManager,
    private val authManager: AuthManager,
    private val settingsRepository: SettingsRepository,
    private val firebaseRepository: FirebaseRepository,
    @ApplicationContext private val context: Context
) {
    // Internal Result that matches AppSyncProgress fields plus orchestrator specific info
    data class SyncResult(
        val accountStatus: AppSyncStatus = AppSyncStatus.Pending,
        val liveStatus: AppSyncStatus = AppSyncStatus.Pending,
        val vodStatus: AppSyncStatus = AppSyncStatus.Pending,
        val seriesStatus: AppSyncStatus = AppSyncStatus.Pending,
        val isContentReady: Boolean = false,
        val sessionStatus: SessionStatus = SessionStatus.UNKNOWN,
        val isComplete: Boolean = false,
        val error: String? = null,
        val workId: UUID? = null
    )

    fun startBackgroundSync(
        force: Boolean = false,
        syncLive: Boolean = true,
        syncVod: Boolean = true,
        syncSeries: Boolean = true
    ): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                "force" to force,
                "syncLive" to syncLive,
                "syncVod" to syncVod,
                "syncSeries" to syncSeries
            ))
            .addTag("sync_work")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync_data_work",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        return workRequest.id
    }

    suspend operator fun invoke(
        force: Boolean = false,
        syncLive: Boolean = true,
        syncVod: Boolean = true,
        syncSeries: Boolean = true,
        fastLogin: Boolean = false
    ): Flow<SyncResult> = flow {
        val settings = settingsRepository.settingsFlow.first()
        val creds = settings.credentials ?: return@flow
        
        Timber.d("SyncUseCase: STARTING (force=$force)")
        var currentResult = SyncResult(accountStatus = AppSyncStatus.Fetching)
        emit(currentResult)

        try {
            dnsManager.updateBaseUrl(creds.baseUrl)
            try {
                Timber.d("SyncUseCase: Authenticating...")
                val loginResponse = authManager.login(creds)
                loginResponse.userInfo?.let { info ->
                    settingsRepository.updateAccountCache(info)
                    
                    // Check for expiration
                    val expDate = info.expDate?.toLongOrNull() ?: 0L
                    val now = System.currentTimeMillis() / 1000
                    if (expDate in 1..now) {
                        currentResult = currentResult.copy(
                            accountStatus = AppSyncStatus.Success, 
                            sessionStatus = SessionStatus.EXPIRED
                        )
                    } else {
                        currentResult = currentResult.copy(
                            accountStatus = AppSyncStatus.Success, 
                            sessionStatus = SessionStatus.VALID
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "SyncUseCase: IPTV Login failed, checking local data for offline mode")
                val stats = catalogRepository.getStats()
                val hasData = stats.first > 0 || stats.second > 0 || stats.third > 0
                if (hasData) {
                    currentResult = currentResult.copy(
                        accountStatus = AppSyncStatus.OfflineMode, 
                        sessionStatus = SessionStatus.OFFLINE
                    )
                } else {
                    currentResult = currentResult.copy(
                        sessionStatus = SessionStatus.INVALID
                    )
                    throw e // No local data and no network = fail
                }
            }
            
            // Re-check content readiness after login attempt/cache check
            val stats = catalogRepository.getStats()
            currentResult = currentResult.copy(isContentReady = stats.first > 0 || stats.second > 0 || stats.third > 0)
            Timber.d("SyncUseCase: Pre-sync check - isContentReady=${currentResult.isContentReady} (stats=$stats)")
            emit(currentResult)
            
            if (fastLogin) {
                Timber.i("SyncUseCase: FastLogin mode. Emitting readiness.")
                emit(currentResult) 
                performFullSyncInternal(creds, force, syncLive, syncVod, syncSeries, settings).collect { 
                    emit(it)
                }
            } else {
                performFullSyncInternal(creds, force, syncLive, syncVod, syncSeries, settings).collect { 
                    emit(it)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncUseCase: Failure")
            emit(currentResult.copy(error = e.message, isComplete = true))
        }
    }

    private suspend fun performFullSyncInternal(
        creds: XtreamCredentials,
        force: Boolean,
        syncLive: Boolean,
        syncVod: Boolean,
        syncSeries: Boolean,
        settings: SettingsRepository.AppSettings
    ): Flow<SyncResult> = flow {
        val now = System.currentTimeMillis()
        val intervalMillis = settings.syncIntervalHours * 3600 * 1000L
        
        val shouldSyncLive = force || (syncLive && (now - settings.lastSyncLive > intervalMillis))
        val shouldSyncVod = force || (syncVod && (now - settings.lastSyncVod > intervalMillis))
        val shouldSyncSeries = force || (syncSeries && (now - settings.lastSyncSeries > intervalMillis))

        var currentResult = SyncResult(
            accountStatus = AppSyncStatus.Success, // Preserve success status
            liveStatus = if (shouldSyncLive) AppSyncStatus.Pending else AppSyncStatus.Success,
            vodStatus = if (shouldSyncVod) AppSyncStatus.Pending else AppSyncStatus.Success,
            seriesStatus = if (shouldSyncSeries) AppSyncStatus.Pending else AppSyncStatus.Success,
            isContentReady = true, // We already checked this in the caller or login
            sessionStatus = SessionStatus.VALID // Assume valid as we reached this point
        )

        if (!shouldSyncLive && !shouldSyncVod && !shouldSyncSeries && !force) {
            Timber.i("SyncUseCase: Local data is already fresh. Skipping download.")
            // Mark the entire sync process as successful for the session
            settingsRepository.updateLastSyncTimestamp(System.currentTimeMillis())
            emit(currentResult.copy(isComplete = true))
            return@flow
        }

        // 1. SYNC IPTV (Local Database ONLY)
        if (shouldSyncLive) {
            try {
                currentResult = currentResult.copy(liveStatus = AppSyncStatus.Syncing)
                emit(currentResult)
                catalogRepository.getLiveCategories(creds, forceRefresh = true)
                val streams = catalogRepository.getLiveStreams(creds, forceRefresh = true)
                settingsRepository.updateSyncState("live", true)
                currentResult = currentResult.copy(liveStatus = AppSyncStatus.Done(streams.size))
                emit(currentResult)
            } catch (e: Exception) {
                currentResult = currentResult.copy(liveStatus = AppSyncStatus.Error(e.message ?: ""))
                emit(currentResult)
            }
        }

        if (shouldSyncVod) {
            try {
                currentResult = currentResult.copy(vodStatus = AppSyncStatus.Syncing)
                emit(currentResult)
                catalogRepository.getVodCategories(creds, forceRefresh = true)
                val streams = catalogRepository.getVodStreams(creds, forceRefresh = true)
                settingsRepository.updateSyncState("vod", true)
                currentResult = currentResult.copy(vodStatus = AppSyncStatus.Done(streams.size))
                emit(currentResult)
            } catch (e: Exception) {
                currentResult = currentResult.copy(vodStatus = AppSyncStatus.Error(e.message ?: ""))
                emit(currentResult)
            }
        }

        if (shouldSyncSeries) {
            try {
                currentResult = currentResult.copy(seriesStatus = AppSyncStatus.Syncing)
                emit(currentResult)
                catalogRepository.getSeriesCategories(creds, forceRefresh = true)
                val series = catalogRepository.getSeries(creds, forceRefresh = true)
                settingsRepository.updateSyncState("series", true)
                currentResult = currentResult.copy(seriesStatus = AppSyncStatus.Done(series.size))
                emit(currentResult)
            } catch (e: Exception) {
                currentResult = currentResult.copy(seriesStatus = AppSyncStatus.Error(e.message ?: ""))
                emit(currentResult)
            }
        }

        if (shouldSyncLive) {
            try { epgRepository.syncFullEpg(creds) } catch (_: Exception) {}
        }

        // 2. PERSIST SUCCESS TIMESTAMP BEFORE EMITTING COMPLETION IF NO ERRORS
        val hasCategoryError = currentResult.liveStatus is AppSyncStatus.Error || 
                               currentResult.vodStatus is AppSyncStatus.Error || 
                               currentResult.seriesStatus is AppSyncStatus.Error

        val errorSummary = if (hasCategoryError) {
            val errors = listOfNotNull(
                (currentResult.liveStatus as? AppSyncStatus.Error)?.message?.let { "Ao vivo: $it" },
                (currentResult.vodStatus as? AppSyncStatus.Error)?.message?.let { "Filmes: $it" },
                (currentResult.seriesStatus as? AppSyncStatus.Error)?.message?.let { "Séries: $it" }
            )
            errors.joinToString("; ")
        } else null

        if (currentResult.sessionStatus == SessionStatus.VALID && !hasCategoryError) {
            settingsRepository.updateLastSyncTimestamp(System.currentTimeMillis())
        }

        // 3. EMIT COMPLETE FOR UI (The catalog is now local)
        val finalResult = currentResult.copy(
            isComplete = true,
            error = errorSummary
        )
        emit(finalResult)
    }
}
