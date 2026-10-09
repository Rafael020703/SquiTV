package rsv.squitv.ui.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rsv.squitv.core.app.AppStateManager
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.UserRepository
import rsv.squitv.core.domain.interactor.*
import rsv.squitv.core.domain.state.AppState
import rsv.squitv.core.domain.state.AppSyncProgress
import rsv.squitv.data.model.XtreamCategory
import rsv.squitv.data.model.UserInfo
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.domain.model.UserProfile
import rsv.squitv.R
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import rsv.squitv.data.repository.UpdateRepository
import rsv.squitv.domain.model.UpdateCheckResult
import rsv.squitv.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
@UnstableApi
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val authManager: AuthManager,
    private val syncManager: SyncManager,
    private val profileManager: ProfileManager,
    private val securityManager: SecurityManager,
    private val themeManager: ThemeManager,
    val appStateManager: AppStateManager
) : ViewModel() {

    // Global UI State
    private val _loadingMessage = MutableStateFlow("Iniciando sistema...")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Delegated States
    val appState: StateFlow<AppState> = appStateManager.appState
    val isLoggedIn = authManager.isLoggedIn
    val credentials get() = authManager.credentials
    val accountInfo = authManager.accountInfo
    val sessionStatus = authManager.sessionStatus
    val showExpiryWarning = authManager.showExpiryWarning
    
    val activeProfile = profileManager.activeProfile
    val profiles = profileManager.profiles
    val profileImageUrl = profileManager.profileImageUrl

    val isContentReady = syncManager.isContentReady
    val syncProgress: StateFlow<AppSyncProgress> = syncManager.syncProgress
    val allLiveStreams = syncManager.allLiveStreams
    val categories = syncManager.categories

    val isAppLocked = securityManager.isAppLocked
    val blockedCategoryIds = securityManager.blockedCategoryIds

    val useOledTheme = themeManager.useOledTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val uiZoom = themeManager.uiZoom.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val language = themeManager.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "pt")

    val appSettings: StateFlow<SettingsRepository.AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsRepository.AppSettings(null, lastSyncTimestamp = 0L, syncIntervalHours = 24)
    )

    // Dashboard/Info specific (To be moved in Phase 5)
    private val _dbStats = MutableStateFlow<Triple<Int, Int, Int>>(Triple(0, 0, 0))
    val dbStats: StateFlow<Triple<Int, Int, Int>> = _dbStats.asStateFlow()

    private val _cacheSize = MutableStateFlow("0 MB")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _newlyAdded = MutableStateFlow<Map<ContentType, List<IptvItem>>>(emptyMap())
    val newlyAdded: StateFlow<Map<ContentType, List<IptvItem>>> = _newlyAdded.asStateFlow()

    init {
        viewModelScope.launch {
            appState.collect { state ->
                when (state) {
                    is AppState.Initializing -> _loadingMessage.value = "Carregando configurações..."
                    is AppState.SyncRequired -> _loadingMessage.value = "Preparando biblioteca..."
                    is AppState.Ready -> {
                        _loadingMessage.value = "Bem-vindo!"
                        refreshStats()
                        checkUpdatesForDashboard()
                    }
                    else -> {}
                }
            }
        }
    }

    private val _availableUpdatePrompt = MutableStateFlow<rsv.squitv.domain.model.AppUpdateInfo?>(null)
    val availableUpdatePrompt: StateFlow<rsv.squitv.domain.model.AppUpdateInfo?> = _availableUpdatePrompt.asStateFlow()

    private var hasShownUpdateModalThisSession = false

    fun checkUpdatesForDashboard() {
        if (hasShownUpdateModalThisSession) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updateResult = updateRepository.checkForUpdates(force = false)
                if (updateResult is UpdateCheckResult.UpdateAvailable) {
                    val settings = settingsRepository.settingsFlow.first()
                    val info = updateResult.updateInfo
                    if (settings.ignoredVersion != info.versionName) {
                        _availableUpdatePrompt.value = info
                        if (settings.lastNotifiedVersion != info.versionName) {
                            NotificationHelper.showUpdateNotification(context, info)
                            settingsRepository.updateLastNotifiedVersion(info.versionName)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Erro ao verificar atualizações em background")
            }
        }
    }

    fun dismissUpdateModal() {
        hasShownUpdateModalThisSession = true
        _availableUpdatePrompt.value = null
    }

    fun ignoreUpdateVersion(versionName: String) {
        hasShownUpdateModalThisSession = true
        _availableUpdatePrompt.value = null
        viewModelScope.launch {
            settingsRepository.updateIgnoredVersion(versionName)
        }
    }

    // Proxy Methods
    fun logout() = authManager.logout()
    fun switchAccount(index: Int) = authManager.switchAccount(index)
    fun removeAccount(index: Int) = authManager.removeAccount(index)
    fun refreshAccountInfo() {
        authManager.credentials?.let { authManager.refreshAccountInfo(it) }
    }

    fun selectProfile(profile: UserProfile) = profileManager.selectProfile(profile)
    fun addProfile(name: String, iconUrl: String? = null, isChild: Boolean = false) = 
        profileManager.addProfile(name, iconUrl, isChild)
    fun deleteProfile(profileId: String) = profileManager.deleteProfile(profileId)
    fun updateProfilePicture(url: String) = profileManager.updateProfilePicture(url)

    fun unlockApp(pin: String) = securityManager.unlockApp(pin)
    fun unlockCategory(catId: String) = securityManager.unlockCategory(catId)

    fun loadData(force: Boolean = false) = syncManager.startSync(force)
    fun loadAllLiveStreams() {
        authManager.credentials?.let { syncManager.loadAllLiveStreams(it) }
    }
    fun loadLiveCategories(isRefresh: Boolean = false) {
        authManager.credentials?.let { syncManager.loadCategories(it, "live", isRefresh) }
    }
    fun clearCatalogData() = syncManager.clearCatalogData()

    fun refreshStats() {
        viewModelScope.launch {
            _dbStats.value = catalogRepository.getStats()
            calculateCacheSize()
            loadNewlyAdded()
        }
    }

    private fun loadNewlyAdded() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recent = catalogRepository.getRecentStreams(20)
                val grouped = recent.groupBy { it.type }
                _newlyAdded.value = grouped
            } catch (e: Exception) {
                Timber.e(e, "Error loading newly added content")
            }
        }
    }

    private fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = context.cacheDir
            val size = getFolderSize(cacheDir) + getFolderSize(context.externalCacheDir)
            _cacheSize.value = "%.2f MB".format(size / (1024.0 * 1024.0))
        }
    }

    private fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (!file.isDirectory) return file.length()
        var size = 0L
        file.listFiles()?.forEach { size += getFolderSize(it) }
        return size
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            context.cacheDir.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()
            calculateCacheSize()
        }
    }

    fun loadCategories(type: String, isRefresh: Boolean = false) {
        authManager.credentials?.let { syncManager.loadCategories(it, type, isRefresh) }
    }

    fun saveLastCategory(type: String, categoryId: String) {
        viewModelScope.launch {
            settingsRepository.updateLastCategory(type, categoryId)
        }
    }

    fun saveLastChannel(type: String, categoryId: String, channelId: String, channelName: String) {
        viewModelScope.launch {
            settingsRepository.updateLastChannel(type, categoryId, channelId, channelName)
        }
    }

    fun addSearchHistory(query: String) {
        viewModelScope.launch {
            userRepository.insertSearchHistory(query)
        }
    }

    fun getAgeRatingColor(rating: String?): Color {
        val r = rating?.uppercase() ?: ""
        return when {
            r.contains("18") || r.contains("R") -> Color.Red
            r.contains("16") -> androidx.compose.ui.graphics.Color(0xFFFF9800)
            r.contains("14") -> androidx.compose.ui.graphics.Color(0xFFFFC107)
            r.contains("12") -> androidx.compose.ui.graphics.Color(0xFFCDDC39)
            r.contains("10") -> androidx.compose.ui.graphics.Color(0xFF2196F3)
            r.contains("L") || r.contains("G") -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
            else -> Color.Gray
        }
    }
}
