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
internal data class CatalogBrowse(val categories: List<LiveCategory> = emptyList(),
    val category: LiveCategory? = null, val items: List<CatalogItem> = emptyList(),
    val loadingCategories: Boolean = false, val loading: Boolean = false, val error: String? = null)
internal data class CatalogDetail(val kind: CatalogKind, val item: CatalogItem,
    val movie: XtreamVodInfo? = null, val series: XtreamSeriesInfo? = null,
    val loading: Boolean = true, val error: String? = null, val lastPlayedSeason: Int? = null)
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
        if (browse.categories.isEmpty() && !browse.loadingCategories) refreshCategories()
        else if (browse.category != null && browse.items.isEmpty() && !browse.loading) selectCategory(browse.category)
    }

    fun refreshCategories() {
        val account = credentials ?: return
        val kind = mutable.value.kind
        browseJob?.cancel()
        change(kind) { it.copy(loadingCategories = true, loading = false, error = null) }
        browseJob = scope.launch {
            try {
                val categories = withContext(Dispatchers.IO) { client.catalogCategories(account, kind == CatalogKind.SERIES) }
                ensureActive()
                val old = mutable.value.browse[kind]?.category?.id
                val selected = categories.firstOrNull { it.id == old } ?: categories.firstOrNull()
                change(kind) { it.copy(categories = categories, category = selected, loadingCategories = false) }
                if (selected != null) fetchItems(kind, account, selected)
                else change(kind) { it.copy(items = emptyList()) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { change(kind) { it.copy(loadingCategories = false, loading = false,
                error = "Não foi possível carregar as categorias. Tente novamente.") } }
        }
    }

    fun selectCategory(category: LiveCategory) {
        val account = credentials ?: return
        val kind = mutable.value.kind
        if (category !in mutable.value.current.categories) return
        browseJob?.cancel()
        change(kind) { it.copy(category = category, items = emptyList(), loading = true, error = null) }
        browseJob = scope.launch { fetchItems(kind, account, category) }
    }

    private suspend fun fetchItems(kind: CatalogKind, account: ServerCredentials, category: LiveCategory) {
        change(kind) { it.copy(loading = true, error = null) }
        try {
            val items = withContext(Dispatchers.IO) {
                if (kind == CatalogKind.MOVIES) client.movies(account, category.id).mapNotNull { movie ->
                    movie.streamId?.takeIf { it > 0 }?.let { id -> CatalogItem(id, movie.name ?: "Filme $id",
                        movie.streamIcon, movie.rating, extension = movie.container_extension) }
                } else client.series(account, category.id).mapNotNull { series ->
                    series.seriesId?.takeIf { it > 0 }?.let { id -> CatalogItem(id, series.name ?: "Série $id",
                        series.cover, series.rating, series.genre, series = series) }
                }
            }
            currentCoroutineContext().ensureActive()
            change(kind) { it.copy(items = items.distinctBy(CatalogItem::id), loading = false) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { change(kind) { it.copy(loading = false,
            error = "Não foi possível carregar o catálogo. Tente novamente.") } }
    }

    fun openDetail(item: CatalogItem) {
        val account = credentials ?: return
        if (item !in mutable.value.current.items) return
        val detail = CatalogDetail(mutable.value.kind, item)
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
            catch (e: Exception) { mutable.update { it.copy(detail = detail.copy(loading = false,
                error = (e as? ServerProblem)?.message ?: if (detail.kind == CatalogKind.SERIES)
                    "Não foi possível ler as temporadas e episódios recebidos do servidor. Tente novamente."
                else "Não foi possível carregar os detalhes. Tente novamente.")) } }
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
        mutable.update { it.copy(playback = episodeSession(detail.item, episodes, index), detail = detail.copy(lastPlayedSeason = episodes[index].season)) }
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
            detail = detail.copy(lastPlayedSeason = session.episodes[index].season)) }
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
