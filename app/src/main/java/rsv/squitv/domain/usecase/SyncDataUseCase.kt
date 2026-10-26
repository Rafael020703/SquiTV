package rsv.squitv.domain.usecase

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.core.domain.interactor.AuthManager
import rsv.squitv.core.domain.interactor.DnsManager
import rsv.squitv.core.domain.state.AppSyncStatus
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.model.SessionStatus
import rsv.squitv.worker.SyncWorker
import timber.log.Timber

/** One coordinator for UI, DNS changes and WorkManager. Downloads precede Room publication. */
@Singleton
class SyncDataUseCase @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val epgRepository: EpgRepository,
    private val dnsManager: DnsManager,
    private val authManager: AuthManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    data class SyncResult(
        val accountStatus: AppSyncStatus = AppSyncStatus.Pending,
        val liveStatus: AppSyncStatus = AppSyncStatus.Skipped,
        val vodStatus: AppSyncStatus = AppSyncStatus.Skipped,
        val seriesStatus: AppSyncStatus = AppSyncStatus.Skipped,
        val epgStatus: AppSyncStatus = AppSyncStatus.Skipped,
        val isContentReady: Boolean = false,
        val sessionStatus: SessionStatus = SessionStatus.UNKNOWN,
        val isComplete: Boolean = false,
        val error: String? = null,
        val retryable: Boolean = false,
        val isCancelled: Boolean = false,
        val operationId: String = UUID.randomUUID().toString()
    )

    private val mutex = Mutex()
    private val _progress = MutableStateFlow<SyncResult?>(null)
    val progress: StateFlow<SyncResult?> = _progress.asStateFlow()
    private val _catalogRevision = MutableStateFlow(0L)
    val catalogRevision: StateFlow<Long> = _catalogRevision.asStateFlow()

    fun startBackgroundSync(force: Boolean = false, syncLive: Boolean = true,
        syncVod: Boolean = true, syncSeries: Boolean = true): UUID {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("force" to force, "syncLive" to syncLive,
                "syncVod" to syncVod, "syncSeries" to syncSeries))
            .addTag("sync_work").build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync_data_work", ExistingWorkPolicy.KEEP, request)
        return request.id
    }

    suspend operator fun invoke(force: Boolean = false, syncLive: Boolean = true,
        syncVod: Boolean = true, syncSeries: Boolean = true,
        @Suppress("UNUSED_PARAMETER") fastLogin: Boolean = false): Flow<SyncResult> = flow {
        mutex.withLock {
            try {
                runSync(force, syncLive, syncVod, syncSeries)
                    .onEach { _progress.value = it }.collect { emit(it) }
            } catch (cancelled: CancellationException) {
                _progress.value = _progress.value?.copy(isComplete = true, isCancelled = true)
                throw cancelled
            }
        }
    }

    /** Reset shares the publication lock, so an old download cannot repopulate the database. */
    suspend fun clearCatalogData() = mutex.withLock {
        settingsRepository.clearSyncTimestamps()
        catalogRepository.clearCatalogData()
        _catalogRevision.value += 1
    }

    private suspend fun ensureAccountCurrent(credentials: XtreamCredentials) {
        currentCoroutineContext().ensureActive()
        if (settingsRepository.settingsFlow.first().credentials != credentials) {
            throw CancellationException("A conta foi alterada durante a sincronização")
        }
    }

    private fun runSync(force: Boolean, syncLive: Boolean, syncVod: Boolean, syncSeries: Boolean): Flow<SyncResult> = flow {
        var result = SyncResult(accountStatus = AppSyncStatus.Fetching)
        emit(result)
        val settings = settingsRepository.settingsFlow.first()
        val credentials = settings.credentials
        if (credentials == null) {
            emit(result.copy(accountStatus = AppSyncStatus.Error("Entre na sua conta para sincronizar"),
                sessionStatus = SessionStatus.INVALID, error = "Nenhuma conta configurada", isComplete = true))
            return@flow
        }
        val stats = catalogRepository.getStats()
        result = result.copy(isContentReady = stats.first + stats.second + stats.third > 0)
        dnsManager.updateBaseUrl(credentials.baseUrl)
        val login = try {
            authManager.login(credentials)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val retryable = isRetryable(failure)
            authManager.setSessionStatus(if (retryable) SessionStatus.OFFLINE else SessionStatus.INVALID)
            emit(result.copy(accountStatus = if (retryable) AppSyncStatus.OfflineMode else AppSyncStatus.Error("Falha na autenticação"),
                sessionStatus = if (retryable) SessionStatus.OFFLINE else SessionStatus.INVALID,
                error = if (retryable) "Sem conexão com o servidor. O catálogo anterior foi preservado." else "Não foi possível autenticar a conta",
                retryable = retryable, isComplete = true))
            return@flow
        }
        ensureAccountCurrent(credentials)
        val info = login.userInfo
        val session = when {
            info?.auth != 1 -> SessionStatus.INVALID
            info.status.equals("Expired", true) || (info.expDate?.toLongOrNull() ?: 0L) in 1..(System.currentTimeMillis() / 1000) -> SessionStatus.EXPIRED
            info.status.equals("Banned", true) || info.status.equals("Disabled", true) -> SessionStatus.INVALID
            else -> SessionStatus.VALID
        }
        authManager.setSessionStatus(session)
        if (session != SessionStatus.VALID) {
            val message = if (session == SessionStatus.EXPIRED) "Sua conta expirou" else "O servidor recusou a autenticação"
            emit(result.copy(accountStatus = AppSyncStatus.Error(message), sessionStatus = session,
                error = message, isComplete = true))
            return@flow
        }
        settingsRepository.updateAccountCache(requireNotNull(info))
        result = result.copy(accountStatus = AppSyncStatus.Success, sessionStatus = session)
        val interval = settings.syncIntervalHours.coerceAtLeast(1) * 3_600_000L
        val now = System.currentTimeMillis()
        fun due(enabled: Boolean, last: Long) = enabled && (force || settings.lastSyncTimestamp == 0L || now - last >= interval)
        val live = due(syncLive, settings.lastSyncLive)
        val vod = due(syncVod, settings.lastSyncVod)
        val series = due(syncSeries, settings.lastSyncSeries)
        val epg = due(syncLive, settings.lastSyncEpg)
        result = result.copy(
            liveStatus = if (live) AppSyncStatus.Pending else AppSyncStatus.Skipped,
            vodStatus = if (vod) AppSyncStatus.Pending else AppSyncStatus.Skipped,
            seriesStatus = if (series) AppSyncStatus.Pending else AppSyncStatus.Skipped,
            epgStatus = if (epg) AppSyncStatus.Pending else AppSyncStatus.Skipped
        )
        emit(result)
        fun withStatus(type: String, status: AppSyncStatus): SyncResult = when (type) {
            "LIVE" -> result.copy(liveStatus = status)
            "VOD" -> result.copy(vodStatus = status)
            else -> result.copy(seriesStatus = status)
        }
        for ((type, enabled) in listOf("LIVE" to live, "VOD" to vod, "SERIES" to series)) {
            if (!enabled) continue
            result = withStatus(type, AppSyncStatus.Syncing)
            emit(result)
            val status = try {
                val count = catalogRepository.syncCatalogType(credentials, type) { ensureAccountCurrent(credentials) }
                _catalogRevision.value += 1
                ensureAccountCurrent(credentials)
                settingsRepository.updateSyncState(type, true)
                AppSyncStatus.Done(count)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.w(failure, "Catalog sync failed for %s", type)
                result = result.copy(retryable = result.retryable || isRetryable(failure))
                AppSyncStatus.Error("Não foi possível concluir esta etapa")
            }
            result = withStatus(type, status)
            emit(result)
        }
        if (epg && result.liveStatus !is AppSyncStatus.Error) {
            result = result.copy(epgStatus = AppSyncStatus.Syncing)
            emit(result)
            val status = try {
                val count = epgRepository.syncFullEpg(credentials) { ensureAccountCurrent(credentials) }
                ensureAccountCurrent(credentials)
                settingsRepository.updateSyncState("epg", true)
                AppSyncStatus.Done(count)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.w(failure, "EPG sync failed")
                result = result.copy(retryable = result.retryable || isRetryable(failure))
                AppSyncStatus.Error("Não foi possível atualizar o guia de programação")
            }
            result = result.copy(epgStatus = status)
            emit(result)
        } else if (epg) {
            result = result.copy(epgStatus = AppSyncStatus.Skipped)
        }
        ensureAccountCurrent(credentials)
        val errors = listOf("TV ao vivo" to result.liveStatus, "Filmes" to result.vodStatus,
            "Séries" to result.seriesStatus, "EPG" to result.epgStatus)
            .mapNotNull { (name, status) -> (status as? AppSyncStatus.Error)?.let { "$name: ${it.message}" } }
        val currentStats = catalogRepository.getStats()
        if (errors.isEmpty()) settingsRepository.updateLastSyncTimestamp(System.currentTimeMillis())
        emit(result.copy(isComplete = true, error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            isContentReady = currentStats.first + currentStats.second + currentStats.third > 0))
    }.catch { failure ->
        // Account switches can cancel this run even while the collecting job is still active.
        if (failure is CancellationException) throw failure
        Timber.e(failure, "Sync failed")
        val previous = _progress.value ?: SyncResult()
        emit(previous.copy(isComplete = true, error = "Não foi possível concluir a sincronização",
            retryable = isRetryable(failure)))
    }

    companion object {
        fun isRetryable(failure: Throwable): Boolean = failure is IOException ||
            (failure is HttpException && (failure.code() == 429 || failure.code() >= 500))
    }
}
