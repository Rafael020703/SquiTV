@file:Suppress("UnsafeOptInUsageError")
@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package rsv.squitv.core.domain.interactor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.data.local.dao.IptvDao
import rsv.squitv.data.local.entities.CatalogMetadataEntity
import rsv.squitv.data.local.entities.CatalogMetadataUpdate
import rsv.squitv.shared.catalog.PreparationBatchPolicy
import rsv.squitv.shared.catalog.PreparationProgress
import rsv.squitv.domain.usecase.GenreUtils
import rsv.squitv.data.model.SettingsBackupCodec
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.model.PreparationSpeed
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

enum class PauseReason {
    USER_PAUSED,
    PLAYBACK_ACTIVE,
    NETWORK_UNAVAILABLE,
    APP_BACKGROUND,
    SYNCING_OR_DOWNLOADING
}

data class CatalogPreparationState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isCooldown: Boolean = false,
    val pauseReason: PauseReason? = null,
    val priorityType: String = "VOD", // VOD or SERIES
    val currentItemType: String? = null,
    val currentItemName: String? = null,
    val speed: PreparationSpeed = PreparationSpeed.NORMAL,
    val vodTotal: Int = 0,
    val vodProcessed: Int = 0,
    val vodWithGenre: Int = 0,
    val seriesTotal: Int = 0,
    val seriesProcessed: Int = 0,
    val seriesWithGenre: Int = 0,
    val totalItems: Int = 0,
    val processedItems: Int = 0,
    val withGenreItems: Int = 0,
    val noDataItems: Int = 0,
    val failedItems: Int = 0,
    val inFlight: Int = 0,
    val estimatedRemainingMs: Long? = null,
    val waitingForRetry: Boolean = false,
    val isThrottled: Boolean = false,
    val error: String? = null
)

@UnstableApi
@Singleton
class CatalogPreparationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val iptvDao: IptvDao,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackManager: PlaybackManager
) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow(CatalogPreparationState())
    val state: StateFlow<CatalogPreparationState> = _state.asStateFlow()

    private var userPaused = false
    private var isAppInForeground = true
    private var isNetworkConnected = true
    private var isPlaybackActive = false
    private var activeScreenType = "VOD" // VOD or SERIES
    private var job: Job? = null
    private var generation = 0L

    init {
        isNetworkConnected = isNetworkAvailable()
        registerNetworkCallback()
        observePlaybackState()
        observeSettings()
    }

    fun setActiveContentType(type: String) {
        val normType = if (type.equals("series", ignoreCase = true)) "SERIES" else "VOD"
        val restartIdle = !_state.value.isRunning && !_state.value.isPaused
        if (activeScreenType != normType || restartIdle) {
            activeScreenType = normType
            _state.value = _state.value.copy(priorityType = normType)
            evaluateAndTrigger(restart = restartIdle)
        }
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        isAppInForeground = inForeground
        evaluateAndTrigger()
    }

    fun requestStartOrResume() {
        userPaused = false
        evaluateAndTrigger()
    }

    fun requestPause() {
        userPaused = true
        evaluateAndTrigger()
    }

    fun retryFailed() {
        scope.launch {
          try {
            val settings = settingsRepository.settingsFlow.first()
            val credentials = settings.credentials ?: return@launch
            val accountKey = SettingsBackupCodec.accountKey(credentials)
            val token = ++generation
            job?.cancelAndJoin()
            iptvDao.retryFailedMetadata(accountKey)
            if (token != generation || settingsRepository.settingsFlow.first().credentials != credentials) return@launch
            userPaused = false
            evaluateAndTrigger(restart = true)
          } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { _state.value = _state.value.copy(error = "Não foi possível reagendar as falhas. Tente novamente.") }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { isNetworkConnected = isNetworkAvailable(); evaluateAndTrigger() }
            }

            override fun onLost(network: Network) {
                scope.launch { isNetworkConnected = isNetworkAvailable(); evaluateAndTrigger() }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                scope.launch { isNetworkConnected = isNetworkAvailable(); evaluateAndTrigger() }
            }
        })
    }

    private fun observePlaybackState() {
        scope.launch {
            playbackManager.isPlaybackActive.collect { active ->
                isPlaybackActive = active
                evaluateAndTrigger()
            }
        }
    }

    private fun observeSettings() {
        scope.launch {
            var previousAccount: XtreamCredentials? = null
            settingsRepository.settingsFlow.map { it.credentials to PreparationSpeed.fromString(it.preparationSpeed) }
                .distinctUntilChanged().collect { (credentials, speed) ->
                if (credentials != previousAccount) _state.value = CatalogPreparationState(speed = speed)
                previousAccount = credentials
                _state.value = _state.value.copy(speed = speed, estimatedRemainingMs = null)
                evaluateAndTrigger(restart = true)
            }
        }
    }

    private fun evaluateAndTrigger(restart: Boolean = false) {
        val pauseReason = when {
            userPaused -> PauseReason.USER_PAUSED
            !isAppInForeground -> PauseReason.APP_BACKGROUND
            !isNetworkConnected -> PauseReason.NETWORK_UNAVAILABLE
            isPlaybackActive -> PauseReason.PLAYBACK_ACTIVE
            else -> null
        }

        if (pauseReason != null) {
            job?.cancel()
            generation++
            _state.value = _state.value.copy(
                isRunning = false,
                isPaused = true,
                isCooldown = false,
                pauseReason = pauseReason,
                currentItemName = null,
                currentItemType = null, inFlight = 0, estimatedRemainingMs = null
            )
        } else {
            _state.value = _state.value.copy(
                isRunning = true,
                isPaused = false,
                pauseReason = null,
                priorityType = activeScreenType
            )
            if (restart || job?.isActive != true) {
                val previous = job
                previous?.cancel()
                val token = ++generation
                job = scope.launch {
                    previous?.join()
                    if (token != generation) return@launch
                    try { runPreparationLoop(token) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        if (token == generation) _state.value = _state.value.copy(error = "Não foi possível preparar o catálogo. Tente retomar.", estimatedRemainingMs = null)
                    } finally {
                        if (token == generation) _state.value = _state.value.copy(isRunning = false, inFlight = 0)
                    }
                }
            }
        }
    }

    private suspend fun runPreparationLoop(token: Long) {
        val estimator = PreparationProgress()
        val policy = PreparationBatchPolicy()
        var seededAt = 0L
        var queuePriority = ""
        val queue = java.util.ArrayDeque<CatalogMetadataEntity>()
        val credentials = settingsRepository.settingsFlow.first().credentials ?: return
        val accountKey = SettingsBackupCodec.accountKey(credentials)
        while (currentCoroutineContext().isActive && token == generation) {
            if (settingsRepository.settingsFlow.first().credentials != credentials) return
            val speed = _state.value.speed
            if (seededAt == 0L || System.nanoTime() - seededAt >= 60_000_000_000L) {
                seedCacheFromStreams(accountKey)
                seededAt = System.nanoTime()
                updateStats(accountKey, estimator)
                queue.clear()
            }
            val primary = activeScreenType
            val now = System.currentTimeMillis()
            val limit = policy.size(speed)
            if (queuePriority != primary) { queue.clear(); queuePriority = primary }
            if (queue.isEmpty()) queue.addAll(iptvDao.getPendingMetadataItems(accountKey, primary, now, 50).ifEmpty {
                iptvDao.getPendingMetadataItems(accountKey, if (primary == "VOD") "SERIES" else "VOD", now, 50)
            })
            val candidates = buildList {
                repeat(minOf(limit, queue.size)) { add(queue.removeFirst()) }
            }
            if (candidates.isEmpty()) {
                updateStats(accountKey, estimator)
                _state.value = _state.value.copy(isRunning = false, isCooldown = false, inFlight = 0,
                    currentItemName = null, currentItemType = null, estimatedRemainingMs = null,
                    waitingForRetry = _state.value.processedItems < _state.value.totalItems)
                delay(15_000L)
                continue
            }
            val started = System.nanoTime()
            val names = iptvDao.getStreamsByIds(candidates.map { it.streamId })
            val first = candidates.first()
            _state.value = _state.value.copy(isRunning = true, isCooldown = false, waitingForRetry = false,
                error = null, inFlight = candidates.size, currentItemType = first.streamType,
                currentItemName = names.firstOrNull { it.id == first.streamId &&
                    (it.streamType == first.streamType || first.streamType == "VOD" && it.streamType == "MOVIE") }?.name)
            val results = coroutineScope {
                candidates.map { candidate -> async { processCandidate(credentials, candidate) } }.awaitAll()
            }
            currentCoroutineContext().ensureActive()
            if (token != generation || settingsRepository.settingsFlow.first().credentials != credentials) return
            iptvDao.applyPreparationBatch(results.map { it.first }, results.mapNotNull { it.second })
            val interval = policy.complete(speed, results.any { it.first.status == CatalogMetadataEntity.STATUS_FAILED_TEMPORARY })
            estimator.record(results.count { it.first.status != CatalogMetadataEntity.STATUS_FAILED_TEMPORARY },
                (System.nanoTime() - started) / 1_000_000L, interval)
            val oldRows = candidates.associateBy { it.streamType to it.streamId }
            val resultRows = results.map { it.first }
            fun resolved(row: CatalogMetadataEntity) = row.status == CatalogMetadataEntity.STATUS_SUCCESS ||
                row.status == CatalogMetadataEntity.STATUS_NO_DATA && row.nextEligibleAt > now
            fun delta(type: String?, predicate: (CatalogMetadataEntity) -> Boolean) = resultRows
                .filter { type == null || it.streamType == type }.sumOf { row ->
                    (if (predicate(row)) 1 else 0) - (if (predicate(oldRows.getValue(row.streamType to row.streamId))) 1 else 0)
                }
            val old = _state.value
            val done = old.processedItems + delta(null, ::resolved)
            val failed = old.failedItems + delta(null) { it.status == CatalogMetadataEntity.STATUS_FAILED_TEMPORARY }
            _state.value = old.copy(processedItems = done, failedItems = failed,
                vodProcessed = old.vodProcessed + delta("VOD", ::resolved), seriesProcessed = old.seriesProcessed + delta("SERIES", ::resolved),
                vodWithGenre = old.vodWithGenre + delta("VOD") { it.status == CatalogMetadataEntity.STATUS_SUCCESS },
                seriesWithGenre = old.seriesWithGenre + delta("SERIES") { it.status == CatalogMetadataEntity.STATUS_SUCCESS },
                withGenreItems = old.withGenreItems + delta(null) { it.status == CatalogMetadataEntity.STATUS_SUCCESS },
                noDataItems = old.noDataItems + delta(null) { it.status == CatalogMetadataEntity.STATUS_NO_DATA && resolved(it) },
                estimatedRemainingMs = estimator.remainingMs(old.totalItems, done, failed))
            _state.value = _state.value.copy(inFlight = 0, isCooldown = true,
                isThrottled = interval > speed.batchCooldownMs, currentItemName = null, currentItemType = null)
            delay(interval)
        }
    }

    private suspend fun seedCacheFromStreams(accountKey: String): Unit = withContext(Dispatchers.Default) {
        try {
            val existingCache = iptvDao.getMetadataKeys(accountKey)
            val cachedKeys = existingCache.map { "${it.streamType}_${it.streamId}" }.toSet()

            val allStreams = iptvDao.getMetadataSeedStreams()
            val newEntries = mutableListOf<CatalogMetadataEntity>()

            for (stream in allStreams) {
                val type = if (stream.streamType.equals("SERIES", ignoreCase = true)) "SERIES" else "VOD"
                val key = "${type}_${stream.id}"
                if (key !in cachedKeys) {
                    val hasGenre = GenreUtils.extractNormalizedGenres(stream.genre).isNotEmpty()
                    newEntries.add(
                        CatalogMetadataEntity(
                            accountKey = accountKey,
                            streamType = type,
                            streamId = stream.id,
                            status = if (hasGenre) CatalogMetadataEntity.STATUS_SUCCESS else CatalogMetadataEntity.STATUS_PENDING,
                            genre = stream.genre?.takeIf { hasGenre }
                        )
                    )
                }
            }

            currentCoroutineContext().ensureActive()
            if (settingsRepository.settingsFlow.first().credentials?.let(SettingsBackupCodec::accountKey) != accountKey) return@withContext
            if (newEntries.isNotEmpty()) {
                iptvDao.insertOrUpdateMetadataCacheList(newEntries)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Timber.e(e, "Error seeding metadata cache")
            throw e
        }
    }

    private suspend fun processCandidate(credentials: XtreamCredentials, candidate: CatalogMetadataEntity):
        Pair<CatalogMetadataEntity, CatalogMetadataUpdate?> {
        try {
            val detail = withTimeoutOrNull(45_000L) {
                catalogRepository.fetchPreparationMetadata(credentials, candidate.streamId, candidate.streamType == "SERIES")
            } ?: throw java.io.IOException("Tempo limite ao consultar metadados")
            currentCoroutineContext().ensureActive()
            val now = System.currentTimeMillis()
            val genre = detail.genre?.take(4096)?.takeIf { GenreUtils.extractNormalizedGenres(it).isNotEmpty() }
            val row = candidate.copy(status = if (genre == null) CatalogMetadataEntity.STATUS_NO_DATA else CatalogMetadataEntity.STATUS_SUCCESS,
                genre = genre, lastAttempt = now, lastSuccess = if (genre != null) now else candidate.lastSuccess,
                nextEligibleAt = if (genre == null) now + 7L * 86400_000L else 0,
                attempts = 0, errorMessage = null)
            return row to detail.copy(genre = genre)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            val now = System.currentTimeMillis()
            val attempts = candidate.attempts + 1
            return candidate.copy(status = CatalogMetadataEntity.STATUS_FAILED_TEMPORARY,
                lastAttempt = now, nextEligibleAt = now + calculateBackoffMs(attempts), attempts = attempts,
                errorMessage = "Não foi possível consultar os metadados") to null
        }
    }

    private fun calculateBackoffMs(attempts: Int): Long {
        val base = min((1L shl attempts.coerceAtMost(10)) * 60_000L, 24 * 3600_000L)
        val jitter = (0..5000).random().toLong()
        return base + jitter
    }

    private suspend fun updateStats(accountKey: String, estimator: PreparationProgress) {
        val rows = iptvDao.getMetadataProgress(accountKey, System.currentTimeMillis())
        if (settingsRepository.settingsFlow.first().credentials?.let(SettingsBackupCodec::accountKey) != accountKey) return
        fun count(type: String, status: String? = null) = rows.filter {
            it.streamType == type && (status == null || it.status == status)
        }.sumOf { it.itemCount }
        val vodTotal = count("VOD")
        val seriesTotal = count("SERIES")
        val vodSuccess = count("VOD", CatalogMetadataEntity.STATUS_SUCCESS)
        val seriesSuccess = count("SERIES", CatalogMetadataEntity.STATUS_SUCCESS)
        val vodNoData = count("VOD", CatalogMetadataEntity.STATUS_NO_DATA)
        val seriesNoData = count("SERIES", CatalogMetadataEntity.STATUS_NO_DATA)
        val failed = count("VOD", CatalogMetadataEntity.STATUS_FAILED_TEMPORARY) + count("SERIES", CatalogMetadataEntity.STATUS_FAILED_TEMPORARY)
        val done = vodSuccess + seriesSuccess + vodNoData + seriesNoData
        _state.update { it.copy(vodTotal = vodTotal, seriesTotal = seriesTotal,
            vodProcessed = vodSuccess + vodNoData, seriesProcessed = seriesSuccess + seriesNoData,
            vodWithGenre = vodSuccess, seriesWithGenre = seriesSuccess, totalItems = vodTotal + seriesTotal,
            processedItems = done, withGenreItems = vodSuccess + seriesSuccess,
            noDataItems = vodNoData + seriesNoData, failedItems = failed,
            estimatedRemainingMs = estimator.remainingMs(vodTotal + seriesTotal, done, failed)) }
    }
}
