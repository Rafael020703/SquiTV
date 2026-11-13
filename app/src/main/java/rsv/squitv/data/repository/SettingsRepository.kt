package rsv.squitv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import rsv.squitv.data.model.SettingsBackup
import rsv.squitv.data.model.SettingsBackupCodec
import rsv.squitv.data.model.UserInfo
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.util.SecurityHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.onStart
import rsv.squitv.domain.model.UserProfile
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

    private val storageMigrationMutex = Mutex()
    private var storageMigrated = false
    private val profileJson = Json { ignoreUnknownKeys = true }

    private suspend fun ensureStorageMigration() = storageMigrationMutex.withLock {
        if (!storageMigrated) {
            migrateToEncryptedStorage()
            storageMigrated = true
        }
    }

    private suspend fun migrateToEncryptedStorage() {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                // 1. Migrate single password
                val currentPassword = preferences[PreferencesKeys.PASSWORD]
                if (currentPassword != null && !SecurityHelper.isEncrypted(currentPassword)) {
                    SecurityHelper.encrypt(currentPassword)?.let { encrypted ->
                        preferences[PreferencesKeys.PASSWORD] = encrypted
                    }
                }

                // Migrate accounts without changing their identity.
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
        val GROUP_LIVE_CHANNELS = booleanPreferencesKey("group_live_channels")
        val PREFERRED_CHANNEL_QUALITY = stringPreferencesKey("preferred_channel_quality")
        val AUTO_PLAY_ENABLED = booleanPreferencesKey("auto_play_enabled")
        val ACCOUNTS = stringPreferencesKey("accounts")
        val CURRENT_ACCOUNT_INDEX = intPreferencesKey("current_account_index")
        val IS_LIVE_LOADED = booleanPreferencesKey("is_live_loaded")
        val IS_VOD_LOADED = booleanPreferencesKey("is_vod_loaded")
        val IS_SERIES_LOADED = booleanPreferencesKey("is_series_loaded")
        val LAST_SYNC_LIVE = longPreferencesKey("last_sync_live")
        val LAST_SYNC_VOD = longPreferencesKey("last_sync_vod")
        val LAST_SYNC_SERIES = longPreferencesKey("last_sync_series")
        val UI_ZOOM = floatPreferencesKey("ui_zoom")
        val LANGUAGE = stringPreferencesKey("language")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val SMART_DOWNLOADS_ENABLED = booleanPreferencesKey("smart_downloads_enabled")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val BACKGROUND_PLAYBACK_ENABLED = booleanPreferencesKey("background_playback_enabled")
        val PIP_AUTO_ENTER_ENABLED = booleanPreferencesKey("pip_auto_enter_enabled")
        val LAST_SYNC_EPG = longPreferencesKey("last_sync_epg")
        
        val LAST_LIVE_CATEGORY = stringPreferencesKey("last_live_category")
        val LAST_LIVE_CHANNEL_ID = stringPreferencesKey("last_live_channel_id")
        val LAST_LIVE_CHANNEL_NAME = stringPreferencesKey("last_live_channel_name")
        val LAST_LIVE_CATEGORY_CHANNELS = stringPreferencesKey("last_live_category_channels")
        val LAST_LIVE_CATEGORY_CHANNEL_NAMES = stringPreferencesKey("last_live_category_channel_names")
        val LAST_MOVIE_CATEGORY = stringPreferencesKey("last_movie_category")
        val LAST_SERIES_CATEGORY = stringPreferencesKey("last_series_category")
        val CATALOG_BROWSE_MEMORY = stringPreferencesKey("catalog_browse_memory_v1")
        
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

        // Customization
        val DISPLAY_NAMES_MAP = stringPreferencesKey("display_names_map")
        val PORTAL_DISPLAY_NAME = stringPreferencesKey("portal_display_name")
        val PORTAL_SYNC_SESSION = stringPreferencesKey("portal_sync_session")
        val LOCAL_PROFILES = stringPreferencesKey("local_profiles_v1")
        val PROFILE_PICTURE = stringPreferencesKey("profile_picture")
        val HIDDEN_SECTIONS = stringSetPreferencesKey("hidden_sections")
        val CATEGORY_ORDER_MAP = stringPreferencesKey("category_order_map")
        val HIDDEN_CATEGORIES_MAP = stringPreferencesKey("hidden_categories_map")
        val MOVIE_CATEGORY_MODE_MAP = stringPreferencesKey("movie_category_mode_map")
        val SERIES_CATEGORY_MODE_MAP = stringPreferencesKey("series_category_mode_map")
        val MOVIE_CATEGORY_PROMPT_DISMISSED_MAP = stringPreferencesKey("movie_category_prompt_dismissed_map")
        val SERIES_CATEGORY_PROMPT_DISMISSED_MAP = stringPreferencesKey("series_category_prompt_dismissed_map")
        val PREPARATION_SPEED = stringPreferencesKey("preparation_speed")
    }

    data class AppSettings(
        val credentials: XtreamCredentials?,
        val accounts: List<XtreamCredentials> = emptyList(),
        val currentAccountIndex: Int = 0,
        val isLiveLoaded: Boolean = false,
        val isVodLoaded: Boolean = false,
        val isSeriesLoaded: Boolean = false,
        val lastSyncLive: Long = 0L,
        val lastSyncVod: Long = 0L,
        val lastSyncSeries: Long = 0L,
        val uiZoom: Float = 1.0f,
        val language: String = "auto",
        val lastSyncTimestamp: Long,
        val syncIntervalHours: Int,
        val groupLiveChannels: Boolean = true,
        val preferredChannelQuality: String = "AUTO",
        val autoPlayEnabled: Boolean = true,
        val downloadWifiOnly: Boolean = true,
        val smartDownloadsEnabled: Boolean = true,
        val preferredAudioLang: String? = null,
        val preferredSubtitleLang: String? = null,
        val activeProfileId: String = "default",
        val profiles: List<UserProfile> = listOf(UserProfile("default", "Usuário 1")),
        val profileImageUrl: String? = null,
        val backgroundPlaybackEnabled: Boolean = false,
        val pipAutoEnterEnabled: Boolean = true,
        val lastSyncEpg: Long = 0L,
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
        val ignoredVersion: String? = null,
        val displayNamesMap: Map<String, String> = emptyMap(),
        val hiddenSections: Set<String> = emptySet(),
        val categoryOrderMap: Map<String, List<String>> = emptyMap(),
        val hiddenCategoriesMap: Map<String, Set<String>> = emptyMap(),
        val movieCategoryModeMap: Map<String, String> = emptyMap(),
        val seriesCategoryModeMap: Map<String, String> = emptyMap(),
        val moviePromptDismissedMap: Map<String, Boolean> = emptyMap(),
        val seriesPromptDismissedMap: Map<String, Boolean> = emptyMap(),
        val preparationSpeed: String = "NORMAL",
        val browseMemory: rsv.squitv.shared.catalog.CatalogBrowseMemory = rsv.squitv.shared.catalog.CatalogBrowseMemory(),
        val portalDisplayName: String? = null
    ) {
        fun catalogPosition(type: String) = browseMemory.position(
            rsv.squitv.core.data.repository.PlaybackSourceRepository.scope(this), type)
        val currentDisplayName: String?
            get() = credentials?.username?.lowercase()?.let { displayNamesMap[it] }

        fun getMovieCategoryMode(accountKey: String, profileId: String): rsv.squitv.domain.model.CategoryMode {
            val key = "${accountKey}_$profileId"
            return rsv.squitv.domain.model.CategoryMode.fromString(movieCategoryModeMap[key])
        }

        fun getSeriesCategoryMode(accountKey: String, profileId: String): rsv.squitv.domain.model.CategoryMode {
            val key = "${accountKey}_$profileId"
            return rsv.squitv.domain.model.CategoryMode.fromString(seriesCategoryModeMap[key])
        }

        fun isMoviePromptDismissed(accountKey: String, profileId: String): Boolean {
            val key = "${accountKey}_$profileId"
            return moviePromptDismissedMap[key] == true
        }

        fun isSeriesPromptDismissed(accountKey: String, profileId: String): Boolean {
            val key = "${accountKey}_$profileId"
            return seriesPromptDismissedMap[key] == true
        }
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.onStart { ensureStorageMigration() }.map(::readSettings)

    private fun readSettings(preferences: Preferences): AppSettings {
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
        
        val isLiveLoaded = preferences[PreferencesKeys.IS_LIVE_LOADED] ?: false
        val isVodLoaded = preferences[PreferencesKeys.IS_VOD_LOADED] ?: false
        val isSeriesLoaded = preferences[PreferencesKeys.IS_SERIES_LOADED] ?: false
        val lastSyncLive = preferences[PreferencesKeys.LAST_SYNC_LIVE] ?: 0L
        val lastSyncVod = preferences[PreferencesKeys.LAST_SYNC_VOD] ?: 0L
        val lastSyncSeries = preferences[PreferencesKeys.LAST_SYNC_SERIES] ?: 0L
        val zoom = preferences[PreferencesKeys.UI_ZOOM] ?: 1.0f
        val language = preferences[PreferencesKeys.LANGUAGE] ?: "auto"
        val downloadWifiOnly = preferences[PreferencesKeys.DOWNLOAD_WIFI_ONLY] ?: true
        val smartDownloads = preferences[PreferencesKeys.SMART_DOWNLOADS_ENABLED] ?: true

        val username = preferences[PreferencesKeys.USERNAME]
        val encryptedPassword = preferences[PreferencesKeys.PASSWORD]
        val password = SecurityHelper.decrypt(encryptedPassword)
        val baseUrl = preferences[PreferencesKeys.BASE_URL]
        val lastSync = preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L
        val interval = preferences[PreferencesKeys.SYNC_INTERVAL_HOURS] ?: 24
        
        val autoPlay = preferences[PreferencesKeys.AUTO_PLAY_ENABLED] ?: true
        val audioLang = preferences[PreferencesKeys.PREFERRED_AUDIO_LANG]
        val subLang = preferences[PreferencesKeys.PREFERRED_SUBTITLE_LANG]
        val profileId = preferences[PreferencesKeys.ACTIVE_PROFILE_ID] ?: "default"
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

        val displayNamesJson = preferences[PreferencesKeys.DISPLAY_NAMES_MAP]
        val displayNamesMap = try {
            if (displayNamesJson != null) Json.decodeFromString<Map<String, String>>(displayNamesJson) else emptyMap()
        } catch (_: Exception) { emptyMap() }
        val hiddenSections = (preferences[PreferencesKeys.HIDDEN_SECTIONS] ?: emptySet()) - "EPG"

        val categoryOrderJson = preferences[PreferencesKeys.CATEGORY_ORDER_MAP]
        val categoryOrderMap = try {
            if (categoryOrderJson != null) Json.decodeFromString<Map<String, List<String>>>(categoryOrderJson) else emptyMap()
        } catch (_: Exception) { emptyMap() }

        val hiddenCategoriesJson = preferences[PreferencesKeys.HIDDEN_CATEGORIES_MAP]
        val hiddenCategoriesMap = try {
            if (hiddenCategoriesJson != null) Json.decodeFromString<Map<String, Set<String>>>(hiddenCategoriesJson) else emptyMap()
        } catch (_: Exception) { emptyMap() }

        val movieCategoryModeJson = preferences[PreferencesKeys.MOVIE_CATEGORY_MODE_MAP]
        val movieCategoryModeMap = try {
            if (movieCategoryModeJson != null) Json.decodeFromString<Map<String, String>>(movieCategoryModeJson) else emptyMap()
        } catch (_: Exception) { emptyMap() }

        val seriesCategoryModeJson = preferences[PreferencesKeys.SERIES_CATEGORY_MODE_MAP]
        val seriesCategoryModeMap = try {
            if (seriesCategoryModeJson != null) Json.decodeFromString<Map<String, String>>(seriesCategoryModeJson) else emptyMap()
        } catch (_: Exception) { emptyMap() }

        val moviePromptDismissedJson = preferences[PreferencesKeys.MOVIE_CATEGORY_PROMPT_DISMISSED_MAP]
        val moviePromptDismissedMap = try {
            if (moviePromptDismissedJson != null) Json.decodeFromString<Map<String, Boolean>>(moviePromptDismissedJson) else emptyMap()
        } catch (_: Exception) { emptyMap() }

        val seriesPromptDismissedJson = preferences[PreferencesKeys.SERIES_CATEGORY_PROMPT_DISMISSED_MAP]
        val seriesPromptDismissedMap = try {
            if (seriesPromptDismissedJson != null) Json.decodeFromString<Map<String, Boolean>>(seriesPromptDismissedJson) else emptyMap()
        } catch (_: Exception) { emptyMap() }

        val creds = if (username != null && password != null && baseUrl != null) {
            XtreamCredentials(username, password, baseUrl)
        } else if (accounts.isNotEmpty() && currentIndex >= 0 && currentIndex < accounts.size) {
            accounts[currentIndex]
        } else {
            null
        }
        
        return AppSettings(
            browseMemory = try { decodeBrowseMemory(preferences) } catch (_: Exception) {
                rsv.squitv.shared.catalog.CatalogBrowseMemory()
            },
            credentials = creds,
            accounts = accounts,
            currentAccountIndex = currentIndex,
            isLiveLoaded = isLiveLoaded,
            isVodLoaded = isVodLoaded,
            isSeriesLoaded = isSeriesLoaded,
            lastSyncLive = lastSyncLive,
            lastSyncVod = lastSyncVod,
            lastSyncSeries = lastSyncSeries,
            uiZoom = zoom,
            language = language,
            lastSyncTimestamp = lastSync,
            syncIntervalHours = interval,
            groupLiveChannels = preferences[PreferencesKeys.GROUP_LIVE_CHANNELS] ?: true,
            preferredChannelQuality = rsv.squitv.domain.model.ChannelQuality.fromPreference(preferences[PreferencesKeys.PREFERRED_CHANNEL_QUALITY] ?: "AUTO").name,
            autoPlayEnabled = autoPlay,
            downloadWifiOnly = downloadWifiOnly,
            smartDownloadsEnabled = smartDownloads,
            preferredAudioLang = audioLang,
            preferredSubtitleLang = subLang,
            activeProfileId = profileId,
            profiles = readProfiles(preferences),
            profileImageUrl = preferences[PreferencesKeys.PROFILE_PICTURE],
            backgroundPlaybackEnabled = bgPlaybackEnabled,
            pipAutoEnterEnabled = preferences[PreferencesKeys.PIP_AUTO_ENTER_ENABLED] ?: true,
            lastSyncEpg = preferences[PreferencesKeys.LAST_SYNC_EPG] ?: 0L,
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
            ignoredVersion = ignoredVersion,
            displayNamesMap = displayNamesMap,
            portalDisplayName = preferences[PreferencesKeys.PORTAL_DISPLAY_NAME],
            hiddenSections = hiddenSections,
            categoryOrderMap = categoryOrderMap,
            hiddenCategoriesMap = hiddenCategoriesMap,
            movieCategoryModeMap = movieCategoryModeMap,
            seriesCategoryModeMap = seriesCategoryModeMap,
            moviePromptDismissedMap = moviePromptDismissedMap,
            seriesPromptDismissedMap = seriesPromptDismissedMap,
            preparationSpeed = preferences[PreferencesKeys.PREPARATION_SPEED] ?: "NORMAL"
        )
    }

    val credentialsFlow: Flow<XtreamCredentials?> = settingsFlow.map { it.credentials }

    internal suspend fun applyCloudProfileName(accountId: String, profileId: String, name: String) {
        require(name.isNotBlank() && name.length <= 80)
        context.dataStore.edit { prefs ->
            val settings = readSettings(prefs)
            val key = settings.credentials?.let(SettingsBackupCodec::accountKey) ?: return@edit
            if (SettingsBackupCodec.fingerprint(key) != accountId || settings.activeProfileId != profileId) return@edit
            val profile = settings.profiles.firstOrNull { it.id == profileId } ?: return@edit
            if (profile.name != name) {
                prefs[PreferencesKeys.LOCAL_PROFILES] = Json.encodeToString(
                    settings.profiles.map { if (it.id == profileId) it.copy(name = name) else it },
                )
            }
        }
    }

    internal fun cloudCategories(
        settings: AppSettings,
        accountKey: String,
        profileId: String,
    ): List<rsv.squitv.shared.account.CloudCategories> =
        listOf("live", "movie", "series").map { kind ->
            val localType = if (kind == "movie") "vod" else kind
            val key = "${accountKey.lowercase().trim()}_$localType"
            val mode = when (kind) {
                "movie" -> settings.getMovieCategoryMode(accountKey, profileId).name.lowercase()
                "series" -> settings.getSeriesCategoryMode(accountKey, profileId).name.lowercase()
                else -> "provider"
            }
            rsv.squitv.shared.account.CloudCategories(
                kind, mode, settings.categoryOrderMap[key].orEmpty(), settings.hiddenCategoriesMap[key].orEmpty().sorted(),
            )
        }

    internal suspend fun applyCloudCategories(
        accountId: String,
        profileId: String,
        expected: List<rsv.squitv.shared.account.CloudCategories>,
        rows: List<rsv.squitv.shared.account.CloudCategories>,
    ): Boolean {
        var applied = false
        context.dataStore.edit { prefs ->
            val settings = readSettings(prefs)
            val accountKey = settings.credentials?.let(SettingsBackupCodec::accountKey) ?: return@edit
            if (SettingsBackupCodec.fingerprint(accountKey) != accountId || settings.activeProfileId != profileId ||
                cloudCategories(settings, accountKey, profileId) != expected) return@edit
            val order = readOrderMap(prefs)
            val hidden = readHiddenMap(prefs)
            val modeKey = "${accountKey}_$profileId"
            for (row in rows) {
                val localType = if (row.kind == "movie") "vod" else row.kind
                val key = "${accountKey.lowercase().trim()}_$localType"
                order[key] = row.order
                hidden[key] = row.hidden.toSet()
                when (row.kind) {
                    "movie" -> prefs[PreferencesKeys.MOVIE_CATEGORY_MODE_MAP] =
                        Json.encodeToString(settings.movieCategoryModeMap + (modeKey to row.mode.uppercase()))
                    "series" -> prefs[PreferencesKeys.SERIES_CATEGORY_MODE_MAP] =
                        Json.encodeToString(settings.seriesCategoryModeMap + (modeKey to row.mode.uppercase()))
                }
            }
            prefs[PreferencesKeys.CATEGORY_ORDER_MAP] = Json.encodeToString(order)
            prefs[PreferencesKeys.HIDDEN_CATEGORIES_MAP] = Json.encodeToString(hidden)
            applied = true
        }
        return applied
    }

    internal suspend fun loadFavoriteCheckpoint(key: String): rsv.squitv.shared.account.FavoriteCheckpoint? =
        withContext(Dispatchers.IO) {
            val file = favoriteCheckpointFile(key)
            if (!file.baseFile.exists() && !java.io.File(file.baseFile.path + ".bak").exists()) return@withContext null
            file.openRead().use { input ->
                val bytes = input.readBytes()
                require(bytes.size <= 2 * 1024 * 1024)
                Json.decodeFromString<rsv.squitv.shared.account.FavoriteCheckpoint>(bytes.toString(Charsets.UTF_8))
            }
        }

    internal suspend fun saveFavoriteCheckpoint(key: String, value: rsv.squitv.shared.account.FavoriteCheckpoint) =
        withContext(Dispatchers.IO) {
            val file = favoriteCheckpointFile(key)
            val bytes = Json.encodeToString(value).toByteArray(Charsets.UTF_8)
            require(bytes.size <= 2 * 1024 * 1024)
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
        }

    internal suspend fun loadCloudProgress(key: String): rsv.squitv.shared.account.CloudProgressJournal =
        withContext(Dispatchers.IO) {
            val file = favoriteCheckpointFile(key, "-progress")
            if (!file.baseFile.exists() && !java.io.File(file.baseFile.path + ".bak").exists())
                return@withContext rsv.squitv.shared.account.CloudProgressJournal()
            require(file.baseFile.length() <= 4 * 1024 * 1024)
            file.openRead().use { input ->
                Json.decodeFromString<rsv.squitv.shared.account.CloudProgressJournal>(input.readBytes().toString(Charsets.UTF_8))
            }
        }

    internal suspend fun saveCloudProgress(key: String, value: rsv.squitv.shared.account.CloudProgressJournal) =
        withContext(Dispatchers.IO) {
            val file = favoriteCheckpointFile(key, "-progress")
            val bytes = Json.encodeToString(value).toByteArray(Charsets.UTF_8)
            require(bytes.size <= 4 * 1024 * 1024)
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
        }

    private fun favoriteCheckpointFile(key: String, suffix: String = ""): android.util.AtomicFile {
        require(key.matches(Regex("[a-f0-9]{64}")))
        return android.util.AtomicFile(java.io.File(context.filesDir, "favorite-sync/$key$suffix.json"))
    }

    internal val portalSyncSessionFlow = context.dataStore.data.map { preferences ->
        val encrypted = preferences[PreferencesKeys.PORTAL_SYNC_SESSION] ?: return@map null
        val user = preferences[PreferencesKeys.USERNAME] ?: return@map null
        val url = preferences[PreferencesKeys.BASE_URL] ?: return@map null
        val account = SettingsBackupCodec.accountKey(XtreamCredentials(user, "", url))
        account to encrypted
    }.distinctUntilChanged().map { entry ->
        if (entry == null) null else withContext(Dispatchers.IO) {
            val (account, encrypted) = entry
            if (SecurityHelper.isEncrypted(encrypted)) {
                SettingsBackupCodec.fingerprint(account) to SecurityHelper.decrypt(encrypted)
            } else null
        }
    }

    suspend fun saveSessionCredentials(
        credentials: XtreamCredentials,
        portalDisplayName: String? = null,
        portalSyncSession: String? = null,
    ) {
        context.dataStore.edit { preferences ->
            preferences.writeActiveSession(encryptCredentials(credentials), portalDisplayName, portalSyncSession)
        }
    }

    private fun encryptCredentials(credentials: XtreamCredentials): XtreamCredentials =
        credentials.copy(password = SecurityHelper.encrypt(credentials.password)
            ?: throw IllegalStateException("Não foi possível proteger a senha neste aparelho."))

    private fun MutablePreferences.writeActiveSession(
        encrypted: XtreamCredentials,
        portalDisplayName: String?,
        portalSyncSession: String?,
    ) {
        val encryptedSession = portalSyncSession?.let {
            SecurityHelper.encrypt(it) ?: throw IllegalStateException("Não foi possível proteger a sessão da conta.")
        }
        this[PreferencesKeys.USERNAME] = encrypted.username
        this[PreferencesKeys.PASSWORD] = encrypted.password
        this[PreferencesKeys.BASE_URL] = encrypted.baseUrl
        if (portalDisplayName.isNullOrBlank()) remove(PreferencesKeys.PORTAL_DISPLAY_NAME)
        else this[PreferencesKeys.PORTAL_DISPLAY_NAME] = portalDisplayName
        if (encryptedSession == null) remove(PreferencesKeys.PORTAL_SYNC_SESSION)
        else this[PreferencesKeys.PORTAL_SYNC_SESSION] = encryptedSession
    }

    suspend fun isAccountSaved(credentials: XtreamCredentials): Boolean {
        val settings = settingsFlow.first()
        return settings.accounts.any {
            it.username.equals(credentials.username, ignoreCase = true) &&
            it.baseUrl.equals(credentials.baseUrl, ignoreCase = true)
        }
    }

    suspend fun saveAccountToSavedList(credentials: XtreamCredentials) {
        context.dataStore.edit { preferences ->
            preferences.writeSavedAccount(encryptCredentials(credentials))
        }
    }

    private fun MutablePreferences.writeSavedAccount(encryptedCreds: XtreamCredentials) {
        val accountsJson = this[PreferencesKeys.ACCOUNTS]
        val accounts = try {
            if (accountsJson != null) Json.decodeFromString<List<XtreamCredentials>>(accountsJson).toMutableList()
            else mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }

        val index = accounts.indexOfFirst {
            it.username.equals(encryptedCreds.username, ignoreCase = true) &&
            it.baseUrl.equals(encryptedCreds.baseUrl, ignoreCase = true)
        }
        if (index == -1) {
            accounts.add(encryptedCreds)
            this[PreferencesKeys.CURRENT_ACCOUNT_INDEX] = accounts.lastIndex
        } else {
            accounts[index] = encryptedCreds
            this[PreferencesKeys.CURRENT_ACCOUNT_INDEX] = index
        }
        this[PreferencesKeys.ACCOUNTS] = Json.encodeToString(accounts)
    }

    suspend fun saveCredentials(
        credentials: XtreamCredentials,
        portalDisplayName: String? = null,
        portalSyncSession: String? = null,
    ) {
        context.dataStore.edit { preferences ->
            val encrypted = encryptCredentials(credentials)
            preferences.writeActiveSession(encrypted, portalDisplayName, portalSyncSession)
            preferences.writeSavedAccount(encrypted)
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
                    preferences.remove(PreferencesKeys.PORTAL_DISPLAY_NAME)
                    preferences.remove(PreferencesKeys.PORTAL_SYNC_SESSION)
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
                preferences.remove(PreferencesKeys.PORTAL_DISPLAY_NAME)
                preferences.remove(PreferencesKeys.PORTAL_SYNC_SESSION)
                val creds = accounts[index]
                preferences[PreferencesKeys.USERNAME] = creds.username
                preferences[PreferencesKeys.PASSWORD] = creds.password
                preferences[PreferencesKeys.BASE_URL] = creds.baseUrl
                preferences.remove(PreferencesKeys.LAST_SYNC_TIMESTAMP)
                
                // Clear stale account metadata from previous session
                preferences.remove(PreferencesKeys.ACCOUNT_STATUS)
                preferences.remove(PreferencesKeys.ACCOUNT_EXP_DATE)
                preferences.remove(PreferencesKeys.ACCOUNT_ACTIVE_CONS)
                preferences.remove(PreferencesKeys.ACCOUNT_MAX_CONNECTIONS)
            }
        }
    }

    suspend fun updateSyncState(type: String, loaded: Boolean) {
        context.dataStore.edit { preferences ->
            val now = System.currentTimeMillis()
            when(type.lowercase()) {
                "epg" -> if (loaded) preferences[PreferencesKeys.LAST_SYNC_EPG] = now
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

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            // Clear active session
            preferences.remove(PreferencesKeys.PORTAL_DISPLAY_NAME)
            preferences.remove(PreferencesKeys.PORTAL_SYNC_SESSION)
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

    suspend fun clearSyncTimestamps() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_EPG] = 0L
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = 0L
            preferences[PreferencesKeys.LAST_SYNC_LIVE] = 0L
            preferences[PreferencesKeys.LAST_SYNC_VOD] = 0L
            preferences[PreferencesKeys.LAST_SYNC_SERIES] = 0L
            preferences[PreferencesKeys.IS_LIVE_LOADED] = false
            preferences[PreferencesKeys.IS_VOD_LOADED] = false
            preferences[PreferencesKeys.IS_SERIES_LOADED] = false
        }
    }

    suspend fun updateSyncInterval(hours: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_INTERVAL_HOURS] = hours
        }
    }

    suspend fun updateGroupLiveChannels(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.GROUP_LIVE_CHANNELS] = enabled }
    }

    suspend fun updatePreferredChannelQuality(quality: String) {
        require(rsv.squitv.domain.model.ChannelQuality.entries.any { it.name == quality })
        context.dataStore.edit { it[PreferencesKeys.PREFERRED_CHANNEL_QUALITY] = quality }
    }

    suspend fun updateAutoPlay(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_PLAY_ENABLED] = enabled
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

    suspend fun updateBackgroundPlaybackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_PLAYBACK_ENABLED] = enabled
        }
    }

    suspend fun updatePipAutoEnterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.PIP_AUTO_ENTER_ENABLED] = enabled }
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

    private fun decodeBrowseMemory(preferences: Preferences): rsv.squitv.shared.catalog.CatalogBrowseMemory {
        val raw = preferences[PreferencesKeys.CATALOG_BROWSE_MEMORY] ?: return rsv.squitv.shared.catalog.CatalogBrowseMemory()
        require(raw.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        return profileJson.decodeFromString<rsv.squitv.shared.catalog.CatalogBrowseMemory>(raw).also { it.validate() }
    }

    suspend fun rememberCatalogPosition(scope: String, type: String, categoryId: String, itemId: String? = null) {
        try {
            context.dataStore.edit { preferences ->
                // A delayed focus event must never be assigned to the next account/profile.
                if (rsv.squitv.core.data.repository.PlaybackSourceRepository.scope(readSettings(preferences)) != scope) return@edit
                val previous = decodeBrowseMemory(preferences)
                val next = previous.record(scope, type, categoryId, itemId)
                if (next != previous) preferences[PreferencesKeys.CATALOG_BROWSE_MEMORY] = Json.encodeToString(next)
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { Timber.w(error, "Não foi possível salvar a posição do catálogo") }
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

    suspend fun updateCustomDisplayName(username: String, baseUrl: String? = null, name: String?) {
        context.dataStore.edit { preferences ->
            val mapJson = preferences[PreferencesKeys.DISPLAY_NAMES_MAP]
            val map = try {
                if (mapJson != null) Json.decodeFromString<MutableMap<String, String>>(mapJson) else mutableMapOf()
            } catch (_: Exception) { mutableMapOf() }
            
            val simpleKey = username.trim().lowercase()
            val cleanUrl = baseUrl?.trim()?.lowercase()?.removeSuffix("/")
            val accountKey = if (!cleanUrl.isNullOrBlank()) "$cleanUrl#$simpleKey" else simpleKey
            
            val trimmedValue = name?.trim()
            if (trimmedValue.isNullOrBlank()) {
                map.remove(accountKey)
                if (accountKey == simpleKey) {
                    map.remove(simpleKey)
                }
            } else {
                map[accountKey] = trimmedValue
            }
            preferences[PreferencesKeys.DISPLAY_NAMES_MAP] = Json.encodeToString(map)
        }
    }

    suspend fun updateHiddenSections(hidden: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDDEN_SECTIONS] = hidden - "EPG"
        }
    }

    suspend fun resetHiddenSections() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.HIDDEN_SECTIONS)
        }
    }

    suspend fun updateCategoryOrder(accountKey: String, contentType: String, order: List<String>) {
        context.dataStore.edit { preferences ->
            val map = readOrderMap(preferences)
            val key = "${accountKey.lowercase().trim()}_${contentType.lowercase().trim()}"
            map[key] = order
            preferences[PreferencesKeys.CATEGORY_ORDER_MAP] = Json.encodeToString(map)
        }
    }

    suspend fun updateHiddenCategories(accountKey: String, contentType: String, hiddenSet: Set<String>) {
        context.dataStore.edit { preferences ->
            val map = readHiddenMap(preferences)
            val key = "${accountKey.lowercase().trim()}_${contentType.lowercase().trim()}"
            map[key] = hiddenSet
            preferences[PreferencesKeys.HIDDEN_CATEGORIES_MAP] = Json.encodeToString(map)
        }
    }

    suspend fun resetCategoryCustomization(accountKey: String, contentType: String) {
        context.dataStore.edit { preferences ->
            val orderMap = readOrderMap(preferences)
            val hiddenMap = readHiddenMap(preferences)

            val key = "${accountKey.lowercase().trim()}_${contentType.lowercase().trim()}"
            orderMap.remove(key)
            hiddenMap.remove(key)

            preferences[PreferencesKeys.CATEGORY_ORDER_MAP] = Json.encodeToString(orderMap)
            preferences[PreferencesKeys.HIDDEN_CATEGORIES_MAP] = Json.encodeToString(hiddenMap)
        }
    }

    private fun readOrderMap(preferences: Preferences): MutableMap<String, List<String>> =
        preferences[PreferencesKeys.CATEGORY_ORDER_MAP]?.let { Json.decodeFromString(it) } ?: mutableMapOf()

    private fun readHiddenMap(preferences: Preferences): MutableMap<String, Set<String>> =
        preferences[PreferencesKeys.HIDDEN_CATEGORIES_MAP]?.let { Json.decodeFromString(it) } ?: mutableMapOf()

    private fun readDisplayNamesMap(preferences: Preferences): MutableMap<String, String> =
        preferences[PreferencesKeys.DISPLAY_NAMES_MAP]?.let { Json.decodeFromString(it) } ?: mutableMapOf()

    suspend fun exportSettingsBackup(): String {
        ensureStorageMigration()
        val preferences = context.dataStore.data.first()
        val snapshot = readSettings(preferences).copy(
            categoryOrderMap = readOrderMap(preferences), hiddenCategoriesMap = readHiddenMap(preferences),
            displayNamesMap = readDisplayNamesMap(preferences))
        return SettingsBackupCodec.encode(SettingsBackupCodec.create(snapshot))
    }

    /** A single DataStore transaction; Room favourites/history/downloads and login data are untouched. */
    suspend fun importSettingsBackup(backup: SettingsBackup): Int {
        SettingsBackupCodec.validate(backup)
        var matched = 0
        context.dataStore.edit { preferences ->
            val current = readSettings(preferences)
            val configured = SettingsBackupCodec.configuredAccounts(current)
            val orders = readOrderMap(preferences)
            val hidden = readHiddenMap(preferences)
            val names = readDisplayNamesMap(preferences)
            backup.accounts.forEach { account ->
                val key = configured[account.fingerprint] ?: return@forEach
                matched++
                account.categories.forEach { (type, categories) ->
                    val categoryKey = "${key}_$type"
                    categories.order?.let { orders[categoryKey] = it }
                    categories.hidden?.let { hidden[categoryKey] = it }
                }
                account.displayName?.let { if (it.isBlank()) names.remove(key) else names[key] = it }
            }
            with(backup.preferences) {
                groupLiveChannels?.let { preferences[PreferencesKeys.GROUP_LIVE_CHANNELS] = it }
                preferredChannelQuality?.let { preferences[PreferencesKeys.PREFERRED_CHANNEL_QUALITY] = it }
                uiZoom?.let { preferences[PreferencesKeys.UI_ZOOM] = it }
                language?.let { preferences[PreferencesKeys.LANGUAGE] = it }
                syncIntervalHours?.let { preferences[PreferencesKeys.SYNC_INTERVAL_HOURS] = it }
                autoPlayEnabled?.let { preferences[PreferencesKeys.AUTO_PLAY_ENABLED] = it }
                downloadWifiOnly?.let { preferences[PreferencesKeys.DOWNLOAD_WIFI_ONLY] = it }
                smartDownloadsEnabled?.let { preferences[PreferencesKeys.SMART_DOWNLOADS_ENABLED] = it }
                preferredAudioLang?.let { if (it.isEmpty()) preferences.remove(PreferencesKeys.PREFERRED_AUDIO_LANG) else preferences[PreferencesKeys.PREFERRED_AUDIO_LANG] = it }
                preferredSubtitleLang?.let { if (it.isEmpty()) preferences.remove(PreferencesKeys.PREFERRED_SUBTITLE_LANG) else preferences[PreferencesKeys.PREFERRED_SUBTITLE_LANG] = it }
                backgroundPlaybackEnabled?.let { preferences[PreferencesKeys.BACKGROUND_PLAYBACK_ENABLED] = it }
                pipAutoEnterEnabled?.let { preferences[PreferencesKeys.PIP_AUTO_ENTER_ENABLED] = it }
            }
            backup.profiles?.let { incoming ->
                val profiles = readProfiles(preferences).associateBy { it.id }.toMutableMap()
                incoming.forEach { profile ->
                    profiles[profile.id] = UserProfile(profile.id, profile.name, profile.iconUrl, profile.isChild)
                }
                preferences[PreferencesKeys.LOCAL_PROFILES] = Json.encodeToString(profiles.values.toList())
                backup.activeProfileId?.takeIf { it in profiles }?.let { preferences[PreferencesKeys.ACTIVE_PROFILE_ID] = it }
                backup.profileImageUrl?.let { preferences[PreferencesKeys.PROFILE_PICTURE] = it }
            }
            backup.hiddenSections?.let { preferences[PreferencesKeys.HIDDEN_SECTIONS] = it - "EPG" }
            preferences[PreferencesKeys.CATEGORY_ORDER_MAP] = Json.encodeToString(orders)
            preferences[PreferencesKeys.HIDDEN_CATEGORIES_MAP] = Json.encodeToString(hidden)
            preferences[PreferencesKeys.DISPLAY_NAMES_MAP] = Json.encodeToString(names)
        }
        return matched
    }

    suspend fun moveCategory(accountKey: String, contentType: String, categoryId: String,
        availableIds: List<String>, offset: Int = 0, position: Int? = null) {
        context.dataStore.edit { preferences ->
            val orders = readOrderMap(preferences)
            val key = "${accountKey.lowercase().trim()}_${contentType.lowercase().trim()}"
            orders[key] = rsv.squitv.shared.catalog.CategoryCustomization.move(
                orders[key].orEmpty(), availableIds, categoryId, offset, position) ?: return@edit
            preferences[PreferencesKeys.CATEGORY_ORDER_MAP] = Json.encodeToString(orders)
        }
    }

    suspend fun toggleHiddenCategory(accountKey: String, contentType: String, categoryId: String) {
        context.dataStore.edit { preferences ->
            val hidden = readHiddenMap(preferences)
            val key = "${accountKey.lowercase().trim()}_${contentType.lowercase().trim()}"
            val values = hidden[key].orEmpty().toMutableSet()
            if (!values.add(categoryId)) values.remove(categoryId)
            hidden[key] = values
            preferences[PreferencesKeys.HIDDEN_CATEGORIES_MAP] = Json.encodeToString(hidden)
        }
    }

    /** Only for a verified DNS change of the same login; never for switching unrelated accounts. */
    suspend fun preserveCustomizationForDnsChange(old: XtreamCredentials, updated: XtreamCredentials) {
        require(old.username == updated.username)
        val oldKey = SettingsBackupCodec.accountKey(old)
        val newKey = SettingsBackupCodec.accountKey(updated)
        if (oldKey == newKey) return
        context.dataStore.edit { preferences ->
            val current = readSettings(preferences).credentials ?: return@edit
            if (SettingsBackupCodec.accountKey(current) != newKey) return@edit
            val orders = readOrderMap(preferences)
            val hidden = readHiddenMap(preferences)
            val names = readDisplayNamesMap(preferences)
            for (type in listOf("live", "vod", "series")) {
                val source = "${oldKey}_$type"
                val destination = "${newKey}_$type"
                if (destination !in orders) orders[source]?.let { orders[destination] = it }
                if (destination !in hidden) hidden[source]?.let { hidden[destination] = it }
            }
            if (newKey !in names) names[oldKey]?.let { names[newKey] = it }
            preferences[PreferencesKeys.CATEGORY_ORDER_MAP] = Json.encodeToString(orders)
            preferences[PreferencesKeys.HIDDEN_CATEGORIES_MAP] = Json.encodeToString(hidden)
            preferences[PreferencesKeys.DISPLAY_NAMES_MAP] = Json.encodeToString(names)
        }
    }

    private fun readProfiles(preferences: Preferences): List<UserProfile> {
        val encoded = preferences[PreferencesKeys.LOCAL_PROFILES]
        if (encoded != null) return profileJson.decodeFromString(encoded)
        val activeId = preferences[PreferencesKeys.ACTIVE_PROFILE_ID] ?: "default"
        return if (activeId == "default") listOf(UserProfile("default", "Usuário 1"))
            else listOf(UserProfile("default", "Usuário 1"), UserProfile(activeId, "Perfil recuperado"))
    }

    suspend fun saveLocalProfile(profile: UserProfile) {
        require(profile.id.isNotBlank() && profile.name.isNotBlank())
        context.dataStore.edit { preferences ->
            val profiles = readProfiles(preferences).filterNot { it.id == profile.id } + profile
            preferences[PreferencesKeys.LOCAL_PROFILES] = Json.encodeToString(profiles)
        }
    }

    suspend fun deleteLocalProfile(profileId: String) {
        if (profileId == "default") return
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCAL_PROFILES] = Json.encodeToString(readProfiles(preferences).filterNot { it.id == profileId })
            if (preferences[PreferencesKeys.ACTIVE_PROFILE_ID] == profileId) preferences[PreferencesKeys.ACTIVE_PROFILE_ID] = "default"
        }
    }

    suspend fun updateProfilePicture(url: String) {
        context.dataStore.edit { it[PreferencesKeys.PROFILE_PICTURE] = url }
    }

    suspend fun updateMovieCategoryMode(accountKey: String, profileId: String, mode: rsv.squitv.domain.model.CategoryMode) {
        val key = "${accountKey}_$profileId"
        context.dataStore.edit { prefs ->
            val currentModes = try {
                prefs[PreferencesKeys.MOVIE_CATEGORY_MODE_MAP]?.let { Json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            prefs[PreferencesKeys.MOVIE_CATEGORY_MODE_MAP] = Json.encodeToString(currentModes + (key to mode.name))

            val currentDismissed = try {
                prefs[PreferencesKeys.MOVIE_CATEGORY_PROMPT_DISMISSED_MAP]?.let { Json.decodeFromString<Map<String, Boolean>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            prefs[PreferencesKeys.MOVIE_CATEGORY_PROMPT_DISMISSED_MAP] = Json.encodeToString(currentDismissed + (key to true))
        }
    }

    suspend fun updateSeriesCategoryMode(accountKey: String, profileId: String, mode: rsv.squitv.domain.model.CategoryMode) {
        val key = "${accountKey}_$profileId"
        context.dataStore.edit { prefs ->
            val currentModes = try {
                prefs[PreferencesKeys.SERIES_CATEGORY_MODE_MAP]?.let { Json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            prefs[PreferencesKeys.SERIES_CATEGORY_MODE_MAP] = Json.encodeToString(currentModes + (key to mode.name))

            val currentDismissed = try {
                prefs[PreferencesKeys.SERIES_CATEGORY_PROMPT_DISMISSED_MAP]?.let { Json.decodeFromString<Map<String, Boolean>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            prefs[PreferencesKeys.SERIES_CATEGORY_PROMPT_DISMISSED_MAP] = Json.encodeToString(currentDismissed + (key to true))
        }
    }

    suspend fun dismissMovieCategoryPrompt(accountKey: String, profileId: String) {
        val key = "${accountKey}_$profileId"
        context.dataStore.edit { prefs ->
            val currentDismissed = try {
                prefs[PreferencesKeys.MOVIE_CATEGORY_PROMPT_DISMISSED_MAP]?.let { Json.decodeFromString<Map<String, Boolean>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            prefs[PreferencesKeys.MOVIE_CATEGORY_PROMPT_DISMISSED_MAP] = Json.encodeToString(currentDismissed + (key to true))
        }
    }

    suspend fun dismissSeriesCategoryPrompt(accountKey: String, profileId: String) {
        val key = "${accountKey}_$profileId"
        context.dataStore.edit { prefs ->
            val currentDismissed = try {
                prefs[PreferencesKeys.SERIES_CATEGORY_PROMPT_DISMISSED_MAP]?.let { Json.decodeFromString<Map<String, Boolean>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            prefs[PreferencesKeys.SERIES_CATEGORY_PROMPT_DISMISSED_MAP] = Json.encodeToString(currentDismissed + (key to true))
        }
    }

    suspend fun updatePreparationSpeed(speed: rsv.squitv.domain.model.PreparationSpeed) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.PREPARATION_SPEED] = speed.name
        }
    }
}
