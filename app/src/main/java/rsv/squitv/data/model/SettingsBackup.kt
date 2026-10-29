package rsv.squitv.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import rsv.squitv.data.local.entities.FavoriteEntity
import rsv.squitv.data.repository.SettingsRepository
import java.security.MessageDigest
import java.util.Locale

@Serializable
data class SettingsBackup(
    val format: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val preferences: BackupPreferences,
    val accounts: List<BackupAccount> = emptyList(),
    val hiddenSections: Set<String>? = null,
    val profiles: List<BackupProfile>? = null,
    val activeProfileId: String? = null,
    val profileImageUrl: String? = null,
    val favorites: List<FavoriteEntity>? = null
)

/** Explicit preference allowlist; credentials and PINs are never serialized. */
@Serializable
data class BackupPreferences(
    val playerEngine: String? = null,
    val bufferStrategy: String? = null,
    val useOledTheme: Boolean? = null,
    val dataSaverMode: Boolean? = null,
    val uiZoom: Float? = null,
    val language: String? = null,
    val syncIntervalHours: Int? = null,
    val autoPlayEnabled: Boolean? = null,
    val defaultResizeMode: Int? = null,
    val showDiagnostics: Boolean? = null,
    val compactMode: Boolean? = null,
    val hideBlockedCategories: Boolean? = null,
    val downloadWifiOnly: Boolean? = null,
    val smartDownloadsEnabled: Boolean? = null,
    val preferredAudioLang: String? = null,
    val preferredSubtitleLang: String? = null,
    val detailedNotifications: Boolean? = null,
    val backgroundPlaybackEnabled: Boolean? = null,
    val pipAutoEnterEnabled: Boolean? = null
)

@Serializable
data class BackupAccount(
    val fingerprint: String,
    val categories: Map<String, BackupCategories>,
    val displayName: String? = null
)

@Serializable
data class BackupCategories(val order: List<String>? = null, val hidden: Set<String>? = null)

@Serializable
data class BackupProfile(val id: String, val name: String, val iconUrl: String? = null, val isChild: Boolean = false)

enum class BackupOption(val preferenceKey: String? = null) {
    PLAYER_ENGINE("playerEngine"), BUFFER("bufferStrategy"), THEME("useOledTheme"),
    DATA_SAVER("dataSaverMode"), ZOOM("uiZoom"), LANGUAGE("language"), SYNC_INTERVAL("syncIntervalHours"),
    AUTO_PLAY("autoPlayEnabled"), RESIZE_MODE("defaultResizeMode"), DIAGNOSTICS("showDiagnostics"),
    COMPACT_MODE("compactMode"), HIDE_BLOCKED("hideBlockedCategories"), WIFI_ONLY("downloadWifiOnly"),
    SMART_DOWNLOADS("smartDownloadsEnabled"), AUDIO_LANGUAGE("preferredAudioLang"),
    SUBTITLE_LANGUAGE("preferredSubtitleLang"), NOTIFICATIONS("detailedNotifications"),
    BACKGROUND_AUDIO("backgroundPlaybackEnabled"), PIP("pipAutoEnterEnabled"),
    CATEGORY_ORDER, HIDDEN_CATEGORIES, HOME_SECTIONS, DISPLAY_NAMES, PROFILES, FAVORITES
}

object SettingsBackupCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    private const val FORMAT = "SquiTV.Settings"
    private val types = setOf("live", "vod", "series")
    private val json = Json { prettyPrint = true; explicitNulls = false }

    fun accountKey(credentials: XtreamCredentials): String {
        val user = credentials.username.trim().lowercase(Locale.ROOT)
        val url = credentials.baseUrl.trim().lowercase(Locale.ROOT).removeSuffix("/")
        return if (url.isNotBlank()) "$url#$user" else user
    }

    fun fingerprint(accountKey: String): String = MessageDigest.getInstance("SHA-256")
        .digest(accountKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun configuredAccounts(settings: SettingsRepository.AppSettings): Map<String, String> =
        (settings.accounts + listOfNotNull(settings.credentials)).associate { credentials ->
            val key = accountKey(credentials)
            fingerprint(key) to key
        }

    fun create(settings: SettingsRepository.AppSettings): SettingsBackup {
        val groups = linkedMapOf<String, MutableMap<String, BackupCategories>>()
        (settings.categoryOrderMap.keys + settings.hiddenCategoriesMap.keys).forEach { key ->
            val type = key.substringAfterLast('_')
            require(type in types)
            val account = key.substringBeforeLast('_')
            groups.getOrPut(account) { linkedMapOf() }[type] = BackupCategories(
                settings.categoryOrderMap[key], settings.hiddenCategoriesMap[key])
        }
        configuredAccounts(settings).values.forEach { account ->
            val categories = groups.getOrPut(account) { linkedMapOf() }
            types.forEach { type -> categories[type] = BackupCategories(
                settings.categoryOrderMap["${account}_$type"].orEmpty(), settings.hiddenCategoriesMap["${account}_$type"].orEmpty()) }
        }
        return SettingsBackup(
            FORMAT, 2, System.currentTimeMillis(),
            BackupPreferences(
                playerEngine = settings.playerEngine, bufferStrategy = settings.bufferStrategy,
                useOledTheme = settings.useOledTheme, dataSaverMode = settings.dataSaverMode,
                uiZoom = settings.uiZoom, language = settings.language.ifBlank { "auto" },
                syncIntervalHours = settings.syncIntervalHours, autoPlayEnabled = settings.autoPlayEnabled,
                defaultResizeMode = settings.defaultResizeMode, showDiagnostics = settings.showDiagnostics,
                compactMode = settings.compactMode, hideBlockedCategories = settings.hideBlockedCategories,
                downloadWifiOnly = settings.downloadWifiOnly, smartDownloadsEnabled = settings.smartDownloadsEnabled,
                preferredAudioLang = settings.preferredAudioLang.orEmpty(), preferredSubtitleLang = settings.preferredSubtitleLang.orEmpty(),
                detailedNotifications = settings.detailedNotifications, backgroundPlaybackEnabled = settings.backgroundPlaybackEnabled,
                pipAutoEnterEnabled = settings.pipAutoEnterEnabled
            ),
            groups.map { (account, categories) ->
                BackupAccount(fingerprint(account), categories, settings.displayNamesMap[account].orEmpty())
            }, settings.hiddenSections,
            profiles = settings.profiles.map { BackupProfile(it.id, it.name, it.iconUrl, it.isChild) },
            activeProfileId = settings.activeProfileId, profileImageUrl = settings.profileImageUrl.orEmpty()
        ).also(::validate)
    }

    fun encode(backup: SettingsBackup): String = json.encodeToString(backup.also(::validate))

    fun decode(text: String): SettingsBackup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        return json.decodeFromString<SettingsBackup>(text.removePrefix("\uFEFF")).also(::validate)
    }

    fun select(backup: SettingsBackup, selected: Set<BackupOption>): SettingsBackup {
        require(selected.isNotEmpty())
        val fields = selected.mapNotNull { it.preferenceKey }.toSet()
        val preferences = json.encodeToJsonElement(backup.preferences).jsonObject.filterKeys { it in fields }
        return backup.copy(
            preferences = json.decodeFromJsonElement(JsonObject(preferences)),
            accounts = backup.accounts.map { account -> account.copy(
                categories = account.categories.mapValues { (_, categories) -> BackupCategories(
                    categories.order.takeIf { BackupOption.CATEGORY_ORDER in selected },
                    categories.hidden.takeIf { BackupOption.HIDDEN_CATEGORIES in selected })
                }.filterValues { it.order != null || it.hidden != null },
                displayName = account.displayName.takeIf { BackupOption.DISPLAY_NAMES in selected }) },
            hiddenSections = backup.hiddenSections.takeIf { BackupOption.HOME_SECTIONS in selected },
            profiles = backup.profiles.takeIf { BackupOption.PROFILES in selected },
            activeProfileId = backup.activeProfileId.takeIf { BackupOption.PROFILES in selected },
            profileImageUrl = backup.profileImageUrl.takeIf { BackupOption.PROFILES in selected },
            favorites = backup.favorites.takeIf { BackupOption.FAVORITES in selected }
        ).also(::validate)
    }

    fun available(backup: SettingsBackup): Set<BackupOption> = buildSet {
        val fields = json.encodeToJsonElement(backup.preferences).jsonObject
        BackupOption.entries.filterTo(this) { it.preferenceKey != null && it.preferenceKey in fields }
        if (backup.accounts.any { it.categories.values.any { value -> value.order != null } }) add(BackupOption.CATEGORY_ORDER)
        if (backup.accounts.any { it.categories.values.any { value -> value.hidden != null } }) add(BackupOption.HIDDEN_CATEGORIES)
        if (backup.accounts.any { it.displayName != null }) add(BackupOption.DISPLAY_NAMES)
        if (backup.hiddenSections != null) add(BackupOption.HOME_SECTIONS)
        if (backup.profiles != null) add(BackupOption.PROFILES)
        if (backup.favorites != null) add(BackupOption.FAVORITES)
    }

    fun validate(backup: SettingsBackup) {
        require(backup.format == FORMAT && backup.schemaVersion in 1..2 && backup.createdAt > 0)
        with(backup.preferences) {
            playerEngine?.let { require(it in setOf("EXO", "VLC", "IJK")) }
            bufferStrategy?.let { require(it in setOf("Stable", "Fast", "Safe")) }
            uiZoom?.let { require(it.isFinite() && it in 0.5f..2f) }
            language?.let { require(it in setOf("auto", "pt", "en", "es")) }
            syncIntervalHours?.let { require(it in 1..8760) }
            defaultResizeMode?.let { require(it in 0..4) }
            preferredAudioLang?.let { require(it.length <= 64) }
            preferredSubtitleLang?.let { require(it.length <= 64) }
        }
        require(backup.accounts.size <= 256)
        require(backup.accounts.map { it.fingerprint }.distinct().size == backup.accounts.size)
        var count = 0
        backup.accounts.forEach { account ->
            require(account.fingerprint.matches(Regex("[a-f0-9]{64}")))
            require(account.displayName == null || account.displayName.length <= 256)
            require(account.categories.keys.all { it in types })
            account.categories.values.forEach { categories ->
                categories.order?.let { require(it.distinct().size == it.size) }
                val ids = categories.order.orEmpty() + categories.hidden.orEmpty()
                count += ids.size
                require(count <= 50000 && ids.all { it.isNotBlank() && it.length <= 256 })
            }
        }
        backup.profiles?.let { profiles ->
            require(profiles.size in 1..256 && profiles.map { it.id }.distinct().size == profiles.size)
            require(profiles.all { it.id.isNotBlank() && it.id.length <= 128 && it.name.isNotBlank() && it.name.length <= 256 })
            require(profiles.all { it.iconUrl == null || it.iconUrl.length <= 16384 })
        }
        backup.activeProfileId?.let { require(it.isNotBlank() && it.length <= 128) }
        backup.profileImageUrl?.let { require(it.length <= 16384) }
        backup.favorites?.let { favorites ->
            require(favorites.size <= 50000)
            val accountIds = backup.accounts.map { it.fingerprint }.toSet()
            require(favorites.map { listOf(it.accountId, it.profileId, it.streamType, it.streamId.toString()) }.distinct().size == favorites.size)
            favorites.forEach { favorite ->
                require(favorite.accountId in accountIds)
                require(favorite.profileId.isNotBlank() && favorite.profileId.length <= 128)
                require(favorite.streamId > 0 && favorite.streamType in setOf("LIVE", "MOVIE", "SERIES"))
                require(favorite.name.length <= 2048 && favorite.timestamp >= 0)
                require(listOfNotNull(favorite.logo, favorite.url, favorite.rating, favorite.releaseDate, favorite.containerExtension, favorite.categoryId).all { it.length <= 16384 })
            }
        }
        backup.hiddenSections?.let { require(it.size <= 100 && it.all { key -> key.isNotBlank() && key.length <= 128 }) }
    }
}
