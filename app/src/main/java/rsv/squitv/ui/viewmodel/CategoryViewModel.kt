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

@HiltViewModel
class CategoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _rawRows = MutableStateFlow<List<DashboardRow>>(emptyList())
    
    private val _searchQuery = savedStateHandle.getStateFlow("searchQuery", "")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOrder = savedStateHandle.getStateFlow("sortOrder", "NAME") // NAME, RECENT, RATING
    val sortOrder: StateFlow<String> = _sortOrder

    val currentContentRows: StateFlow<List<DashboardRow>> = combine(_rawRows, _searchQuery, _sortOrder) { rows, query, sort ->
        rows.map { row ->
            var items = if (query.isBlank()) row.items 
                        else row.items.filter { it.name.contains(query, ignoreCase = true) }
            
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

    private val _categoryCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val categoryCounts: StateFlow<Map<String, Int>> = _categoryCounts.asStateFlow()

    private val _availableGenres = MutableStateFlow<List<String>>(emptyList())
    val availableGenres: StateFlow<List<String>> = _availableGenres.asStateFlow()

    private val _selectedGenre = savedStateHandle.getStateFlow<String?>("selectedGenre", null)
    val selectedGenre: StateFlow<String?> = _selectedGenre

    fun loadGenres(type: String) {
        viewModelScope.launch {
            try {
                _availableGenres.value = catalogRepository.getAvailableGenres(type)
            } catch (_: Exception) {
                _availableGenres.value = emptyList()
            }
        }
    }

    fun selectGenre(type: String, genre: String?, categoryId: String? = null, blockedIds: Set<String> = emptySet()) {
        savedStateHandle["selectedGenre"] = genre
        loadContent(type, categoryId, blockedIds, genre = genre)
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
        val type: String, val categoryId: String?, val blockedIds: Set<String>,
        val genre: String?, val account: String, val revision: Long
    )

    private var loadJob: Job? = null
    private var generation = 0L
    private var catalogueRevision = 0L
    private var currentRequest: ContentRequest? = null
    private var completedRequest: ContentRequest? = null

    /** A back-stack return reuses the current category, genre and loaded rows. */
    fun ensureContent(type: String, initialCategoryId: String?, blockedIds: Set<String>, revision: Long) {
        val sameType = savedStateHandle.get<String>("contentType") == type
        catalogueRevision = revision
        loadContent(type, if (sameType) activeCategoryId.value ?: initialCategoryId else initialCategoryId,
            blockedIds, genre = if (sameType) _selectedGenre.value else null, reuseLoaded = true)
    }

    init {
        viewModelScope.launch {
            userRepository.getFavoritesListFlow().collectLatest { entities ->
                val request = currentRequest ?: return@collectLatest
                if (request.categoryId == "FAVORITES" || request.type == "favorites") {
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
        blockedIds: Set<String> = emptySet(), 
        favorites: List<IptvItem> = emptyList(),
        genre: String? = null,
        reuseLoaded: Boolean = false
    ) {
        val requestGeneration = ++generation
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val credentials = settingsRepository.settingsFlow.first().credentials ?: return@launch
            val account = "${credentials.baseUrl.trim().lowercase()}#${credentials.username.trim().lowercase()}"
            val request = ContentRequest(type, categoryId, blockedIds.toSet(), genre, account, catalogueRevision)
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
                        IptvItem(s.id.toString(), s.name, s.logo, ContentType.fromString(s.streamType), s.url, s.containerExtension)
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

                val rawCategories = when (type) {
                    "live" -> catalogRepository.getLiveCategories(credentials)
                    "movie" -> catalogRepository.getVodCategories(credentials)
                    "series" -> catalogRepository.getSeriesCategories(credentials)
                    "genre" -> emptyList() // Handled below
                    "actor" -> emptyList() // Handled below
                    else -> emptyList()
                }.filter { it.id !in blockedIds }

                val currentSettings = settingsRepository.settingsFlow.first()
                val simpleUser = credentials.username.trim().lowercase()
                val cleanUrl = credentials.baseUrl.trim().lowercase().removeSuffix("/")
                val accountKey = if (cleanUrl.isNotBlank()) "$cleanUrl#$simpleUser" else simpleUser
                val normType = if (type == "movie") "vod" else type.lowercase()
                val typeKey = "${accountKey}_${normType}"

                val customOrder = currentSettings.categoryOrderMap[typeKey]
                val hiddenCategories = currentSettings.hiddenCategoriesMap[typeKey]

                val categories = catalogRepository.applyCategoryCustomization(
                    categories = rawCategories,
                    customOrder = customOrder,
                    hiddenCategories = hiddenCategories
                )

                val rows = mutableListOf<DashboardRow>()

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
                } else {
                    val filteredCats = if (categoryId != null) categories.filter { it.id == categoryId } else categories
                    filteredCats.forEach { cat ->
                        val items = when(type) {
                            "live" -> {
                                val raw = catalogRepository.getLiveStreams(credentials, cat.id)
                                catalogRepository.groupLiveStreams(raw)
                            }
                            "movie" -> {
                                catalogRepository.getVodStreams(credentials, cat.id).map { it.toIptvItem() }
                            }
                            "series" -> {
                                catalogRepository.getSeries(credentials, cat.id).map { it.toIptvItem() }
                            }
                            else -> emptyList()
                        }
                        if (items.isNotEmpty()) {
                            rows.add(DashboardRow(cat.name, items, catId = cat.id))
                        }
                    }
                    _rawRows.value = rows
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
