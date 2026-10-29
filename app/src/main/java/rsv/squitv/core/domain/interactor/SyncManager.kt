package rsv.squitv.core.domain.interactor

import rsv.squitv.core.domain.state.AppSyncProgress
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.data.model.XtreamCategory
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.usecase.SyncDataUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val syncDataUseCase: SyncDataUseCase,
    private val authManager: AuthManager
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var resetJob: Job? = null

    private val _syncProgress = MutableStateFlow(AppSyncProgress())
    val syncProgress: StateFlow<AppSyncProgress> = _syncProgress.asStateFlow()
    val catalogRevision = syncDataUseCase.catalogRevision

    private val _isContentReady = MutableStateFlow(false)
    val isContentReady: StateFlow<Boolean> = _isContentReady.asStateFlow()

    private val _allLiveStreams = MutableStateFlow<List<XtreamStream>>(emptyList())
    val allLiveStreams: StateFlow<List<XtreamStream>> = _allLiveStreams.asStateFlow()

    private val _categories = MutableStateFlow<List<XtreamCategory>>(emptyList())
    val categories: StateFlow<List<XtreamCategory>> = _categories.asStateFlow()

    init {
        managerScope.launch {
            catalogRevision.drop(1).collect {
                if (_allLiveStreams.value.isNotEmpty()) {
                    _allLiveStreams.value = catalogRepository.getCachedLiveStreams()
                }
            }
        }
        managerScope.launch {
            syncDataUseCase.progress.filterNotNull().collect { result ->
                _syncProgress.value = AppSyncProgress(
                    accountStatus = result.accountStatus, liveStatus = result.liveStatus,
                    vodStatus = result.vodStatus, seriesStatus = result.seriesStatus,
                    epgStatus = result.epgStatus, operationId = result.operationId,
                    isRunning = !result.isComplete, isCancelled = result.isCancelled,
                    isComplete = result.isComplete, error = result.error
                )
                if (result.isComplete && !result.isCancelled) {
                    if (result.isContentReady) _isContentReady.value = true
                    else checkContentReadiness()
                }
            }
        }
        managerScope.launch {
            // Check readiness at startup. 
            checkContentReadiness()
        }
        managerScope.launch {
            authManager.isLoggedIn.collect { logged ->
                if (logged == false) {
                    // Logout: We preserve catalog data but reset readiness state 
                    // until the next login confirms if this data is still valid.
                    _isContentReady.value = false
                    Timber.d("SyncManager: User logged out. Preserving data but resetting readiness.")
                }
            }
        }
    }

    suspend fun checkContentReadiness() {
        val stats = catalogRepository.getStats()
        val lastSync = settingsRepository.settingsFlow.first().lastSyncTimestamp
        // Content is ready if local data exists AND lastSyncTimestamp > 0 (ensuring at least one full sync completion)
        val hasContent = (stats.first > 0 || stats.second > 0 || stats.third > 0) && lastSync > 0
        _isContentReady.value = hasContent
        Timber.i("SyncManager: Readiness check -> $hasContent (Stats: Live=${stats.first}, VOD=${stats.second}, Series=${stats.third} | LastSync: $lastSync)")
    }

    @Synchronized
    fun startSync(force: Boolean = false) {
        // A second click must observe the current run, rather than cancelling its downloads.
        if (syncJob?.isActive == true || syncDataUseCase.progress.value?.isComplete == false) return
        _syncProgress.value = AppSyncProgress(isRunning = true)
        Timber.d("Sync: STARTING (force=$force)")
        syncJob = managerScope.launch {
            syncDataUseCase(force = force).collect()
        }
    }

    fun loadAllLiveStreams(creds: XtreamCredentials) {
        managerScope.launch {
            try {
                val streams = catalogRepository.getLiveStreams(creds)
                _allLiveStreams.value = streams
            } catch (e: Exception) {
                Timber.e(e, "Error loading all live streams")
                _allLiveStreams.value = emptyList()
            }
        }
    }

    fun loadCategories(creds: XtreamCredentials, type: String, isRefresh: Boolean = false) {
        managerScope.launch {
            try {
                val cats = when (type) {
                    "live" -> catalogRepository.getLiveCategories(creds, forceRefresh = isRefresh)
                    "movie" -> catalogRepository.getVodCategories(creds, forceRefresh = isRefresh)
                    "series" -> catalogRepository.getSeriesCategories(creds, forceRefresh = isRefresh)
                    else -> emptyList()
                }
                val mappedCats = cats.map { XtreamCategory(it.id, it.name) }.toMutableList()
                
                // Add FAVORITES category if not present
                val favCat = mappedCats.find { it.categoryId == "FAVORITES" }
                if (favCat != null) mappedCats.remove(favCat)
                mappedCats.add(0, XtreamCategory("FAVORITES", "Favoritos"))

                // Add RECENTS category if not present
                val recentsCat = mappedCats.find { it.categoryId == "RECENTS" }
                if (recentsCat != null) mappedCats.remove(recentsCat)
                mappedCats.add(1, XtreamCategory("RECENTS", "Adicionados recentemente"))

                _categories.value = mappedCats
            } catch (e: Exception) {
                Timber.e(e, "Error loading categories")
            }
        }
    }

    @Synchronized
    fun clearCatalogData() {
        if (resetJob?.isActive == true) return
        resetJob = managerScope.launch {
            try {
                syncJob?.cancelAndJoin()
                syncDataUseCase.clearCatalogData()
                _syncProgress.value = AppSyncProgress()
                _isContentReady.value = false
                Timber.i("SyncManager: Catalog data cleared and sync timestamps reset.")
                startSync(force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error during catalog data reset")
                _syncProgress.value = AppSyncProgress(isComplete = true, error = e.message)
            }
        }
    }
}
