package rsv.squitv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import rsv.squitv.data.model.UserInfo
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.util.SecurityHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    init {
        // Safe migration on startup
        migrateToEncryptedStorage()
    }

    private fun migrateToEncryptedStorage() {
        // We use runBlocking here as this is a one-time migration on singleton init
        // and needs to ensure data consistency before the first flow emission.
        runBlocking {
            context.dataStore.edit { preferences ->
                // 1. Migrate single password
                val currentPassword = preferences[PreferencesKeys.PASSWORD]
                if (currentPassword != null && !SecurityHelper.isEncrypted(currentPassword)) {
                    SecurityHelper.encrypt(currentPassword)?.let { encrypted ->
                        preferences[PreferencesKeys.PASSWORD] = encrypted
                    }
                }

                // 2. Migrate PIN
                val currentPin = preferences[PreferencesKeys.APP_PIN]
                if (currentPin != null && !SecurityHelper.isEncrypted(currentPin)) {
                    SecurityHelper.encrypt(currentPin)?.let { encrypted ->
                        preferences[PreferencesKeys.APP_PIN] = encrypted
                    }
                }

                // 3. Migrate Accounts list
                val accountsJson = preferences[PreferencesKeys.ACCOUNTS]
                if (accountsJson != null) {
                    try {
                        val accounts = Json.decodeFromString<List<XtreamCredentials>>(accountsJson)
                        var changed = false
                        val migratedAccounts = accounts.map { cred ->
                            if (!SecurityHelper.isEncrypted(cred.password)) {
                                changed = true
                                cred.copy(password = SecurityHelper.encrypt(cred.password) ?: cred.password)
                            } else cred
                        }
                        if (changed) {
                            preferences[PreferencesKeys.ACCOUNTS] = Json.encodeToString(migratedAccounts)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private object PreferencesKeys {
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val BASE_URL = stringPreferencesKey("base_url")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        val APP_PIN = stringPreferencesKey("app_pin")
        val AUTO_PLAY_ENABLED = booleanPreferencesKey("auto_play_enabled")
        val DEFAULT_RESIZE_MODE = intPreferencesKey("default_resize_mode")
        val SHOW_DIAGNOSTICS = booleanPreferencesKey("show_diagnostics")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val HIDE_BLOCKED_CATEGORIES = booleanPreferencesKey("hide_blocked_categories")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val ACCOUNTS = stringPreferencesKey("accounts")
        val CURRENT_ACCOUNT_INDEX = intPreferencesKey("current_account_index")
        val PLAYER_ENGINE = stringPreferencesKey("player_engine") // EXO, VLC, IJK
        val IS_LIVE_LOADED = booleanPreferencesKey("is_live_loaded")
        val IS_VOD_LOADED = booleanPreferencesKey("is_vod_loaded")
        val IS_SERIES_LOADED = booleanPreferencesKey("is_series_loaded")
        val LAST_SYNC_LIVE = longPreferencesKey("last_sync_live")
        val LAST_SYNC_VOD = longPreferencesKey("last_sync_vod")
        val LAST_SYNC_SERIES = longPreferencesKey("last_sync_series")
        val BUFFER_STRATEGY = stringPreferencesKey("buffer_strategy")
        val USE_OLED_THEME = booleanPreferencesKey("use_oled_theme")
        val DATA_SAVER_MODE = booleanPreferencesKey("data_saver_mode")
        val UI_ZOOM = floatPreferencesKey("ui_zoom")
        val LANGUAGE = stringPreferencesKey("language")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val SMART_DOWNLOADS_ENABLED = booleanPreferencesKey("smart_downloads_enabled")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val DETAILED_NOTIFICATIONS = booleanPreferencesKey("detailed_notifications")
        val BACKGROUND_PLAYBACK_ENABLED = booleanPreferencesKey("background_playback_enabled")
        
        val LAST_LIVE_CATEGORY = stringPreferencesKey("last_live_category")
        val LAST_LIVE_CHANNEL_ID = stringPreferencesKey("last_live_channel_id")
        val LAST_LIVE_CHANNEL_NAME = stringPreferencesKey("last_live_channel_name")
        val LAST_LIVE_CATEGORY_CHANNELS = stringPreferencesKey("last_live_category_channels")
        val LAST_LIVE_CATEGORY_CHANNEL_NAMES = stringPreferencesKey("last_live_category_channel_names")
        val LAST_MOVIE_CATEGORY = stringPreferencesKey("last_movie_category")
        val LAST_SERIES_CATEGORY = stringPreferencesKey("last_series_category")
        
        // Account Cache
        val ACCOUNT_EXP_DATE = stringPreferencesKey("account_exp_date")
        val ACCOUNT_STATUS = stringPreferencesKey("account_status")
        val ACCOUNT_ACTIVE_CONS = stringPreferencesKey("account_active_cons")
        val ACCOUNT_MAX_CONNECTIONS = stringPreferencesKey("account_max_connections")

        // App Updates
        val LAST_UPDATE_CHECK_TIMESTAMP = longPreferencesKey("last_update_check_timestamp")
        val LAST_AVAILABLE_VERSION = stringPreferencesKey("last_available_version")
        val LAST_NOTIFIED_VERSION = stringPreferencesKey("last_notified_version")
        val IGNORED_VERSION = stringPreferencesKey("ignored_version")
    }

    data class AppSettings(
        val credentials: XtreamCredentials?,
        val accounts: List<XtreamCredentials> = emptyList(),
        val currentAccountIndex: Int = 0,
        val playerEngine: String = "EXO",
        val isLiveLoaded: Boolean = false,
        val isVodLoaded: Boolean = false,
        val isSeriesLoaded: Boolean = false,
        val lastSyncLive: Long = 0L,
        val lastSyncVod: Long = 0L,
        val lastSyncSeries: Long = 0L,
        val bufferStrategy: String = "Stable",
        val useOledTheme: Boolean = true,
        val dataSaverMode: Boolean = false,
        val uiZoom: Float = 1.0f,
        val language: String = "pt",
        val lastSyncTimestamp: Long,
        val syncIntervalHours: Int,
        val appPin: String? = null,
        val autoPlayEnabled: Boolean = true,
        val defaultResizeMode: Int = 0, // RESIZE_MODE_FIT
        val showDiagnostics: Boolean = true,
        val compactMode: Boolean = true,
        val hideBlockedCategories: Boolean = true,
        val appLockEnabled: Boolean = false,
        val downloadWifiOnly: Boolean = true,
        val smartDownloadsEnabled: Boolean = true,
        val preferredAudioLang: String? = null,
        val preferredSubtitleLang: String? = null,
        val activeProfileId: String = "default",
        val detailedNotifications: Boolean = true,
        val backgroundPlaybackEnabled: Boolean = false,
        val lastLiveCategory: String? = null,
        val lastLiveChannelId: String? = null,
        val lastLiveChannelName: String? = null,
        val lastLiveCategoryChannels: Map<String, String> = emptyMap(),
        val lastLiveCategoryChannelNames: Map<String, String> = emptyMap(),
        val lastMovieCategory: String? = null,
        val lastSeriesCategory: String? = null,
        val cachedUserInfo: UserInfo? = null,
        val lastUpdateCheckTimestamp: Long = 0L,
        val lastAvailableVersion: String? = null,
        val lastNotifiedVersion: String? = null,
        val ignoredVersion: String? = null
    )

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val accountsJson = preferences[PreferencesKeys.ACCOUNTS]
        val accounts = try {
            if (accountsJson != null) {
                val rawAccounts = Json.decodeFromString<List<XtreamCredentials>>(accountsJson)
                rawAccounts.map { it.copy(password = SecurityHelper.decrypt(it.password) ?: "") }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val currentIndex = preferences[PreferencesKeys.CURRENT_ACCOUNT_INDEX] ?: 0
        
        val playerEngine = preferences[PreferencesKeys.PLAYER_ENGINE] ?: "EXO"
        val isLiveLoaded = preferences[PreferencesKeys.IS_LIVE_LOADED] ?: false
        val isVodLoaded = preferences[PreferencesKeys.IS_VOD_LOADED] ?: false
        val isSeriesLoaded = preferences[PreferencesKeys.IS_SERIES_LOADED] ?: false
        val lastSyncLive = preferences[PreferencesKeys.LAST_SYNC_LIVE] ?: 0L
        val lastSyncVod = preferences[PreferencesKeys.LAST_SYNC_VOD] ?: 0L
        val lastSyncSeries = preferences[PreferencesKeys.LAST_SYNC_SERIES] ?: 0L
        val bufferStrat = preferences[PreferencesKeys.BUFFER_STRATEGY] ?: "Stable"
        val oledTheme = preferences[PreferencesKeys.USE_OLED_THEME] ?: true
        val dataSaver = preferences[PreferencesKeys.DATA_SAVER_MODE] ?: false
        val zoom = preferences[PreferencesKeys.UI_ZOOM] ?: 1.0f
        val language = preferences[PreferencesKeys.LANGUAGE] ?: "pt"
        val downloadWifiOnly = preferences[PreferencesKeys.DOWNLOAD_WIFI_ONLY] ?: true
        val smartDownloads = preferences[PreferencesKeys.SMART_DOWNLOADS_ENABLED] ?: true

        val username = preferences[PreferencesKeys.USERNAME]
        val encryptedPassword = preferences[PreferencesKeys.PASSWORD]
        val password = SecurityHelper.decrypt(encryptedPassword)
        val baseUrl = preferences[PreferencesKeys.BASE_URL]
        val lastSync = preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L
        val interval = preferences[PreferencesKeys.SYNC_INTERVAL_HOURS] ?: 24
        val encryptedPin = preferences[PreferencesKeys.APP_PIN]
        val pin = SecurityHelper.decrypt(encryptedPin)
        
        val autoPlay = preferences[PreferencesKeys.AUTO_PLAY_ENABLED] ?: true
        val resizeMode = preferences[PreferencesKeys.DEFAULT_RESIZE_MODE] ?: 0
        val diagnostics = preferences[PreferencesKeys.SHOW_DIAGNOSTICS] ?: true
        val compact = preferences[PreferencesKeys.COMPACT_MODE] ?: true
        val hideBlocked = preferences[PreferencesKeys.HIDE_BLOCKED_CATEGORIES] ?: true
        val appLock = preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false
        val audioLang = preferences[PreferencesKeys.PREFERRED_AUDIO_LANG]
        val subLang = preferences[PreferencesKeys.PREFERRED_SUBTITLE_LANG]
        val profileId = preferences[PreferencesKeys.ACTIVE_PROFILE_ID] ?: "default"
        val detailedNotifs = preferences[PreferencesKeys.DETAILED_NOTIFICATIONS] ?: true
        val bgPlaybackEnabled = preferences[PreferencesKeys.BACKGROUND_PLAYBACK_ENABLED] ?: false
        
        val lastLive = preferences[PreferencesKeys.LAST_LIVE_CATEGORY]
        val lastLiveChannelId = preferences[PreferencesKeys.LAST_LIVE_CHANNEL_ID]
        val lastLiveChannelName = preferences[PreferencesKeys.LAST_LIVE_CHANNEL_NAME]
        val lastLiveChannelsJson = preferences[PreferencesKeys.LAST_LIVE_CATEGORY_CHANNELS]
        val lastLiveCategoryChannels = try {
            if (lastLiveChannelsJson != null) Json.decodeFromString<Map<String, String>>(lastLiveChannelsJson) else emptyMap()
        } catch (_: Exception) { emptyMap() }
        val lastLiveNamesJson = preferences[PreferencesKeys.LAST_LIVE_CATEGORY_CHANNEL_NAMES]
        val lastLiveCategoryChannelNames = try {
            if (lastLiveNamesJson != null) Json.decodeFromString<Map<String, String>>(lastLiveNamesJson) else emptyMap()
        } catch (_: Exception) { emptyMap() }
        val lastMovie = preferences[PreferencesKeys.LAST_MOVIE_CATEGORY]
        val lastSeries = preferences[PreferencesKeys.LAST_SERIES_CATEGORY]

        val cachedExp = preferences[PreferencesKeys.ACCOUNT_EXP_DATE]
        val cachedStatus = preferences[PreferencesKeys.ACCOUNT_STATUS]
        val cachedActive = preferences[PreferencesKeys.ACCOUNT_ACTIVE_CONS]
        val cachedMax = preferences[PreferencesKeys.ACCOUNT_MAX_CONNECTIONS]
        
        val cachedUserInfo = if (cachedStatus != null) {
            rsv.squitv.data.model.UserInfo(
                auth = 1,
                status = cachedStatus,
                expDate = cachedExp,
                activeCons = cachedActive,
                maxConnections = cachedMax
            )
        } else null

        val lastUpdateCheck = preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIMESTAMP] ?: 0L
        val lastAvailableVersion = preferences[PreferencesKeys.LAST_AVAILABLE_VERSION]
        val lastNotifiedVersion = preferences[PreferencesKeys.LAST_NOTIFIED_VERSION]
        val ignoredVersion = preferences[PreferencesKeys.IGNORED_VERSION]

        val creds = if (username != null && password != null && baseUrl != null) {
            XtreamCredentials(username, password, baseUrl)
        } else if (accounts.isNotEmpty() && currentIndex >= 0 && currentIndex < accounts.size) {
            accounts[currentIndex]
        } else {
            null
        }
        
        AppSettings(
            credentials = creds,
            accounts = accounts,
            currentAccountIndex = currentIndex,
            playerEngine = playerEngine,
            isLiveLoaded = isLiveLoaded,
            isVodLoaded = isVodLoaded,
            isSeriesLoaded = isSeriesLoaded,
            lastSyncLive = lastSyncLive,
            lastSyncVod = lastSyncVod,
            lastSyncSeries = lastSyncSeries,
            bufferStrategy = bufferStrat,
            useOledTheme = oledTheme,
            dataSaverMode = dataSaver,
            uiZoom = zoom,
            language = language,
            lastSyncTimestamp = lastSync,
            syncIntervalHours = interval,
            appPin = pin,
            autoPlayEnabled = autoPlay,
            defaultResizeMode = resizeMode,
            showDiagnostics = diagnostics,
            compactMode = compact,
            hideBlockedCategories = hideBlocked,
            appLockEnabled = appLock,
            downloadWifiOnly = downloadWifiOnly,
            smartDownloadsEnabled = smartDownloads,
            preferredAudioLang = audioLang,
            preferredSubtitleLang = subLang,
            activeProfileId = profileId,
            detailedNotifications = detailedNotifs,
            backgroundPlaybackEnabled = bgPlaybackEnabled,
            lastLiveCategory = lastLive,
            lastLiveChannelId = lastLiveChannelId,
            lastLiveChannelName = lastLiveChannelName,
            lastLiveCategoryChannels = lastLiveCategoryChannels,
            lastLiveCategoryChannelNames = lastLiveCategoryChannelNames,
            lastMovieCategory = lastMovie,
            lastSeriesCategory = lastSeries,
            cachedUserInfo = cachedUserInfo,
            lastUpdateCheckTimestamp = lastUpdateCheck,
            lastAvailableVersion = lastAvailableVersion,
            lastNotifiedVersion = lastNotifiedVersion,
            ignoredVersion = ignoredVersion
        )
    }

    val credentialsFlow: Flow<XtreamCredentials?> = settingsFlow.map { it.credentials }

    suspend fun saveCredentials(credentials: XtreamCredentials) {
        context.dataStore.edit { preferences ->
            val encryptedPassword = SecurityHelper.encrypt(credentials.password) ?: credentials.password
            val encryptedCreds = credentials.copy(password = encryptedPassword)
            
            preferences[PreferencesKeys.USERNAME] = credentials.username
            preferences[PreferencesKeys.PASSWORD] = encryptedPassword
            preferences[PreferencesKeys.BASE_URL] = credentials.baseUrl
            
            // Also add to accounts list
            val accountsJson = preferences[PreferencesKeys.ACCOUNTS]
            val accounts = try {
                if (accountsJson != null) Json.decodeFromString<List<XtreamCredentials>>(accountsJson).toMutableList()
                else mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            
            if (!accounts.any { it.username == credentials.username && it.baseUrl == credentials.baseUrl }) {
                accounts.add(encryptedCreds)
                preferences[PreferencesKeys.ACCOUNTS] = Json.encodeToString(accounts)
                preferences[PreferencesKeys.CURRENT_ACCOUNT_INDEX] = accounts.size - 1
            } else {
                // Update existing account with encrypted password
                val index = accounts.indexOfFirst { it.username == credentials.username && it.baseUrl == credentials.baseUrl }
                if (index != -1) {
                    accounts[index] = encryptedCreds
                    preferences[PreferencesKeys.ACCOUNTS] = Json.encodeToString(accounts)
                    preferences[PreferencesKeys.CURRENT_ACCOUNT_INDEX] = index
                }
            }
        }
    }

    suspend fun addAccount(credentials: XtreamCredentials) {
        saveCredentials(credentials)
    }

    suspend fun removeAccount(index: Int) {
        context.dataStore.edit { preferences ->
            val accountsJson = preferences[PreferencesKeys.ACCOUNTS]
            val accounts = try {
                if (accountsJson != null) Json.decodeFromString<List<XtreamCredentials>>(accountsJson).toMutableList()
                else return@edit
            } catch (_: Exception) {
                return@edit
            }
            
            if (index in accounts.indices) {
                accounts.removeAt(index)
                preferences[PreferencesKeys.ACCOUNTS] = Json.encodeToString(accounts)
                
                val currentIndex = preferences[PreferencesKeys.CURRENT_ACCOUNT_INDEX] ?: 0
                if (currentIndex == index) {
                    preferences[PreferencesKeys.CURRENT_ACCOUNT_INDEX] = 0
                    if (accounts.isNotEmpty()) {
                        val first = accounts[0]
                        preferences[PreferencesKeys.USERNAME] = first.username
                        preferences[PreferencesKeys.PASSWORD] = first.password
                        preferences[PreferencesKeys.BASE_URL] = first.baseUrl
                    } else {
                        preferences.remove(PreferencesKeys.USERNAME)
                        preferences.remove(PreferencesKeys.PASSWORD)
                        preferences.remove(PreferencesKeys.BASE_URL)
                        preferences.remove(PreferencesKeys.LAST_SYNC_TIMESTAMP)
                    }
                } else if (currentIndex > index) {
                    preferences[PreferencesKeys.CURRENT_ACCOUNT_INDEX] = currentIndex - 1
                }
            }
        }
    }

    suspend fun switchAccount(index: Int) {
        context.dataStore.edit { preferences ->
            val accountsJson = preferences[PreferencesKeys.ACCOUNTS]
            val accounts = try {
                if (accountsJson != null) Json.decodeFromString<List<XtreamCredentials>>(accountsJson)
                else return@edit
            } catch (_: Exception) {
                return@edit
            }
            
            if (index in accounts.indices) {
                preferences[PreferencesKeys.CURRENT_ACCOUNT_INDEX] = index
                val creds = accounts[index]
                preferences[PreferencesKeys.USERNAME] = creds.username
                preferences[PreferencesKeys.PASSWORD] = creds.password
                preferences[PreferencesKeys.BASE_URL] = creds.baseUrl
                preferences.remove(PreferencesKeys.LAST_SYNC_TIMESTAMP)
            }
        }
    }

    suspend fun updatePlayerEngine(engine: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYER_ENGINE] = engine
        }
    }

    suspend fun updateSyncState(type: String, loaded: Boolean) {
        context.dataStore.edit { preferences ->
            val now = System.currentTimeMillis()
            when(type.lowercase()) {
                "live" -> {
                    preferences[PreferencesKeys.IS_LIVE_LOADED] = loaded
                    if (loaded) preferences[PreferencesKeys.LAST_SYNC_LIVE] = now
                }
                "vod", "movie" -> {
                    preferences[PreferencesKeys.IS_VOD_LOADED] = loaded
                    if (loaded) preferences[PreferencesKeys.LAST_SYNC_VOD] = now
                }
                "series" -> {
                    preferences[PreferencesKeys.IS_SERIES_LOADED] = loaded
                    if (loaded) preferences[PreferencesKeys.LAST_SYNC_SERIES] = now
                }
            }
        }
    }

    suspend fun updateBufferStrategy(strategy: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUFFER_STRATEGY] = strategy
        }
    }

    suspend fun updateUseOledTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_OLED_THEME] = enabled
        }
    }

    suspend fun updateDataSaverMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DATA_SAVER_MODE] = enabled
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            // Clear active session
            preferences.remove(PreferencesKeys.USERNAME)
            preferences.remove(PreferencesKeys.PASSWORD)
            preferences.remove(PreferencesKeys.BASE_URL)
            
            // Mark app as not synchronized for the next session
            preferences.remove(PreferencesKeys.LAST_SYNC_TIMESTAMP)
            
            // Stop fallback to accounts list by setting an invalid index
            preferences[PreferencesKeys.CURRENT_ACCOUNT_INDEX] = -1
            
            // Clear account metadata cache
            preferences.remove(PreferencesKeys.ACCOUNT_STATUS)
            preferences.remove(PreferencesKeys.ACCOUNT_EXP_DATE)
            preferences.remove(PreferencesKeys.ACCOUNT_ACTIVE_CONS)
            preferences.remove(PreferencesKeys.ACCOUNT_MAX_CONNECTIONS)
        }
    }

    suspend fun updateLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun updateSyncInterval(hours: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_INTERVAL_HOURS] = hours
        }
    }

    suspend fun updatePin(pin: String?) {
        context.dataStore.edit { preferences ->
            if (pin == null) preferences.remove(PreferencesKeys.APP_PIN)
            else {
                val encryptedPin = SecurityHelper.encrypt(pin) ?: pin
                preferences[PreferencesKeys.APP_PIN] = encryptedPin
            }
        }
    }

    suspend fun updateAutoPlay(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_PLAY_ENABLED] = enabled
        }
    }

    suspend fun updateDefaultResizeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_RESIZE_MODE] = mode
        }
    }

    suspend fun updateShowDiagnostics(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_DIAGNOSTICS] = enabled
        }
    }

    suspend fun updateCompactMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPACT_MODE] = enabled
        }
    }

    suspend fun updateHideBlockedCategories(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDE_BLOCKED_CATEGORIES] = enabled
        }
    }

    suspend fun updateAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun updateUiZoom(zoom: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UI_ZOOM] = zoom
        }
    }

    suspend fun updateLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = language
        }
    }

    suspend fun updatePreferredAudioLang(lang: String?) {
        context.dataStore.edit { preferences ->
            if (lang == null) preferences.remove(PreferencesKeys.PREFERRED_AUDIO_LANG)
            else preferences[PreferencesKeys.PREFERRED_AUDIO_LANG] = lang
        }
    }

    suspend fun updatePreferredSubtitleLang(lang: String?) {
        context.dataStore.edit { preferences ->
            if (lang == null) preferences.remove(PreferencesKeys.PREFERRED_SUBTITLE_LANG)
            else preferences[PreferencesKeys.PREFERRED_SUBTITLE_LANG] = lang
        }
    }

    suspend fun updateDownloadWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DOWNLOAD_WIFI_ONLY] = enabled
        }
    }

    suspend fun updateSmartDownloads(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMART_DOWNLOADS_ENABLED] = enabled
        }
    }

    suspend fun updateActiveProfileId(profileId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_PROFILE_ID] = profileId
        }
    }

    suspend fun updateDetailedNotifications(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DETAILED_NOTIFICATIONS] = enabled
        }
    }

    suspend fun updateBackgroundPlaybackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_PLAYBACK_ENABLED] = enabled
        }
    }

    suspend fun updateLastCategory(type: String, categoryId: String) {
        context.dataStore.edit { preferences ->
            when (type.lowercase()) {
                "live" -> preferences[PreferencesKeys.LAST_LIVE_CATEGORY] = categoryId
                "movie" -> preferences[PreferencesKeys.LAST_MOVIE_CATEGORY] = categoryId
                "series" -> preferences[PreferencesKeys.LAST_SERIES_CATEGORY] = categoryId
            }
        }
    }

    suspend fun updateLastChannel(type: String, categoryId: String, channelId: String, channelName: String) {
        context.dataStore.edit { preferences ->
            when (type.lowercase()) {
                "live" -> {
                    if (categoryId.isNotBlank()) {
                        preferences[PreferencesKeys.LAST_LIVE_CATEGORY] = categoryId
                    }
                    preferences[PreferencesKeys.LAST_LIVE_CHANNEL_ID] = channelId
                    preferences[PreferencesKeys.LAST_LIVE_CHANNEL_NAME] = channelName

                    if (categoryId.isNotBlank()) {
                        val channelsJson = preferences[PreferencesKeys.LAST_LIVE_CATEGORY_CHANNELS]
                        val namesJson = preferences[PreferencesKeys.LAST_LIVE_CATEGORY_CHANNEL_NAMES]
                        val channelsMap = try {
                            if (channelsJson != null) Json.decodeFromString<MutableMap<String, String>>(channelsJson) else mutableMapOf()
                        } catch (_: Exception) { mutableMapOf() }
                        val namesMap = try {
                            if (namesJson != null) Json.decodeFromString<MutableMap<String, String>>(namesJson) else mutableMapOf()
                        } catch (_: Exception) { mutableMapOf() }

                        channelsMap[categoryId] = channelId
                        namesMap[categoryId] = channelName

                        preferences[PreferencesKeys.LAST_LIVE_CATEGORY_CHANNELS] = Json.encodeToString(channelsMap)
                        preferences[PreferencesKeys.LAST_LIVE_CATEGORY_CHANNEL_NAMES] = Json.encodeToString(namesMap)
                        Timber.i("CATEGORY_SELECTION_SAVED: categoryId=$categoryId, channelId=$channelId, channelName=$channelName")
                    }
                }
            }
        }
    }

    suspend fun updateAccountCache(userInfo: rsv.squitv.data.model.UserInfo) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCOUNT_EXP_DATE] = userInfo.expDate ?: ""
            preferences[PreferencesKeys.ACCOUNT_STATUS] = userInfo.status ?: ""
            preferences[PreferencesKeys.ACCOUNT_ACTIVE_CONS] = userInfo.activeCons ?: ""
            preferences[PreferencesKeys.ACCOUNT_MAX_CONNECTIONS] = userInfo.maxConnections ?: ""
        }
    }

    suspend fun updateLastUpdateCheck(timestamp: Long, availableVersion: String?) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIMESTAMP] = timestamp
            if (availableVersion == null) {
                preferences.remove(PreferencesKeys.LAST_AVAILABLE_VERSION)
            } else {
                preferences[PreferencesKeys.LAST_AVAILABLE_VERSION] = availableVersion
            }
        }
    }

    suspend fun updateLastNotifiedVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_NOTIFIED_VERSION] = version
        }
    }

    suspend fun updateIgnoredVersion(version: String?) {
        context.dataStore.edit { preferences ->
            if (version == null) {
                preferences.remove(PreferencesKeys.IGNORED_VERSION)
            } else {
                preferences[PreferencesKeys.IGNORED_VERSION] = version
            }
        }
    }
}
