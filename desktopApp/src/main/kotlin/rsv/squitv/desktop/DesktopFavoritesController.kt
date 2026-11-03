package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rsv.squitv.desktop.data.XtreamClient
import rsv.squitv.domain.model.ChannelQuality
import rsv.squitv.shared.xtream.*
import java.text.Collator
import java.util.Locale

internal data class DesktopFavoritesState(val results: List<DesktopSearchItem> = emptyList(),
    val loading: Boolean = false, val errors: Map<DesktopSearchType, String> = emptyMap(),
    val accountScope: String? = null, val requestedType: DesktopSearchType? = null)

/** Explicit library access; resolve only types with saved IDs, never on login/dashboard. */
internal class DesktopFavoritesController(private val client: XtreamClient, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(DesktopFavoritesState())
    val state = mutable.asStateFlow()
    private var account: ServerCredentials? = null
    private var signature: List<Any>? = null
    private var job: Job? = null
    private var revision = 0L
    private var interrupted = false
    private val cache = mutableMapOf<DesktopSearchType, List<DesktopSearchItem>>()
    private val resolved = mutableMapOf<DesktopSearchType, Set<Int>>()

    fun open(credentials: ServerCredentials, settings: DesktopPreferences, force: Boolean = false,
        onlyType: DesktopSearchType? = null) {
        if (account != credentials) { reset(); account = credentials }
        val key = liveCategoryScope(credentials) ?: return
        val ids = mapOf(DesktopSearchType.LIVE to settings.favoriteLiveChannelsByAccount[key].orEmpty(),
            DesktopSearchType.MOVIES to settings.favoriteCatalogItemsByScope["$key/MOVIES"].orEmpty(),
            DesktopSearchType.SERIES to settings.favoriteCatalogItemsByScope["$key/SERIES"].orEmpty())
            .filterKeys { onlyType == null || it == onlyType }
        val nextSignature = listOf(ids, settings.groupLiveChannels, settings.preferredChannelQuality, onlyType ?: DesktopSearchType.ALL)
        if (!force && !interrupted && signature == nextSignature) return
        signature = nextSignature; interrupted = true
        job?.cancel()
        val token = ++revision
        // Remove unmarked items immediately, before a network request can complete.
        mutable.value = mutable.value.copy(results = mutable.value.results.filter { item ->
            if (item.channel != null) ids[item.type].orEmpty().any(item.channel::containsStream)
            else item.id in ids[item.type].orEmpty()
        }, loading = true, errors = emptyMap(), accountScope = key, requestedType = onlyType)
        job = scope.launch {
            val results = mutableListOf<DesktopSearchItem>()
            val errors = mutableMapOf<DesktopSearchType, String>()
            suspend fun publish(loading: Boolean) {
                val sorted = withContext(Dispatchers.Default) {
                    val channels = LiveChannelGroups.visible(results.mapNotNull { it.channel }, key, settings.groupLiveChannels,
                        ChannelQuality.fromPreference(settings.preferredChannelQuality))
                        .map { DesktopSearchItem(DesktopSearchType.LIVE, it.id, it.name, channel = it) }
                    val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
                    (results.filter { it.channel == null } + channels).distinctBy { it.key }.sortedWith { a, b ->
                        collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.key.compareTo(b.key)
                    }
                }
                ensureActive()
                if (token == revision) mutable.value = DesktopFavoritesState(sorted, loading, errors.toMap(), key, onlyType)
            }
            for ((type, saved) in ids) {
                ensureActive()
                if (saved.isEmpty()) { cache.remove(type); resolved.remove(type); continue }
                try {
                    val candidates = if (!force && resolved[type].orEmpty().containsAll(saved)) cache[type].orEmpty()
                    else withContext(Dispatchers.IO) {
                        when (type) {
                            DesktopSearchType.LIVE -> client.channels(credentials, null).filter { it.id in saved }
                                .map { DesktopSearchItem(type, it.id, it.name, channel = it) }
                            DesktopSearchType.MOVIES -> client.movies(credentials, null).mapNotNull { it.toCatalogItem() }
                                .filter { it.id in saved }.map { DesktopSearchItem(type, it.id, it.name, catalogItem = it) }
                            DesktopSearchType.SERIES -> client.series(credentials, null).mapNotNull { it.toCatalogItem() }
                                .filter { it.id in saved }.map { DesktopSearchItem(type, it.id, it.name, catalogItem = it) }
                            else -> emptyList()
                        }
                    }
                    ensureActive()
                    if (token != revision) return@launch
                    cache[type] = candidates
                    resolved[type] = saved.toSet()
                    val present = candidates.filter { it.id in saved }
                    results += present
                    val missing = saved - present.map { it.id }.toSet()
                    if (missing.isNotEmpty()) errors[type] = "${type.label}: ${missing.size} favorito(s) não foram encontrados no servidor. Os marcadores foram preservados."
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    ensureActive()
                    results += cache[type].orEmpty().filter { it.id in saved }
                    errors[type] = "${type.label}: não foi possível atualizar os favoritos. Tente novamente."
                }
                publish(loading = true)
            }
            publish(loading = false)
            if (token == revision) {
                interrupted = false
            }
        }
    }

    fun leave() { job?.cancel(); ++revision; mutable.value = mutable.value.copy(loading = false) }
    fun reset() { leave(); account = null; signature = null; interrupted = false; cache.clear(); resolved.clear(); mutable.value = DesktopFavoritesState() }
}
