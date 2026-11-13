package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*

@Serializable
internal data class DesktopPreferences(val schema: Int = 1, val autoPlayEpisodes: Boolean = true,
    val interfaceScale: Float = 1f, val volume: Int = 80, val muted: Boolean = false,
    val fullscreen: Boolean = true, val ignoredUpdateVersion: String? = null,
    val preferredChannelQuality: String = "AUTO", val groupLiveChannels: Boolean = true,
    val hiddenSections: Set<String> = emptySet(), val language: String = "pt-BR",
    val hiddenCategoryIds: Set<String> = emptySet(), val keepAudioOnMinimize: Boolean = false,
    val alwaysOnTop: Boolean = false,
    val hiddenLiveCategoriesByAccount: Map<String, Set<String>> = emptyMap(),
    val liveCategoryOrderByAccount: Map<String, List<String>> = emptyMap(),
    val catalogCategoryOrderByScope: Map<String, List<String>> = emptyMap(),
    val hiddenCatalogCategoriesByScope: Map<String, Set<String>> = emptyMap(),
    val favoriteLiveChannelsByAccount: Map<String, Set<Int>> = emptyMap(),
    val catalogModesByScope: Map<String, String> = emptyMap(),
    val favoriteCatalogItemsByScope: Map<String, Set<Int>> = emptyMap(),
    val displayNamesByAccount: Map<String, String> = emptyMap(),
    val browseMemory: rsv.squitv.shared.catalog.CatalogBrowseMemory = rsv.squitv.shared.catalog.CatalogBrowseMemory(),
    val preferredAudioLanguage: String? = null, val preferredSubtitleLanguage: String? = null)

/** Variant-local preferences only. Credentials remain exclusively in the existing DPAPI store. */
internal class DesktopPreferencesStore(private val directory: Path, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(DesktopPreferences())
    val state = mutable.asStateFlow()
    private val failure = MutableStateFlow<String?>(null)
    val error = failure.asStateFlow()
    private val ready = MutableStateFlow(false)
    val loaded = ready.asStateFlow()
    private var writable = true
    private val file = directory.resolve("preferences.json")
    private var positionJob: Job? = null
    private var pendingPositionKey: String? = null

    suspend fun flushCatalogPosition() { positionJob?.join() }

    fun rememberCatalogPosition(account: String?, type: String, categoryId: String?, itemId: String? = null,
        immediate: Boolean = false) {
        if (account.isNullOrBlank() || categoryId.isNullOrBlank()) return
        val key = "$account/$type/$categoryId"
        if (itemId == null && key == pendingPositionKey && positionJob?.isActive == true) return
        positionJob?.cancel()
        pendingPositionKey = key
        positionJob = scope.launch {
            if (!immediate) delay(400)
            ensureActive()
            // Debounce may cancel the wait, never an atomic write already in progress.
            withContext(NonCancellable) {
                updateAndAwait { it.copy(browseMemory = it.browseMemory.record(account, type, categoryId, itemId)) }
            }
        }
    }
    init {
        scope.launch {
            mutex.withLock {
                try {
                    val saved = withContext(Dispatchers.IO) {
                        if (Files.exists(file)) {
                            require(Files.size(file) <= 64 * 1024)
                            json.decodeFromString<DesktopPreferences>(Files.readString(file))
                        } else DesktopPreferences()
                    }
                    validateDesktopPreferences(saved)
                    mutable.value = saved
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    writable = false
                    failure.value = "As preferências salvas não puderam ser lidas. O arquivo foi preservado."
                } finally { ready.value = true }
            }
        }
    }

    fun update(transform: (DesktopPreferences) -> DesktopPreferences) {
        scope.launch { updateAndAwait(transform) }
    }

    internal fun favoriteCloudStore(
        account: String,
        cloudScope: String,
        onProgressApplied: suspend (Long, List<rsv.squitv.shared.account.CloudProgress>) -> Unit = { _, _ -> },
        titleFor: (String, Int) -> String?,
    ): rsv.squitv.shared.account.FavoriteLocalStore {
        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$account/$cloudScope".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = directory.resolve("favorite-sync").resolve("$key.json")
        val progressFile = directory.resolve("favorite-sync").resolve("$key-progress.json")
        val catalogKeys = mapOf("movie" to "$account/MOVIES", "series" to "$account/SERIES")

        fun snapshot(value: DesktopPreferences): List<rsv.squitv.shared.account.CloudFavorite> {
            fun item(kind: String, id: Int): rsv.squitv.shared.account.CloudFavorite {
                val title = titleFor(kind, id)?.filterNot(Char::isISOControl)?.take(180)
                    ?.trimEnd { Character.isHighSurrogate(it) }
                return rsv.squitv.shared.account.CloudFavorite(kind, id, title)
            }
            return value.favoriteLiveChannelsByAccount[account].orEmpty().map { item("live", it) } +
                catalogKeys.flatMap { (kind, localKey) ->
                    value.favoriteCatalogItemsByScope[localKey].orEmpty().map { item(kind, it) }
                }
        }

        fun categoryKind(kind: String): CatalogKind? = when (kind) {
            "movie" -> CatalogKind.MOVIES
            "series" -> CatalogKind.SERIES
            else -> null
        }

        fun categorySnapshot(value: DesktopPreferences) = listOf("live", "movie", "series").map { kind ->
            val catalogKind = categoryKind(kind)
            rsv.squitv.shared.account.CloudCategories(
                kind, catalogKind?.let { value.catalogMode(account, it).name.lowercase() } ?: "provider",
                value.categoryOrder(account, catalogKind), value.hiddenCategories(account, catalogKind).sorted(),
            )
        }

        return object : rsv.squitv.shared.account.FavoriteLocalStore {
            override val label = "Favoritos deste Windows"
            override suspend fun checkpoint(): rsv.squitv.shared.account.FavoriteCheckpoint? =
                withContext(Dispatchers.IO) {
                    if (!Files.isRegularFile(file)) return@withContext null
                    require(Files.size(file) <= 2 * 1024 * 1024)
                    json.decodeFromString<rsv.squitv.shared.account.FavoriteCheckpoint>(Files.readString(file))
                }

            override suspend fun saveCheckpoint(checkpoint: rsv.squitv.shared.account.FavoriteCheckpoint) {
                withContext(Dispatchers.IO) {
                    val serialized = json.encodeToString(checkpoint)
                    require(serialized.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024)
                    Files.createDirectories(file.parent)
                    val temp = Files.createTempFile(file.parent, "checkpoint-", ".tmp")
                    try {
                        Files.writeString(temp, serialized)
                        try { Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING) }
                        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, file, REPLACE_EXISTING) }
                    } finally { Files.deleteIfExists(temp) }
                }
            }

            override suspend fun snapshot() = snapshot(mutable.value)

            override suspend fun progressJournal(): rsv.squitv.shared.account.CloudProgressJournal =
                withContext(Dispatchers.IO) {
                    if (!Files.isRegularFile(progressFile)) return@withContext rsv.squitv.shared.account.CloudProgressJournal()
                    require(Files.size(progressFile) <= 4 * 1024 * 1024)
                    json.decodeFromString<rsv.squitv.shared.account.CloudProgressJournal>(Files.readString(progressFile))
                }

            override suspend fun saveProgressJournal(journal: rsv.squitv.shared.account.CloudProgressJournal) {
                withContext(Dispatchers.IO) {
                    val serialized = json.encodeToString(journal)
                    require(serialized.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024)
                    Files.createDirectories(progressFile.parent)
                    val temp = Files.createTempFile(progressFile.parent, "progress-", ".tmp")
                    try {
                        Files.writeString(temp, serialized)
                        try { Files.move(temp, progressFile, ATOMIC_MOVE, REPLACE_EXISTING) }
                        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, progressFile, REPLACE_EXISTING) }
                    } finally { Files.deleteIfExists(temp) }
                }
            }

            override suspend fun applyProgress(boundAt: Long, rows: List<rsv.squitv.shared.account.CloudProgress>) =
                onProgressApplied(boundAt, rows)

            override suspend fun categorySnapshot() = categorySnapshot(mutable.value)
            override suspend fun applyCategories(
                expected: List<rsv.squitv.shared.account.CloudCategories>,
                cloudRows: List<rsv.squitv.shared.account.CloudCategories>,
            ): Boolean {
                var matched = false
                val persisted = updateAndAwait { current ->
                    if (categorySnapshot(current) != expected) return@updateAndAwait current
                    matched = true
                    cloudRows.fold(current) { value, row ->
                        val kind = categoryKind(row.kind)
                        val updated = value.withCategoryOrder(account, kind, row.order)
                            .withHiddenCategories(account, kind, row.hidden.toSet())
                        if (kind == null) updated else updated.withCatalogMode(
                            account, kind, rsv.squitv.domain.model.CategoryMode.fromString(row.mode),
                        )
                    }
                }
                return persisted && matched
            }
            override suspend fun applyChanges(
                expected: Set<Pair<String, Int>>,
                cloudRows: List<rsv.squitv.shared.account.CloudFavorite>,
            ): Boolean {
                var matched = false
                val persisted = updateAndAwait { current ->
                    val existing = snapshot(current)
                    if (existing.map { it.key }.toSet() != expected) return@updateAndAwait current
                    val byKind = existing.groupBy { it.kind }
                        .mapValues { (_, entries) -> entries.map { it.id }.toMutableSet() }
                        .toMutableMap()
                    for (row in cloudRows) {
                        val ids = byKind.getOrPut(row.kind) { mutableSetOf() }
                        if (row.deleted) ids.remove(row.id) else ids.add(row.id)
                    }
                    val live = current.favoriteLiveChannelsByAccount.toMutableMap()
                    val catalog = current.favoriteCatalogItemsByScope.toMutableMap()
                    val liveIds = byKind["live"].orEmpty().toSet()
                    if (liveIds.isEmpty()) live.remove(account) else live[account] = liveIds
                    for ((kind, localKey) in catalogKeys) {
                        val ids = byKind[kind].orEmpty().toSet()
                        if (ids.isEmpty()) catalog.remove(localKey) else catalog[localKey] = ids
                    }
                    matched = true
                    current.copy(favoriteLiveChannelsByAccount = live, favoriteCatalogItemsByScope = catalog)
                }
                return persisted && matched
            }
        }
    }

    suspend fun updateAndAwait(transform: (DesktopPreferences) -> DesktopPreferences): Boolean =
        mutex.withLock {
            if (!ready.value || !writable) return@withLock false
            try {
                val next = transform(mutable.value)
                validateDesktopPreferences(next)
                if (next == mutable.value) return@withLock true
                withContext(Dispatchers.IO) {
                    // Match the read limit before replacing a file we must reopen next time.
                    val serialized = json.encodeToString(next)
                    require(serialized.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
                    Files.createDirectories(directory)
                    val temp = Files.createTempFile(directory, "preferences-", ".tmp")
                    try {
                        Files.writeString(temp, serialized)
                        try { Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING) }
                        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, file, REPLACE_EXISTING) }
                    } finally { Files.deleteIfExists(temp) }
                }
                mutable.value = next
                failure.value = null
                true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                failure.value = "Não foi possível salvar a configuração. Tente novamente."
                false
            }
        }
}

internal fun validateDesktopPreferences(value: DesktopPreferences) {
    listOf(value.preferredAudioLanguage, value.preferredSubtitleLanguage).forEach {
        require(it == null || rsv.squitv.shared.player.TrackLanguage.normalize(it) == it)
    }
    value.browseMemory.validate()
    require(value.schema == 1 && value.interfaceScale in .8f..1.25f && value.volume in 0..100)
    require(value.hiddenSections.size <= 6 && value.hiddenSections.all { it in setOf("LIVE_TV", "MOVIES", "SERIES", "MULTI_VIEW", "DOWNLOADS", "FAVORITES") })
    require(value.hiddenCategoryIds.size <= 10000 && value.hiddenCategoryIds.all { it.length in 1..256 })
    require(value.hiddenLiveCategoriesByAccount.size <= 64)
    require(value.hiddenLiveCategoriesByAccount.all { (key, ids) ->
        key.matches(Regex("[a-f0-9]{64}")) && ids.size <= 10000 && ids.all { it.length in 1..256 }
    })
    require(value.liveCategoryOrderByAccount.size <= 64)
    require(value.liveCategoryOrderByAccount.all { (key, ids) ->
        key.matches(Regex("[a-f0-9]{64}")) && ids.size <= 10000 && ids.distinct().size == ids.size &&
            ids.all { it.length in 1..256 }
    })
    require(value.catalogCategoryOrderByScope.size <= 128 && value.hiddenCatalogCategoriesByScope.size <= 128)
    require(value.catalogCategoryOrderByScope.all { (key, ids) ->
        key.matches(Regex("[a-f0-9]{64}/(MOVIES|SERIES)")) && ids.size <= 10000 && ids.distinct().size == ids.size &&
            ids.all { it.length in 1..256 }
    })
    require(value.hiddenCatalogCategoriesByScope.all { (key, ids) ->
        key.matches(Regex("[a-f0-9]{64}/(MOVIES|SERIES)")) && ids.size <= 10000 && ids.all { it.length in 1..256 }
    })
    require(value.favoriteLiveChannelsByAccount.size <= 64)
    require(value.favoriteLiveChannelsByAccount.all { (key, ids) ->
        key.matches(Regex("[a-f0-9]{64}")) && ids.size <= 10000 && ids.all { it > 0 }
    })
    require(value.catalogModesByScope.size <= 128 && value.catalogModesByScope.all { (key, mode) ->
        key.matches(Regex("[a-f0-9]{64}/(MOVIES|SERIES)")) && mode in setOf("PROVIDER", "GENRE")
    })
    require(value.favoriteCatalogItemsByScope.size <= 128 && value.favoriteCatalogItemsByScope.all { (key, ids) ->
        key.matches(Regex("[a-f0-9]{64}/(MOVIES|SERIES)")) && ids.size <= 10000 && ids.all { it > 0 }
    })
    require(value.displayNamesByAccount.size <= 64 && value.displayNamesByAccount.all { (key, name) ->
        key.matches(Regex("[a-f0-9]{64}")) && name.isNotBlank() && name.length <= 256
    })
}

internal fun DesktopPreferences.withDisplayName(account: String, name: String): DesktopPreferences {
    require(account.matches(Regex("[a-f0-9]{64}")))
    val trimmed = name.trim()
    require(trimmed.length <= 256)
    return copy(displayNamesByAccount = if (trimmed.isEmpty()) displayNamesByAccount - account
        else displayNamesByAccount + (account to trimmed))
}

internal fun DesktopPreferences.isCatalogFavorite(account: String?, kind: CatalogKind, catalogId: Int): Boolean =
    account != null && catalogId in favoriteCatalogItemsByScope["$account/${kind.name}"].orEmpty()

/** The caller supplies a catalog ID, including the parent series ID while playing an episode. */
internal fun DesktopPreferences.toggleCatalogFavorite(account: String, kind: CatalogKind, catalogId: Int): DesktopPreferences {
    require(account.matches(Regex("[a-f0-9]{64}")) && catalogId > 0)
    val key = "$account/${kind.name}"
    val saved = favoriteCatalogItemsByScope[key].orEmpty()
    val next = if (catalogId in saved) saved - catalogId else saved + catalogId
    return copy(favoriteCatalogItemsByScope = if (next.isEmpty()) favoriteCatalogItemsByScope - key
        else favoriteCatalogItemsByScope + (key to next))
}

internal fun DesktopPreferences.catalogMode(account: String, kind: CatalogKind) =
    rsv.squitv.domain.model.CategoryMode.fromString(catalogModesByScope["$account/${kind.name}"])

internal fun DesktopPreferences.withCatalogMode(account: String, kind: CatalogKind, mode: rsv.squitv.domain.model.CategoryMode) =
    copy(catalogModesByScope = catalogModesByScope + ("$account/${kind.name}" to mode.name))

internal fun DesktopPreferences.toggleLiveFavorite(account: String, streamId: Int, versionIds: Iterable<Int>): DesktopPreferences {
    val next = rsv.squitv.domain.model.LiveChannelFavorites.toggle(
        favoriteLiveChannelsByAccount[account].orEmpty(), streamId, versionIds)
    return copy(favoriteLiveChannelsByAccount = if (next.isEmpty()) favoriteLiveChannelsByAccount - account
        else favoriteLiveChannelsByAccount + (account to next))
}

internal fun rsv.squitv.shared.xtream.LiveChannel.isFavorite(savedIds: Set<Int>): Boolean =
    rsv.squitv.domain.model.LiveChannelFavorites.isFavorite(savedIds, id, versions.map { it.id })

internal fun DesktopPreferences.categoryOrder(account: String, kind: CatalogKind?): List<String> =
    if (kind == null) liveCategoryOrderByAccount[account].orEmpty()
    else catalogCategoryOrderByScope["$account/${kind.name}"].orEmpty()

internal fun DesktopPreferences.hiddenCategories(account: String, kind: CatalogKind?): Set<String> =
    if (kind == null) hiddenLiveCategoriesByAccount[account].orEmpty()
    else hiddenCatalogCategoriesByScope["$account/${kind.name}"].orEmpty()

internal fun DesktopPreferences.withCategoryOrder(account: String, kind: CatalogKind?, order: List<String>): DesktopPreferences {
    if (kind == null) return copy(liveCategoryOrderByAccount =
        if (order.isEmpty()) liveCategoryOrderByAccount - account else liveCategoryOrderByAccount + (account to order))
    val key = "$account/${kind.name}"
    return copy(catalogCategoryOrderByScope =
        if (order.isEmpty()) catalogCategoryOrderByScope - key else catalogCategoryOrderByScope + (key to order))
}

internal fun DesktopPreferences.withHiddenCategories(account: String, kind: CatalogKind?, hidden: Set<String>): DesktopPreferences {
    if (kind == null) return copy(hiddenLiveCategoriesByAccount =
        if (hidden.isEmpty()) hiddenLiveCategoriesByAccount - account else hiddenLiveCategoriesByAccount + (account to hidden))
    val key = "$account/${kind.name}"
    return copy(hiddenCatalogCategoriesByScope =
        if (hidden.isEmpty()) hiddenCatalogCategoriesByScope - key else hiddenCatalogCategoriesByScope + (key to hidden))
}

/** Stable scope without storing account names or credentials in preference keys. */
internal fun liveCategoryScope(account: rsv.squitv.shared.xtream.ServerCredentials?): String? = account?.let {
    val address = it.baseUrl.trimEnd('/')
    val identity = "${address.length}:$address${it.username.length}:${it.username}"
    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)))
}
