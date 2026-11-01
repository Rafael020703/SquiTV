package rsv.squitv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rsv.squitv.core.data.mapper.toIptvItem
import kotlinx.coroutines.flow.*
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
    data class Stream(val stream: IptvStreamEntity, val item: rsv.squitv.domain.model.IptvItem = stream.toIptvItem()) : SearchResult()
    data class Episode(val episode: EpisodeEntity) : SearchResult()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: rsv.squitv.data.repository.SettingsRepository,
    private val playbackSources: rsv.squitv.core.data.repository.PlaybackSourceRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>("query") ?: "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    

    private val _selectedType = MutableStateFlow("ALL")
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    private val _selectedYear = MutableStateFlow<String?>(null)
    val selectedYear: StateFlow<String?> = _selectedYear.asStateFlow()

    private val _selectedRating = MutableStateFlow(0f)
    val selectedRating: StateFlow<Float> = _selectedRating.asStateFlow()

    private var resultScope: String? = null
    private val _rawSearchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    private val channelOptions = settingsRepository.settingsFlow.map {
        Triple(it.groupLiveChannels, rsv.squitv.domain.model.ChannelQuality.fromPreference(it.preferredChannelQuality),
            rsv.squitv.core.data.repository.PlaybackSourceRepository.scope(it))
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly,
        Triple(true, rsv.squitv.domain.model.ChannelQuality.AUTO, ""))

    val searchResults: StateFlow<List<SearchResult>> = combine(_rawSearchResults, channelOptions) { results, options ->
        if (resultScope != options.third) return@combine emptyList()
        val streams = results.filterIsInstance<SearchResult.Stream>()
        val byId = streams.associateBy { it.item.type to it.item.id }
        val visible = rsv.squitv.domain.model.LiveChannelGrouping.visible(
            streams.map { it.item }, options.third, options.first, options.second)
        visible.mapNotNull { item -> byId[item.type to item.id]?.copy(item = item) } +
            results.filterIsInstance<SearchResult.Episode>()
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun capturePlaybackSource(): String = playbackSources.capture(channelOptions.value.third,
        searchResults.value.filterIsInstance<SearchResult.Stream>().map { it.item }
            .filter { it.type == rsv.squitv.domain.model.ContentType.LIVE })

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var searchJob: Job? = null
    private var searchRevision = 0L

    init {
        if (_searchQuery.value.isNotBlank()) performSearch()
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
        val query = _searchQuery.value
        val type = _selectedType.value
        val yearFilter = _selectedYear.value
        val minRating = _selectedRating.value

        if (query.isBlank()) {
            _rawSearchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            _isLoading.value = true
            resultScope = rsv.squitv.core.data.repository.PlaybackSourceRepository.scope(settingsRepository.settingsFlow.first())
            try {
                var streams = catalogRepository.searchStreams(query, type)
                
                if (yearFilter != null) {
                    streams = streams.filter { it.releaseDate?.contains(yearFilter) == true }
                }
                
                if (minRating > 0) {
                    streams = streams.filter { (it.rating?.toFloatOrNull() ?: 0f) >= minRating }
                }

                val streamResults = streams.map { SearchResult.Stream(it) }
                
                var episodes = if (type == "ALL" || type == "SERIES") {
                    catalogRepository.searchEpisodes(query)
                } else {
                    emptyList()
                }

                if (minRating > 0) {
                    episodes = episodes.filter { (it.rating?.toFloatOrNull() ?: 0f) >= minRating }
                }

                val episodeResults = episodes.map { SearchResult.Episode(it) }
                
                currentCoroutineContext().ensureActive()
                if (revision == searchRevision) _rawSearchResults.value = streamResults + episodeResults
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (revision == searchRevision) _rawSearchResults.value = emptyList()
            } finally {
                if (revision == searchRevision) _isLoading.value = false
            }
        }
    }
}
