package rsv.squitv.ui.viewmodel

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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _rawRows = MutableStateFlow<List<DashboardRow>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow("NAME") // NAME, RECENT, RATING
    val sortOrder: StateFlow<String> = _sortOrder.asStateFlow()

    val currentContentRows: StateFlow<List<DashboardRow>> = combine(_rawRows, _searchQuery, _sortOrder) { rows, query, sort ->
        rows.map { row ->
            var items = if (query.isBlank()) row.items 
                        else row.items.filter { it.name.contains(query, ignoreCase = true) }
            
            items = when (sort) {
                "RECENT" -> items.sortedByDescending { it.releaseDate }
                "RATING" -> items.sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                else -> items.sortedBy { it.name.trim().lowercase() }
            }
            
            row.copy(items = items)
        }.filter { it.items.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _categoryCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val categoryCounts: StateFlow<Map<String, Int>> = _categoryCounts.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSortOrderChanged(order: String) {
        _sortOrder.value = order
    }

    fun togglePinCategory(catId: String, type: String, pinned: Boolean) {
        viewModelScope.launch {
            catalogRepository.updateCategoryPinned(catId, type.uppercase(), pinned)
            // Refresh content if needed or rely on manual refresh
        }
    }

    private var loadJob: Job? = null

    fun loadContent(type: String, categoryId: String? = null, blockedIds: Set<String> = emptySet(), favorites: List<IptvItem> = emptyList()) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val credentials = settingsRepository.settingsFlow.first().credentials ?: return@launch
            _isLoading.value = true
            try {
                if (type == "favorites") {
                    val rows = listOf(
                        DashboardRow("Canais", favorites.filter { it.type == ContentType.LIVE }),
                        DashboardRow("Filmes", favorites.filter { it.type == ContentType.MOVIE }),
                        DashboardRow("Séries", favorites.filter { it.type == ContentType.SERIES })
                    ).filter { it.items.isNotEmpty() }
                    _rawRows.value = rows
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
                    val favList = userRepository.getFavoritesListFlow().first().map { entity ->
                        entity.favoriteToIptvItem()
                    }
                    val filteredItems = when (type) {
                        "live" -> favList.filter { it.type == ContentType.LIVE }
                        "movie" -> favList.filter { it.type == ContentType.MOVIE }
                        "series" -> favList.filter { it.type == ContentType.SERIES }
                        else -> favList
                    }
                    _rawRows.value = listOf(DashboardRow("Favoritos", filteredItems, catId = "FAVORITES"))
                    return@launch
                }

                val categories = when (type) {
                    "live" -> catalogRepository.getLiveCategories(credentials)
                    "movie" -> catalogRepository.getVodCategories(credentials)
                    "series" -> catalogRepository.getSeriesCategories(credentials)
                    "genre" -> emptyList() // Handled below
                    "actor" -> emptyList() // Handled below
                    else -> emptyList()
                }.filter { it.id !in blockedIds }

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
                _rawRows.value = rows
                
                // Prefetch visible rows
                rows.take(5).forEach { row ->
                    prefetchRowImages(row.items)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading content")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun prefetchRowImages(items: List<IptvItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val imageLoader = ImageLoader(context)
            items.take(20).forEach { item ->
                val url = item.icon ?: return@forEach
                // Optimized prefetch with a reasonable thumbnail size (ex: 200x300)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(200, 300)
                    .precision(coil.size.Precision.INEXACT)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
                imageLoader.enqueue(request)
            }
        }
    }
}
