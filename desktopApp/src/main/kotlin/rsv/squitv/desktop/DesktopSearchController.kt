package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rsv.squitv.desktop.data.XtreamClient
import rsv.squitv.desktop.data.ServerProblem
import rsv.squitv.shared.xtream.LiveChannel
import rsv.squitv.shared.xtream.ServerCredentials
import rsv.squitv.shared.xtream.LiveChannelGroups
import rsv.squitv.domain.model.ChannelQuality
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

internal enum class DesktopSearchType(val label: String) {
    ALL("Todos"), LIVE("Canais"), MOVIES("Filmes"), SERIES("Séries")
}

internal data class DesktopSearchItem(
    val type: DesktopSearchType, val id: Int, val name: String,
    val channel: LiveChannel? = null, val catalogItem: CatalogItem? = null
) {
    val key: String get() = channel?.stableKey ?: "${type.name}:$id"
}

internal data class DesktopSearchState(
    val query: String = "", val type: DesktopSearchType = DesktopSearchType.ALL,
    val results: List<DesktopSearchItem> = emptyList(), val totalMatches: Int = 0,
    val loading: Boolean = false,
    val errors: Map<DesktopSearchType, String> = emptyMap()
)

/** Android SearchViewModel interaction: 250 ms debounce, latest query wins and scoped live snapshot.
 * Windows has no Room catalog yet: fetch each full listing only on a non-empty search, then reuse
 * its in-memory index. Details/episodes are never fetched to build this index. */
internal class DesktopSearchController(private val client: XtreamClient, private val scope: CoroutineScope) {
    private data class IndexedItem(val item: DesktopSearchItem, val title: String)
    private data class Listing(val items: List<IndexedItem>, val loadedAt: Long)
    private val mutable = MutableStateFlow(DesktopSearchState())
    val state = mutable.asStateFlow()
    private var credentials: ServerCredentials? = null
    private var job: Job? = null
    private var revision = 0L
    private var resumeNeeded = false
    private var groupLiveChannels = true
    private var preferredChannelQuality = ChannelQuality.AUTO
    private val cache = mutableMapOf<DesktopSearchType, Listing>()

    fun open(account: ServerCredentials) {
        if (credentials != account) { reset(); credentials = account }
    }

    fun query(value: String) {
        val bounded = value.take(256)
        if (bounded == mutable.value.query) return
        mutable.value = mutable.value.copy(query = bounded)
        runSearch(250)
    }

    fun filter(type: DesktopSearchType) {
        if (type == mutable.value.type) return
        mutable.value = mutable.value.copy(type = type)
        runSearch(0)
    }

    fun resume() { if (resumeNeeded) runSearch(0, preserveResults = true) }

    fun configureLiveChannels(grouped: Boolean, preferred: ChannelQuality) {
        if (groupLiveChannels == grouped && preferredChannelQuality == preferred) return
        groupLiveChannels = grouped
        preferredChannelQuality = preferred
        // Rebuild on return to Search; changing a player option must not start background queries.
        resumeNeeded = mutable.value.query.isNotBlank()
    }

    fun retry() = runSearch(0, preserveResults = true)

    private fun runSearch(debounceMs: Long, preserveResults: Boolean = false) {
        job?.cancel()
        val currentRevision = ++revision
        val account = credentials ?: return
        val input = mutable.value
        val grouped = groupLiveChannels
        val preferred = preferredChannelQuality
        val tokens = normalize(input.query).split(' ').filter(String::isNotEmpty)
        resumeNeeded = tokens.isNotEmpty()
        mutable.value = input.copy(results = if (preserveResults) input.results else emptyList(),
            totalMatches = if (preserveResults) input.totalMatches else 0, errors = emptyMap(), loading = tokens.isNotEmpty())
        if (tokens.isEmpty()) return
        job = scope.launch {
            delay(debounceMs)
            val types = if (input.type == DesktopSearchType.ALL)
                listOf(DesktopSearchType.LIVE, DesktopSearchType.MOVIES, DesktopSearchType.SERIES) else listOf(input.type)
            val matches = mutableListOf<DesktopSearchItem>()
            val errors = mutableMapOf<DesktopSearchType, String>()
            for (type in types) {
                ensureActive()
                try {
                    val listing = cache[type]?.takeIf { System.nanoTime() - it.loadedAt < 10.minutes.inWholeNanoseconds }
                        ?: withContext(Dispatchers.IO) { load(account, type) }.also {
                            ensureActive()
                            if (revision == currentRevision) cache[type] = it
                        }
                    val found = withContext(Dispatchers.Default) {
                        listing.items.mapNotNull {
                            ensureActive()
                            it.item.takeIf { _ -> tokens.all(it.title::contains) }
                        }
                    }
                    matches.addAll(found)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { errors[type] = "${type.label}: " + ((e as? ServerProblem)?.message
                    ?: "Não foi possível pesquisar este catálogo.") }
                val (sorted, total) = withContext(Dispatchers.Default) {
                    val presented = if (grouped) {
                        val live = LiveChannelGroups.visible(matches.mapNotNull { it.channel },
                            liveCategoryScope(account).orEmpty(), true, preferred).map {
                            DesktopSearchItem(DesktopSearchType.LIVE, it.id, it.name, channel = it)
                        }
                        matches.filter { it.channel == null } + live
                    } else matches.toList()
                    val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
                    presented.sortedWith { a, b ->
                        collator.compare(a.name, b.name).takeIf { it != 0 }
                            ?: a.key.compareTo(b.key)
                    }.take(300) to presented.size
                }
                ensureActive()
                if (revision != currentRevision) return@launch
                if (preserveResults && type != types.last()) continue
                mutable.value = mutable.value.copy(results = sorted, totalMatches = total,
                    errors = errors.toMap(), loading = type != types.last())
            }
            resumeNeeded = false
        }
    }

    private suspend fun load(account: ServerCredentials, type: DesktopSearchType): Listing {
        val items = when (type) {
            DesktopSearchType.LIVE -> client.channels(account, null).map {
                DesktopSearchItem(type, it.id, it.name, channel = it)
            }
            DesktopSearchType.MOVIES -> client.movies(account, null).mapNotNull { movie ->
                movie.toCatalogItem()?.let { DesktopSearchItem(type, it.id, it.name, catalogItem = it) }
            }
            DesktopSearchType.SERIES -> client.series(account, null).mapNotNull { series ->
                series.toCatalogItem()?.let { DesktopSearchItem(type, it.id, it.name, catalogItem = it) }
            }
            DesktopSearchType.ALL -> error("A listing must have an explicit content type")
        }
        val index = items.distinctBy(DesktopSearchItem::key).map {
            currentCoroutineContext().ensureActive()
            IndexedItem(it, normalize(it.name))
        }
        return Listing(index, System.nanoTime())
    }

    fun leave() { job?.cancel(); ++revision; mutable.value = mutable.value.copy(loading = false) }
    fun reset() { leave(); credentials = null; resumeNeeded = false; cache.clear(); mutable.value = DesktopSearchState() }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(MARKS, "").lowercase(Locale.ROOT).trim().replace(SPACES, " ")

    private companion object {
        val MARKS = Regex("\\p{M}+")
        val SPACES = Regex("\\s+")
    }
}
