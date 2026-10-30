package rsv.squitv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.data.local.entities.IptvStreamEntity
import rsv.squitv.data.local.entities.EpisodeEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

sealed class SearchResult {
    data class Stream(val stream: IptvStreamEntity) : SearchResult()
    data class Episode(val episode: EpisodeEntity) : SearchResult()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>("query") ?: "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private var blockedCategoryIds: Set<String> = emptySet()

    private val _selectedType = MutableStateFlow("ALL")
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    private val _selectedYear = MutableStateFlow<String?>(null)
    val selectedYear: StateFlow<String?> = _selectedYear.asStateFlow()

    private val _selectedRating = MutableStateFlow(0f)
    val selectedRating: StateFlow<Float> = _selectedRating.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var searchJob: Job? = null
    private var searchRevision = 0L

    init {
        if (_searchQuery.value.isNotBlank()) performSearch()
    }

    fun setBlockedCategories(ids: Set<String>) {
        blockedCategoryIds = ids
        performSearch()
    }

    fun onQueryChanged(query: String) {
        savedStateHandle["query"] = query
        _searchQuery.value = query
        performSearch(debounceMillis = 250L)
    }

    fun onTypeChanged(type: String) {
        _selectedType.value = type
        performSearch()
    }

    fun onYearChanged(year: String?) {
        _selectedYear.value = year
        performSearch()
    }

    fun onRatingChanged(rating: Float) {
        _selectedRating.value = rating
        performSearch()
    }

    private fun performSearch(debounceMillis: Long = 0L) {
        searchJob?.cancel()
        val revision = ++searchRevision
        _isLoading.value = false
        val blockedIds = blockedCategoryIds.toSet()
        val query = _searchQuery.value
        val type = _selectedType.value
        val yearFilter = _selectedYear.value
        val minRating = _selectedRating.value

        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            _isLoading.value = true
            try {
                var streams = catalogRepository.searchStreams(query, type, blockedIds)
                
                if (yearFilter != null) {
                    streams = streams.filter { it.releaseDate?.contains(yearFilter) == true }
                }
                
                if (minRating > 0) {
                    streams = streams.filter { (it.rating?.toFloatOrNull() ?: 0f) >= minRating }
                }

                val streamResults = streams.map { SearchResult.Stream(it) }
                
                var episodes = if (type == "ALL" || type == "SERIES") {
                    catalogRepository.searchEpisodes(query, blockedIds)
                } else {
                    emptyList()
                }

                if (minRating > 0) {
                    episodes = episodes.filter { (it.rating?.toFloatOrNull() ?: 0f) >= minRating }
                }

                val episodeResults = episodes.map { SearchResult.Episode(it) }
                
                currentCoroutineContext().ensureActive()
                if (revision == searchRevision) _searchResults.value = streamResults + episodeResults
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (revision == searchRevision) _searchResults.value = emptyList()
            } finally {
                if (revision == searchRevision) _isLoading.value = false
            }
        }
    }
}
