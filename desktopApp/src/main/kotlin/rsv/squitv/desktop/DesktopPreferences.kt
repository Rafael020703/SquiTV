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
    val hiddenCatalogCategoriesByScope: Map<String, Set<String>> = emptyMap())

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
}

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
