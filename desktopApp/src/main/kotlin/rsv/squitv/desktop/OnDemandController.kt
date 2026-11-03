package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rsv.squitv.data.model.*
import rsv.squitv.desktop.data.XtreamClient
import rsv.squitv.desktop.data.ServerProblem
import rsv.squitv.shared.xtream.*

internal enum class CatalogKind(val title: String) { MOVIES("FILMES"), SERIES("SÉRIES") }
internal data class CatalogItem(val id: Int, val name: String, val cover: String?, val rating: String?,
    val genre: String? = null, val extension: String? = null, val series: XtreamSeries? = null)
internal fun XtreamVod.toCatalogItem(): CatalogItem? = streamId?.takeIf { it > 0 }?.let { id ->
    CatalogItem(id, name ?: "Filme $id", streamIcon, rating, extension = container_extension)
}
internal fun XtreamSeries.toCatalogItem(): CatalogItem? = seriesId?.takeIf { it > 0 }?.let { id ->
    CatalogItem(id, name ?: "Série $id", cover, rating, genre, series = this)
}
internal data class CatalogBrowse(val categories: List<LiveCategory> = emptyList(),
    val category: LiveCategory? = null, val items: List<CatalogItem> = emptyList(),
    val loadingCategories: Boolean = false, val loading: Boolean = false, val error: String? = null,
    val itemsLoaded: Boolean = false, val categoriesLoaded: Boolean = false)
internal data class CatalogDetail(val kind: CatalogKind, val item: CatalogItem,
    val movie: XtreamVodInfo? = null, val series: XtreamSeriesInfo? = null,
    val loading: Boolean = true, val error: String? = null, val lastPlayedSeason: Int? = null,
    val lastPlayedEpisodeId: String? = null)
internal data class VodSession(val id: Int, val title: String, val extension: String?, val series: Boolean,
    val cover: String?, val episodes: List<XtreamEpisode> = emptyList(), val episodeIndex: Int = -1)
internal data class OnDemandState(val kind: CatalogKind = CatalogKind.MOVIES,
    val browse: Map<CatalogKind, CatalogBrowse> = emptyMap(), val detail: CatalogDetail? = null,
    val playback: VodSession? = null) {
    val current: CatalogBrowse get() = browse[kind] ?: CatalogBrowse()
}

/** On-demand requests are separate from live state, so browsing cannot change the zapping source. */
internal class OnDemandController(private val client: XtreamClient, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(OnDemandState())
    val state = mutable.asStateFlow()
    private var credentials: ServerCredentials? = null
    private var browseJob: Job? = null
    private var detailJob: Job? = null
    private val categoryOptionsJobs = mutableMapOf<CatalogKind, Job>()

    fun clearCache() {
        categoryOptionsJobs.values.forEach(Job::cancel)
        categoryOptionsJobs.clear()
        browseJob?.cancel()
        detailJob?.cancel()
        mutable.update { OnDemandState() }
    }

    fun open(kind: CatalogKind, account: ServerCredentials) {
        if (credentials != account) { reset(); credentials = account }
        if (mutable.value.kind != kind) {
            browseJob?.cancel(); detailJob?.cancel()
            mutable.update { old -> old.copy(browse = old.browse.mapValues { (_, b) ->
                b.copy(loading = false, loadingCategories = false)
            }, detail = null, playback = null) }
        }
        mutable.update { it.copy(kind = kind) }
        val browse = mutable.value.current
        if (!browse.categoriesLoaded && !browse.loadingCategories) refreshCategories()
    }

    fun refreshCategories() {
        val account = credentials ?: return
        val kind = mutable.value.kind
        browseJob?.cancel()
        categoryOptionsJobs.remove(kind)?.cancel()
        change(kind) { it.copy(loadingCategories = true, categoriesLoaded = false, loading = false, error = null) }
        browseJob = scope.launch { fetchCategories(kind, account) }
    }

    fun loadCategoryOptions(kind: CatalogKind, account: ServerCredentials, force: Boolean = false) {
        if (credentials != account) { reset(); credentials = account }
        val cached = mutable.value.browse[kind]
        if (cached?.loadingCategories == true || !force && cached?.categoriesLoaded == true) return
        categoryOptionsJobs.remove(kind)?.cancel()
        change(kind) { it.copy(loadingCategories = true, categoriesLoaded = false, error = null) }
        categoryOptionsJobs[kind] = scope.launch { fetchCategories(kind, account) }
    }

    private suspend fun fetchCategories(kind: CatalogKind, account: ServerCredentials) {
        try {
            val categories = withContext(Dispatchers.IO) { client.catalogCategories(account, kind == CatalogKind.SERIES) }
            currentCoroutineContext().ensureActive()
            change(kind) { old ->
                val selected = categories.firstOrNull { it.id == old.category?.id }
                old.copy(categories = categories, category = selected, loadingCategories = false, categoriesLoaded = true,
                    items = if (selected == null) emptyList() else old.items,
                    itemsLoaded = selected != null && old.itemsLoaded)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            change(kind) { it.copy(loadingCategories = false, loading = false,
                error = "Não foi possível carregar as categorias. Tente novamente.") }
        }
    }

    fun clearSelection(kind: CatalogKind) {
        if (mutable.value.kind == kind) browseJob?.cancel()
        change(kind) { it.copy(category = null, items = emptyList(), loading = false, itemsLoaded = false, error = null) }
    }

    fun selectCategory(category: LiveCategory) {
        val account = credentials ?: return
        val kind = mutable.value.kind
        if (category !in mutable.value.current.categories) return
        val current = mutable.value.current
        if (current.category == category && (current.loading || current.itemsLoaded && current.error == null)) return
        browseJob?.cancel()
        categoryOptionsJobs.remove(kind)?.cancel()
        change(kind) { it.copy(category = category, items = emptyList(), loading = true, loadingCategories = false,
            categoriesLoaded = true, error = null, itemsLoaded = false) }
        browseJob = scope.launch { fetchItems(kind, account, category) }
    }

    private suspend fun fetchItems(kind: CatalogKind, account: ServerCredentials, category: LiveCategory) {
        change(kind) { it.copy(loading = true, error = null, itemsLoaded = false) }
        try {
            val items = withContext(Dispatchers.IO) {
                if (kind == CatalogKind.MOVIES) client.movies(account, category.id).mapNotNull { it.toCatalogItem() }
                else client.series(account, category.id).mapNotNull { it.toCatalogItem() }
            }
            currentCoroutineContext().ensureActive()
            change(kind) { it.copy(items = items.distinctBy(CatalogItem::id), loading = false, itemsLoaded = true) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            change(kind) { it.copy(loading = false, error = "Não foi possível carregar o catálogo. Tente novamente.") }
        }
    }

    fun openDetail(item: CatalogItem) {
        val account = credentials ?: return
        if (item !in mutable.value.current.items) return
        loadDetail(mutable.value.kind, item, account)
    }

    fun retryDetail() {
        val account = credentials ?: return
        val detail = mutable.value.detail ?: return
        loadDetail(detail.kind, detail.item, account)
    }

    // DesktopController verifies the item against the active global search snapshot.
    internal fun openSearchDetail(kind: CatalogKind, item: CatalogItem, account: ServerCredentials) {
        if (credentials != account) { reset(); credentials = account }
        leave()
        mutable.update { it.copy(kind = kind) }
        loadDetail(kind, item, account)
    }

    private fun loadDetail(kind: CatalogKind, item: CatalogItem, account: ServerCredentials) {
        val detail = CatalogDetail(kind, item)
        detailJob?.cancel()
        mutable.update { it.copy(detail = detail) }
        detailJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (detail.kind == CatalogKind.MOVIES) detail.copy(movie = client.movieInfo(account, item.id), loading = false)
                    else detail.copy(series = client.seriesInfo(account, item.id), loading = false)
                }
                ensureActive()
                mutable.update { it.copy(detail = result) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                mutable.update { it.copy(detail = detail.copy(loading = false,
                    error = (e as? ServerProblem)?.message ?: if (detail.kind == CatalogKind.SERIES)
                        "Não foi possível ler as temporadas e episódios recebidos do servidor. Tente novamente."
                    else "Não foi possível carregar os detalhes. Tente novamente.")) }
            }
        }
    }

    fun playMovie() {
        val detail = mutable.value.detail?.takeIf { it.kind == CatalogKind.MOVIES } ?: return
        mutable.update { it.copy(playback = VodSession(detail.item.id, detail.item.name,
            detail.movie?.movieData?.container_extension ?: detail.item.extension, false,
            detail.movie?.info?.movieImage ?: detail.item.cover)) }
    }

    fun playEpisode(season: Int, episode: XtreamEpisode) {
        val detail = mutable.value.detail ?: return
        val info = detail.series ?: return
        if (episode !in OnDemandPlayback.episodes(info, season)) return
        val episodes = OnDemandPlayback.seasons(info).flatMap { (number, _) ->
            OnDemandPlayback.episodes(info, number).map { it.copy(season = number) }
        }
            .distinctBy { it.id }
        val index = episodes.indexOfFirst { it.id == episode.id }
        if (index < 0) return
        mutable.update { it.copy(playback = episodeSession(detail.item, episodes, index), detail = detail.copy(
            lastPlayedSeason = episodes[index].season, lastPlayedEpisodeId = episodes[index].id)) }
    }

    fun nextEpisode(offset: Int) {
        val session = mutable.value.playback ?: return
        val index = session.episodeIndex + offset
        if (index !in session.episodes.indices) return
        selectEpisode(session.episodes[index].id ?: return)
    }

    fun selectEpisode(id: String) {
        val session = mutable.value.playback?.takeIf { it.series } ?: return
        val detail = mutable.value.detail ?: return
        val index = session.episodes.indexOfFirst { it.id == id }
        if (index < 0) return
        mutable.update { it.copy(playback = episodeSession(detail.item, session.episodes, index),
            detail = detail.copy(lastPlayedSeason = session.episodes[index].season,
                lastPlayedEpisodeId = session.episodes[index].id)) }
    }

    private fun episodeSession(item: CatalogItem, episodes: List<XtreamEpisode>, index: Int): VodSession {
        val episode = episodes[index]
        return VodSession(requireNotNull(episode.id?.toIntOrNull()),
            "${item.name} · ${episode.title ?: "Episódio ${episode.episodeNum.orEmpty()}"}",
            episode.container_extension, true, episode.info?.movieImage ?: item.cover, episodes, index)
    }

    fun stopPlayback() { mutable.update { it.copy(playback = null) } }
    fun closeDetail() { detailJob?.cancel(); mutable.update { it.copy(detail = null, playback = null) } }
    fun leave() {
        categoryOptionsJobs.values.forEach(Job::cancel)
        categoryOptionsJobs.clear()
        browseJob?.cancel(); detailJob?.cancel()
        mutable.update { old -> old.copy(detail = null, playback = null, browse = old.browse.mapValues { (_, b) ->
            b.copy(loading = false, loadingCategories = false)
        }) }
    }
    fun reset() { leave(); credentials = null; mutable.value = OnDemandState() }
    private fun change(kind: CatalogKind, transform: (CatalogBrowse) -> CatalogBrowse) {
        mutable.update { it.copy(browse = it.browse + (kind to transform(it.browse[kind] ?: CatalogBrowse()))) }
    }
}
