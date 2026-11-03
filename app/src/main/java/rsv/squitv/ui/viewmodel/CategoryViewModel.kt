package rsv.squitv.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.UserRepository
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.domain.model.DashboardRow
import rsv.squitv.R
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.data.local.entities.FavoriteEntity
import rsv.squitv.core.data.mapper.favoriteToIptvItem
import rsv.squitv.core.data.mapper.toIptvItem
import coil.ImageLoader
import coil.request.ImageRequest
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import timber.log.Timber
import kotlinx.coroutines.flow.first
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.ContentCategory
import rsv.squitv.domain.model.ContentCategories
import rsv.squitv.domain.model.ChannelQuality
import rsv.squitv.data.model.SettingsBackupCodec

@HiltViewModel
class CategoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackSources: rsv.squitv.core.data.repository.PlaybackSourceRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    // Decode/decrypt DataStore once for the catalogue projections, not once per collector.
    private val observedSettings = settingsRepository.settingsFlow.shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val _rawRows = MutableStateFlow<List<DashboardRow>>(emptyList())
    
    private val _searchQuery = savedStateHandle.getStateFlow("searchQuery", "")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOrder = savedStateHandle.getStateFlow("sortOrder", "NAME") // NAME, RECENT, RATING
    val sortOrder: StateFlow<String> = _sortOrder

    private val channelOptions = observedSettings.map {
        Triple(it.groupLiveChannels, rsv.squitv.domain.model.ChannelQuality.fromPreference(it.preferredChannelQuality),
            rsv.squitv.core.data.repository.PlaybackSourceRepository.scope(it))
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, Triple(true, rsv.squitv.domain.model.ChannelQuality.AUTO, ""))

    fun capturePlaybackSource(): String = playbackSources.capture(
        channelOptions.value.third, currentContentRows.value.flatMap { it.items }.filter { it.type == ContentType.LIVE })

    val currentContentRows: StateFlow<List<DashboardRow>> = combine(_rawRows, _searchQuery, _sortOrder, channelOptions) { rows, query, sort, options ->
        if (currentRequest?.account != options.third) return@combine emptyList()
        rows.map { row ->
            var items = if (query.isBlank()) row.items 
                        else row.items.filter { it.name.contains(query, ignoreCase = true) }
            
            items = rsv.squitv.domain.model.LiveChannelGrouping.visible(items, options.third, options.first, options.second)
            items = when (sort) {
                "RECENT" -> items.sortedByDescending { it.releaseDate }
                "RATING" -> items.sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                else -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
            
            row.copy(items = items)
        }.filter { it.items.isNotEmpty() }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _navigationCategories = MutableStateFlow<List<ContentCategory>>(emptyList())
    val navigationCategories: StateFlow<List<ContentCategory>> = _navigationCategories.asStateFlow()
    private var browseType: String? = null
    private var browseReady = false
    private var browseJob: Job? = null
    private var positionJob: Job? = null
    // Keep this tiny projection current while the player covers this back-stack entry.
    // The Settings screen's WhileSubscribed state can otherwise retain an older channel.
    val browsePosition = combine(observedSettings, savedStateHandle.getStateFlow("contentType", "")) { settings, type ->
        settings.catalogPosition(type)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun rememberItem(itemId: String, immediate: Boolean = false) {
        val request = currentRequest ?: return
        val categoryId = request.categoryId ?: return
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            if (!immediate) delay(400)
            settingsRepository.rememberCatalogPosition(request.account, request.type, categoryId, itemId)
        }
    }

    private data class BrowseOptions(
        val scope: String, val hidden: Set<String>, val order: List<String>,
        val grouped: Boolean, val quality: ChannelQuality, val language: String,
        val categoryMode: rsv.squitv.domain.model.CategoryMode
    )
    private data class BrowseBase(
        val entries: List<ContentCategory>, val lookup: Map<String, IptvItem>,
        val recent: List<IptvItem>, val options: BrowseOptions
    )

    private fun startBrowsing(type: String) {
        if (browseType == type && browseJob?.isActive == true) return
        browseJob?.cancel()
        browseType = type
        browseReady = false
        _navigationCategories.value = emptyList()
        val contentType = ContentType.fromString(type)
        val options = observedSettings.map { settings ->
            val normType = if (type == "movie") "vod" else type
            val accountKey = settings.credentials?.let { SettingsBackupCodec.accountKey(it) } ?: ""
            val key = "${accountKey}_$normType"
            val mode = when (type) {
                "movie" -> settings.getMovieCategoryMode(accountKey, settings.activeProfileId)
                "series" -> settings.getSeriesCategoryMode(accountKey, settings.activeProfileId)
                else -> rsv.squitv.domain.model.CategoryMode.PROVIDER
            }
            BrowseOptions(rsv.squitv.core.data.repository.PlaybackSourceRepository.scope(settings),
                settings.hiddenCategoriesMap[key].orEmpty(), settings.categoryOrderMap[key].orEmpty(),
                settings.groupLiveChannels, ChannelQuality.fromPreference(settings.preferredChannelQuality), settings.language, mode)
        }.distinctUntilChanged()
        browseJob = viewModelScope.launch {
            val base = combine(catalogRepository.observeBrowseCatalog(type), options) { (categories, streams), option ->
                val allItems = streams.map { it.toIptvItem() }
                val visible = allItems.filter { it.categoryId !in option.hidden }
                val customized = catalogRepository.applyCategoryCustomization(categories, option.order, option.hidden)
                BrowseBase(
                    ContentCategories.catalog(visible, customized.map { it.id to it.name },
                        context.getString(R.string.browse_all), context.getString(R.string.browse_unclassified),
                        genresHeaderLabel = context.getString(R.string.category_section_genres),
                        serverHeaderLabel = context.getString(R.string.category_section_server),
                        scope = option.scope, grouped = option.grouped, quality = option.quality, mode = option.categoryMode),
                    allItems.associateBy { it.id },
                    catalogRepository.sortAndFilterRecentStreams(streams.filter { it.categoryId !in option.hidden }, 100),
                    option
                )
            }.flowOn(Dispatchers.Default)
            combine(base, userRepository.getFavoritesListFlow(), userRepository.getAllWatchProgressFlow()) { snapshot, favorites, progress ->
                val option = snapshot.options
                val favoriteItems = favorites.map { it.favoriteToIptvItem() }.filter { it.type == contentType }
                    .map { snapshot.lookup[it.id]?.copy(isFavorite = true) ?: it }
                    .distinctBy { it.id }
                val pending = progress.filter {
                    ContentType.fromString(it.type) == contentType &&
                        rsv.squitv.domain.usecase.WatchProgressUtils.isPendingWatchProgress(it.position, it.duration)
                }.sortedByDescending { it.lastWatched }.mapNotNull { wp ->
                    val id = if (contentType == ContentType.SERIES) wp.seriesId ?: wp.streamId else wp.streamId
                    snapshot.lookup[id.toString()]?.copy(progress = wp.position.toFloat() / wp.duration.coerceAtLeast(1L))
                }.distinctBy { it.id }
                fun special(id: String, label: Int, items: List<IptvItem>) =
                    ContentCategories.entry(id, context.getString(label), items, option.scope, option.grouped, option.quality)
                val specials = buildList {
                    if (contentType != ContentType.LIVE) add(special("CONTINUE_WATCHING", R.string.row_continue_watching, pending))
                    add(special("FAVORITES", R.string.favorites_label, favoriteItems))
                    add(special("RECENTS", R.string.recently_added_label, snapshot.recent))
                }
                option.scope to ContentCategories.withSpecials(snapshot.entries, specials)
            }.flowOn(Dispatchers.Default).catch { error ->
                if (error is CancellationException) throw error
                Timber.e(error, "Erro ao observar categorias de conteúdo")
                _isLoading.value = false
            }.collect { (scope, entries) ->
                if (browseType != type) return@collect
                _navigationCategories.value = entries
                browseReady = true
                publishBrowseSelection(type, scope)
            }
        }
    }

    private fun publishBrowseSelection(type: String, scope: String = channelOptions.value.third) {
        if (!browseReady || browseType != type) return
        val selected = ContentCategories.selected(_navigationCategories.value, activeCategoryId.value)
        val previous = activeCategoryId.value
        savedStateHandle["activeCategoryId"] = selected?.id
        if (selected != null) viewModelScope.launch {
            settingsRepository.rememberCatalogPosition(scope, type, selected.id)
        }
        if (selected != null && previous != selected.id) {
            viewModelScope.launch { settingsRepository.updateLastCategory(type, selected.id) }
        }
        savedStateHandle["selectedGenre"] = null
        currentRequest = ContentRequest(type, selected?.id, null, scope, catalogueRevision)
        _rawRows.value = selected?.let { listOf(DashboardRow(it.name, it.items, catId = it.id)) }.orEmpty()
        _isLoading.value = false
    }

    fun onSearchQueryChanged(query: String) {
        savedStateHandle["searchQuery"] = query
    }

    fun onSortOrderChanged(order: String) {
        savedStateHandle["sortOrder"] = order
    }

    fun togglePinCategory(catId: String, type: String, pinned: Boolean) {
        viewModelScope.launch {
            catalogRepository.updateCategoryPinned(catId, type.uppercase(), pinned)
            // Refresh content if needed or rely on manual refresh
        }
    }

    val activeCategoryId = savedStateHandle.getStateFlow<String?>("activeCategoryId", null)

    private data class ContentRequest(
        val type: String, val categoryId: String?,
        val genre: String?, val account: String, val revision: Long
    )

    private var loadJob: Job? = null
    private var generation = 0L
    private var catalogueRevision = 0L
    private var currentRequest: ContentRequest? = null
    private var completedRequest: ContentRequest? = null

    /** A back-stack return reuses the current category, genre and loaded rows. */
    fun ensureContent(type: String, initialCategoryId: String?, revision: Long) {
        val sameType = savedStateHandle.get<String>("contentType") == type && currentRequest?.account == channelOptions.value.third
        catalogueRevision = revision
        loadContent(type, if (sameType) activeCategoryId.value ?: initialCategoryId else initialCategoryId,
            genre = if (sameType) savedStateHandle.get<String>("selectedGenre") else null, reuseLoaded = true)
    }

    init {
        viewModelScope.launch {
            userRepository.getFavoritesListFlow().collectLatest { entities ->
                val request = currentRequest ?: return@collectLatest
                if (request.type == "favorites") {
                    val items = favoriteItems(entities)
                    if (currentRequest == request) _rawRows.value = favoriteRows(request.type, items, request.genre)
                }
            }
        }
    }

    private suspend fun favoriteItems(entities: List<FavoriteEntity>): List<IptvItem> {
        val streams = entities.map { it.streamId }.distinct().chunked(500)
            .flatMap { catalogRepository.getStreamsByIds(it) }
            .associateBy { it.id.toString() to ContentType.fromString(it.streamType) }
        return entities.map { favorite ->
            val item = favorite.favoriteToIptvItem()
            streams[item.id to item.type]?.toIptvItem()?.copy(isFavorite = true) ?: item
        }
    }

    private fun favoriteRows(type: String, favorites: List<IptvItem>, genre: String?): List<DashboardRow> {
        val filtered = favorites.filter {
            (type == "favorites" || it.type == ContentType.fromString(type)) &&
                (genre.isNullOrBlank() || rsv.squitv.domain.usecase.GenreUtils.matchesGenre(it.genre, genre))
        }
        return if (type == "favorites") {
            listOf(ContentType.LIVE to "Canais", ContentType.MOVIE to "Filmes", ContentType.SERIES to "Séries")
                .map { (contentType, label) -> DashboardRow(label, filtered.filter { it.type == contentType }, catId = "FAVORITES") }
                .filter { it.items.isNotEmpty() }
        } else listOf(DashboardRow("Favoritos", filtered, catId = "FAVORITES"))
    }

    fun loadContent(
        type: String, 
        categoryId: String? = null, 
        favorites: List<IptvItem> = emptyList(),
        genre: String? = null,
        reuseLoaded: Boolean = false
    ) {
        if (type in listOf("live", "movie", "series")) {
            if (currentRequest?.let { it.type != type || it.categoryId != categoryId } != false) positionJob?.cancel()
            loadJob?.cancel()
            savedStateHandle["contentType"] = type
            savedStateHandle["activeCategoryId"] = categoryId
            savedStateHandle["selectedGenre"] = null
            _isLoading.value = !browseReady || browseType != type
            startBrowsing(type)
            publishBrowseSelection(type)
            return
        }
        browseJob?.cancel()
        browseType = null
        val requestGeneration = ++generation
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val settingsSnapshot = settingsRepository.settingsFlow.first()
            if (settingsSnapshot.credentials == null) return@launch
            val account = rsv.squitv.core.data.repository.PlaybackSourceRepository.scope(settingsSnapshot)
            val request = ContentRequest(type, categoryId, genre, account, catalogueRevision)
            if (reuseLoaded && request == completedRequest && categoryId != "CONTINUE_WATCHING") {
                _isLoading.value = false
                return@launch
            }
            if (currentRequest?.let { it.account != account || it.type != type || it.categoryId != categoryId || it.genre != genre } != false) {
                _rawRows.value = emptyList()
            }
            currentRequest = request
            savedStateHandle["contentType"] = type
            savedStateHandle["activeCategoryId"] = categoryId
            savedStateHandle["selectedGenre"] = genre
            _isLoading.value = true
            var succeeded = true
            try {
                if (type == "favorites") {
                    _rawRows.value = favoriteRows(type, favoriteItems(userRepository.getFavoritesListFlow().first()), genre)
                    return@launch
                }

                if (type == "history") {
                    val progressList = userRepository.getAllWatchProgressFlow().first()
                    val streams = catalogRepository.getStreamsByIds(progressList.map { it.streamId })
                    val items = streams.map { s ->
                        s.toIptvItem()
                    }
                    _rawRows.value = listOf(DashboardRow("Recentes", items))
                    return@launch
                }

                if (categoryId == "CONTINUE_WATCHING") {
                    val progressList = userRepository.getAllWatchProgressFlow().first()
                    val validProgress = progressList.filter { wp ->
                        rsv.squitv.domain.usecase.WatchProgressUtils.isPendingWatchProgress(wp.position, wp.duration)
                    }.sortedByDescending { it.lastWatched }

                    val normType = if (type == "movie") "vod" else type.lowercase()
                    val filteredProgress = if (normType == "vod") {
                        validProgress.filter { it.type.lowercase() == "vod" || it.type.lowercase() == "movie" }
                    } else if (normType == "series") {
                        validProgress.filter { it.type.lowercase() == "series" }
                    } else {
                        validProgress
                    }

                    val targetStreamIds = filteredProgress.map { wp ->
                        if (wp.type.equals("series", ignoreCase = true) && wp.seriesId != null) wp.seriesId else wp.streamId
                    }.distinct()

                    val streams = catalogRepository.getStreamsByIds(targetStreamIds)
                    val items = mutableListOf<IptvItem>()
                    val seenSeriesIds = mutableSetOf<Int>()

                    for (wp in filteredProgress) {
                        val lookupId = if (wp.type.equals("series", ignoreCase = true) && wp.seriesId != null) wp.seriesId else wp.streamId
                        if (wp.type.equals("series", ignoreCase = true)) {
                            if (!seenSeriesIds.add(lookupId)) continue
                        }
                        val stream = streams.firstOrNull { s -> s.id == lookupId }
                        if (stream != null) {
                            val prog = wp.position.toFloat() / wp.duration.coerceAtLeast(1L).toFloat()
                            items.add(stream.toIptvItem().copy(progress = prog))
                        }
                    }

                    _rawRows.value = listOf(DashboardRow("Continuar Assistindo", items, catId = "CONTINUE_WATCHING"))
                    return@launch
                }

                if (categoryId == "RECENTS") {
                    val filteredItems = when (type) {
                        "live" -> catalogRepository.getRecentLiveStreams(limit = 100)
                        "movie" -> catalogRepository.getRecentMovies(limit = 100)
                        "series" -> catalogRepository.getRecentSeries(limit = 100)
                        else -> catalogRepository.getRecentStreams(limit = 60)
                    }
                    _rawRows.value = listOf(DashboardRow("Adicionados recentemente", filteredItems, catId = "RECENTS"))
                    return@launch
                }

                if (categoryId == "FAVORITES") {
                    _rawRows.value = favoriteRows(type, favoriteItems(userRepository.getFavoritesListFlow().first()), genre)
                    return@launch
                }

                if (type == "actor" && categoryId != null) {
                    val streams = catalogRepository.getStreamsByActor(categoryId)
                    val resultRows = mutableListOf<DashboardRow>()
                    val movies = streams.filter { it.streamType == "VOD" }.map { it.toIptvItem() }
                    val series = streams.filter { it.streamType == "SERIES" }.map { it.toIptvItem() }
                    if (movies.isNotEmpty()) resultRows.add(DashboardRow("Filmes com $categoryId", movies))
                    if (series.isNotEmpty()) resultRows.add(DashboardRow("Séries com $categoryId", series))
                    _rawRows.value = resultRows
                } else if (type == "genre" && categoryId != null) {
                    val streams = catalogRepository.searchStreams(categoryId, "ALL")
                    val filteredStreams = streams.filter { it.genre?.contains(categoryId, true) == true || it.name.contains(categoryId, true) }
                    val resultRows = mutableListOf<DashboardRow>()
                    val movies = filteredStreams.filter { it.streamType == "VOD" }.map { it.toIptvItem() }
                    val series = filteredStreams.filter { it.streamType == "SERIES" }.map { it.toIptvItem() }
                    if (movies.isNotEmpty()) resultRows.add(DashboardRow("Filmes de $categoryId", movies))
                    if (series.isNotEmpty()) resultRows.add(DashboardRow("Séries de $categoryId", series))
                    _rawRows.value = resultRows
                }

                if (!genre.isNullOrBlank()) {
                    val filteredRows = _rawRows.value.map { row ->
                        val filteredItems = row.items.filter { item ->
                            rsv.squitv.domain.usecase.GenreUtils.matchesGenre(item.genre, genre)
                        }
                        row.copy(items = filteredItems)
                    }.filter { it.items.isNotEmpty() }
                    _rawRows.value = filteredRows
                }
                
                // Prefetch visible rows
                prefetchRowImages(_rawRows.value.take(5).flatMap { it.items.take(10) })
            } catch (e: CancellationException) {
                succeeded = false
                throw e
            } catch (e: Exception) {
                succeeded = false
                Timber.e(e, "Error loading content")
            } finally {
                if (requestGeneration == generation) {
                    if (succeeded) completedRequest = request
                    _isLoading.value = false
                }
            }
        }
    }

    private var prefetchJob: Job? = null

    private fun prefetchRowImages(items: List<IptvItem>) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val imageLoader = coil.Coil.imageLoader(context)
            items.take(20).forEach { item ->
                val url = item.icon ?: return@forEach
                if (!isActive) return@launch
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(200, 300)
                    .precision(coil.size.Precision.INEXACT)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
                imageLoader.enqueue(request)
            }
        }
    }
}
