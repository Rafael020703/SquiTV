package rsv.squitv.core.domain.interactor

import rsv.squitv.core.domain.state.AppSyncProgress
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.UserRepository
import rsv.squitv.data.model.XtreamCategory
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.data.repository.FirebaseRepository
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.domain.usecase.SyncDataUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val syncDataUseCase: SyncDataUseCase,
    private val firebaseRepository: FirebaseRepository,
    private val profileManager: ProfileManager,
    private val authManager: AuthManager
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    private val _syncProgress = MutableStateFlow(AppSyncProgress())
    val syncProgress: StateFlow<AppSyncProgress> = _syncProgress.asStateFlow()

    private val _isContentReady = MutableStateFlow(false)
    val isContentReady: StateFlow<Boolean> = _isContentReady.asStateFlow()

    private val _allLiveStreams = MutableStateFlow<List<XtreamStream>>(emptyList())
    val allLiveStreams: StateFlow<List<XtreamStream>> = _allLiveStreams.asStateFlow()

    private val _categories = MutableStateFlow<List<XtreamCategory>>(emptyList())
    val categories: StateFlow<List<XtreamCategory>> = _categories.asStateFlow()

    init {
        managerScope.launch {
            // Check readiness at startup. 
            checkContentReadiness()
        }
        managerScope.launch {
            authManager.isLoggedIn.collect { logged ->
                if (logged == true) {
                    processPendingSyncs()
                } else if (logged == false) {
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

    fun startSync(force: Boolean = false) {
        if (syncJob?.isActive == true && !force) return
        
        syncJob?.cancel()
        Timber.d("Sync: STARTING (force=$force)")
        syncJob = managerScope.launch {
            syncDataUseCase(force = force).collect { result ->
                _syncProgress.value = AppSyncProgress(
                    accountStatus = result.accountStatus,
                    liveStatus = result.liveStatus,
                    vodStatus = result.vodStatus,
                    seriesStatus = result.seriesStatus,
                    isComplete = result.isComplete,
                    error = result.error
                )
                
                if (result.isComplete) {
                    Timber.d("Sync: COMPLETE emission received. Error: ${result.error}")
                    // Ensure content is marked as ready if there's data, bypassing possible DataStore propagation delays
                    val stats = catalogRepository.getStats()
                    val hasData = stats.first > 0 || stats.second > 0 || stats.third > 0
                    
                    if (result.error == null && hasData) {
                        Timber.i("Sync: SUCCESS. Marking content as ready. Stats: $stats")
                        _isContentReady.value = true
                    } else {
                        // Fallback to official check if there was an error or no data
                        checkContentReadiness()
                    }
                    
                    if (result.error == null) {
                        processPendingSyncs()
                    }
                }
            }
        }
    }

    fun processPendingSyncs() {
        managerScope.launch {
            val pendings = userRepository.getAllPendingSyncs()
            if (pendings.isEmpty()) return@launch
            
            Timber.d("Processing ${pendings.size} pending syncs...")
            val profileId = profileManager.activeProfile.value?.id ?: "default"
            
            pendings.forEach { pending ->
                val success = when (pending.type) {
                    "FAVORITE_ADD" -> {
                        val item = Json.decodeFromString<IptvItem>(pending.payload)
                        firebaseRepository.addFavorite(profileId, item, pending.providerHash)
                    }
                    "FAVORITE_REMOVE" -> {
                        firebaseRepository.removeFavorite(profileId, pending.contentId, pending.providerHash)
                    }
                    "WATCH_PROGRESS" -> {
                        val rawData = Json.decodeFromString<Map<String, JsonElement>>(pending.payload).mapValues { 
                            when(it.value) {
                                is JsonPrimitive -> it.value.jsonPrimitive.content
                                else -> it.value.toString()
                            }
                        }
                        firebaseRepository.syncWatchProgress(profileId, pending.contentId, rawData)
                    }
                    "HISTORY_ADD" -> {
                        val item = Json.decodeFromString<IptvItem>(pending.payload)
                        firebaseRepository.addHistory(profileId, item)
                    }
                    else -> true
                }
                
                if (success) {
                    userRepository.deletePendingSync(pending.type, pending.providerHash, pending.contentId)
                }
            }
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

    fun clearCatalogData() {
        if (syncJob?.isActive == true) {
            syncJob?.cancel()
        }

        _syncProgress.value = AppSyncProgress()
        _isContentReady.value = false

        managerScope.launch {
            try {
                settingsRepository.clearSyncTimestamps()
                catalogRepository.clearCatalogData()
                Timber.i("SyncManager: Catalog data cleared and sync timestamps reset.")
                startSync(force = true)
            } catch (e: Exception) {
                Timber.e(e, "Error during catalog data reset")
                _syncProgress.value = AppSyncProgress(isComplete = true, error = e.message)
            }
        }
    }
}
