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
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.data.local.dao.IptvDao
import rsv.squitv.data.local.entities.CatalogMetadataEntity
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
    val failedItems: Int = 0
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

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(CatalogPreparationState())
    val state: StateFlow<CatalogPreparationState> = _state.asStateFlow()

    private var userPaused = false
    private var isAppInForeground = true
    private var isNetworkConnected = true
    private var isPlaybackActive = false
    private var activeScreenType = "VOD" // VOD or SERIES
    private var job: Job? = null

    init {
        isNetworkConnected = isNetworkAvailable()
        registerNetworkCallback()
        observePlaybackState()
        observeSettings()
    }

    fun setActiveContentType(type: String) {
        val normType = if (type.equals("series", ignoreCase = true)) "SERIES" else "VOD"
        if (activeScreenType != normType) {
            activeScreenType = normType
            _state.value = _state.value.copy(priorityType = normType)
            evaluateAndTrigger()
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
        scope.launch(Dispatchers.IO) {
            val settings = settingsRepository.settingsFlow.first()
            val credentials = settings.credentials ?: return@launch
            val accountKey = SettingsBackupCodec.accountKey(credentials)
            val failed = iptvDao.getPendingMetadataItems(accountKey, "VOD", System.currentTimeMillis() + 864000000L, 1000) +
                    iptvDao.getPendingMetadataItems(accountKey, "SERIES", System.currentTimeMillis() + 864000000L, 1000)
            val resetList = failed.map { it.copy(status = CatalogMetadataEntity.STATUS_PENDING, attempts = 0, nextEligibleAt = 0L) }
            if (resetList.isNotEmpty()) {
                iptvDao.insertOrUpdateMetadataCacheList(resetList)
            }
            userPaused = false
            evaluateAndTrigger()
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
                isNetworkConnected = isNetworkAvailable()
                evaluateAndTrigger()
            }

            override fun onLost(network: Network) {
                isNetworkConnected = isNetworkAvailable()
                evaluateAndTrigger()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                isNetworkConnected = isNetworkAvailable()
                evaluateAndTrigger()
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
            settingsRepository.settingsFlow.collect { settings ->
                val speed = PreparationSpeed.fromString(settings.preparationSpeed)
                _state.value = _state.value.copy(speed = speed)
            }
        }
    }

    private fun evaluateAndTrigger() {
        val pauseReason = when {
            userPaused -> PauseReason.USER_PAUSED
            !isAppInForeground -> PauseReason.APP_BACKGROUND
            !isNetworkConnected -> PauseReason.NETWORK_UNAVAILABLE
            isPlaybackActive -> PauseReason.PLAYBACK_ACTIVE
            else -> null
        }

        if (pauseReason != null) {
            job?.cancel()
            job = null
            _state.value = _state.value.copy(
                isRunning = false,
                isPaused = true,
                isCooldown = false,
                pauseReason = pauseReason,
                currentItemName = null,
                currentItemType = null
            )
        } else {
            _state.value = _state.value.copy(
                isRunning = true,
                isPaused = false,
                pauseReason = null,
                priorityType = activeScreenType
            )
            if (job?.isActive != true) {
                job = scope.launch(Dispatchers.IO) {
                    runPreparationLoop()
                }
            }
        }
    }

    private suspend fun runPreparationLoop() {
        while (currentCoroutineContext().isActive) {
            val settings = settingsRepository.settingsFlow.first()
            val credentials = settings.credentials ?: break
            val accountKey = SettingsBackupCodec.accountKey(credentials)
            val speed = PreparationSpeed.fromString(settings.preparationSpeed)

            // 1. Seed cache from Room if new streams exist
            seedCacheFromStreams(accountKey)

            // 2. Update stats
            updateStats(accountKey)

            // 3. Query candidate queue prioritised by activeScreenType and ordered by name ASC
            val now = System.currentTimeMillis()
            val primaryType = activeScreenType
            val secondaryType = if (primaryType == "VOD") "SERIES" else "VOD"

            var candidates = iptvDao.getPendingMetadataItems(accountKey, primaryType, now, 10)
            if (candidates.isEmpty()) {
                candidates = iptvDao.getPendingMetadataItems(accountKey, secondaryType, now, 10)
            }

            if (candidates.isEmpty()) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    isPaused = false,
                    isCooldown = false,
                    pauseReason = null,
                    currentItemName = null,
                    currentItemType = null
                )
                delay(speed.batchCooldownMs) // Cooldown when no candidates eligible
                continue
            }

            // Process batch (limited to 10 items or 30 seconds active processing)
            val batchStartTime = System.nanoTime()
            var batchProcessed = 0

            for (candidate in candidates) {
                if (!currentCoroutineContext().isActive) break
                val elapsedSec = (System.nanoTime() - batchStartTime) / 1_000_000_000L
                if (elapsedSec >= 30L) break // 30s batch active time limit

                if (userPaused || !isAppInForeground || !isNetworkConnected || isPlaybackActive) {
                    evaluateAndTrigger()
                    return
                }

                // Get item stream name for status UI
                val streamEntity = iptvDao.getStreamsByIds(listOf(candidate.streamId)).firstOrNull()
                _state.value = _state.value.copy(
                    isCooldown = false,
                    currentItemName = streamEntity?.name,
                    currentItemType = candidate.streamType
                )

                processCandidate(credentials, accountKey, candidate)
                batchProcessed++
                updateStats(accountKey)

                delay(speed.itemDelayMs)
            }

            _state.value = _state.value.copy(
                isCooldown = true,
                currentItemName = null,
                currentItemType = null
            )

            if (batchProcessed > 0) {
                delay(speed.batchCooldownMs)
            }
        }
    }

    private suspend fun seedCacheFromStreams(accountKey: String) {
        try {
            val existingCache = iptvDao.getAllMetadataCacheForAccount(accountKey, "VOD") +
                    iptvDao.getAllMetadataCacheForAccount(accountKey, "SERIES")
            val cachedKeys = existingCache.map { "${it.streamType}_${it.streamId}" }.toSet()

            val allStreams = iptvDao.getVodAndSeriesStreams()
            val newEntries = mutableListOf<CatalogMetadataEntity>()

            for (stream in allStreams) {
                val type = if (stream.streamType.equals("SERIES", ignoreCase = true)) "SERIES" else "VOD"
                val key = "${type}_${stream.id}"
                if (key !in cachedKeys) {
                    val hasGenre = !stream.genre.isNullOrBlank()
                    newEntries.add(
                        CatalogMetadataEntity(
                            accountKey = accountKey,
                            streamType = type,
                            streamId = stream.id,
                            status = if (hasGenre) CatalogMetadataEntity.STATUS_SUCCESS else CatalogMetadataEntity.STATUS_PENDING,
                            genre = stream.genre
                        )
                    )
                }
            }

            if (newEntries.isNotEmpty()) {
                iptvDao.insertOrUpdateMetadataCacheList(newEntries)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error seeding metadata cache")
        }
    }

    private suspend fun processCandidate(credentials: XtreamCredentials, accountKey: String, candidate: CatalogMetadataEntity) {
        val now = System.currentTimeMillis()

        // Verify account hasn't changed before writing
        val currentAccountKey = settingsRepository.settingsFlow.first().credentials?.let { SettingsBackupCodec.accountKey(it) } ?: ""
        if (currentAccountKey != accountKey) return

        try {
            if (candidate.streamType == "VOD") {
                val vodInfo = catalogRepository.getVodInfo(credentials, vodId = candidate.streamId)
                val genre = vodInfo.info?.genre
                if (!genre.isNullOrBlank()) {
                    iptvDao.insertOrUpdateMetadataCache(
                        candidate.copy(
                            status = CatalogMetadataEntity.STATUS_SUCCESS,
                            genre = genre,
                            lastSuccess = now,
                            lastAttempt = now
                        )
                    )
                } else {
                    // Cache negative response for 7 days
                    iptvDao.insertOrUpdateMetadataCache(
                        candidate.copy(
                            status = CatalogMetadataEntity.STATUS_NO_DATA,
                            lastAttempt = now,
                            nextEligibleAt = now + (7 * 86400_000L)
                        )
                    )
                }
            } else { // SERIES
                val seriesInfo = catalogRepository.getSeriesMetadata(credentials, seriesId = candidate.streamId)
                val genre = seriesInfo?.genre
                if (!genre.isNullOrBlank()) {
                    iptvDao.insertOrUpdateMetadataCache(
                        candidate.copy(
                            status = CatalogMetadataEntity.STATUS_SUCCESS,
                            genre = genre,
                            lastSuccess = now,
                            lastAttempt = now
                        )
                    )
                } else {
                    iptvDao.insertOrUpdateMetadataCache(
                        candidate.copy(
                            status = CatalogMetadataEntity.STATUS_NO_DATA,
                            lastAttempt = now,
                            nextEligibleAt = now + (7 * 86400_000L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val attempts = candidate.attempts + 1
            val backoffMs = calculateBackoffMs(attempts)
            iptvDao.insertOrUpdateMetadataCache(
                candidate.copy(
                    status = CatalogMetadataEntity.STATUS_FAILED_TEMPORARY,
                    lastAttempt = now,
                    nextEligibleAt = now + backoffMs,
                    attempts = attempts,
                    errorMessage = e.message
                )
            )
        }
    }

    private fun calculateBackoffMs(attempts: Int): Long {
        val base = min((1L shl attempts.coerceAtMost(10)) * 60_000L, 24 * 3600_000L)
        val jitter = (0..5000).random().toLong()
        return base + jitter
    }

    private suspend fun updateStats(accountKey: String) {
        val vodTotal = iptvDao.getTotalMetadataCount(accountKey, "VOD")
        val seriesTotal = iptvDao.getTotalMetadataCount(accountKey, "SERIES")
        val total = vodTotal + seriesTotal

        val vodSuccess = iptvDao.getMetadataCountByStatus(accountKey, "VOD", CatalogMetadataEntity.STATUS_SUCCESS)
        val seriesSuccess = iptvDao.getMetadataCountByStatus(accountKey, "SERIES", CatalogMetadataEntity.STATUS_SUCCESS)
        val success = vodSuccess + seriesSuccess

        val vodNoData = iptvDao.getMetadataCountByStatus(accountKey, "VOD", CatalogMetadataEntity.STATUS_NO_DATA)
        val seriesNoData = iptvDao.getMetadataCountByStatus(accountKey, "SERIES", CatalogMetadataEntity.STATUS_NO_DATA)
        val noData = vodNoData + seriesNoData

        val vodFailed = iptvDao.getMetadataCountByStatus(accountKey, "VOD", CatalogMetadataEntity.STATUS_FAILED_TEMPORARY)
        val seriesFailed = iptvDao.getMetadataCountByStatus(accountKey, "SERIES", CatalogMetadataEntity.STATUS_FAILED_TEMPORARY)
        val failed = vodFailed + seriesFailed

        _state.value = _state.value.copy(
            vodTotal = vodTotal,
            vodProcessed = vodSuccess + vodNoData,
            vodWithGenre = vodSuccess,
            seriesTotal = seriesTotal,
            seriesProcessed = seriesSuccess + seriesNoData,
            seriesWithGenre = seriesSuccess,
            totalItems = total,
            processedItems = success + noData,
            withGenreItems = success,
            noDataItems = noData,
            failedItems = failed
        )
    }
}
