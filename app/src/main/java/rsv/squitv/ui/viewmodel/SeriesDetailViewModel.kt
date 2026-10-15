package rsv.squitv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.UserRepository
import rsv.squitv.data.local.entities.EpisodeEntity
import rsv.squitv.data.local.entities.SeasonEntity
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.model.XtreamCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _seasons = MutableStateFlow<List<SeasonEntity>>(emptyList())
    val seasons: StateFlow<List<SeasonEntity>> = _seasons

    private val _episodesMap = MutableStateFlow<Map<Int, List<EpisodeEntity>>>(emptyMap())
    val episodesMap: StateFlow<Map<Int, List<EpisodeEntity>>> = _episodesMap

    private val _selectedSeason = MutableStateFlow<Int?>(null)
    val selectedSeason: StateFlow<Int?> = _selectedSeason

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadSeriesInfo(seriesId: Int, watchProgress: Map<Int, Float> = emptyMap()) {
        viewModelScope.launch {
            val credentials = settingsRepository.settingsFlow.first().credentials ?: return@launch
            _isLoading.value = true
            try {
                val (seasonsList, episodes) = catalogRepository.getSeriesInfo(credentials, seriesId)
                _seasons.value = seasonsList
                _episodesMap.value = episodes
                
                if (seasonsList.isNotEmpty()) {
                    // Smart Resume: Find season with the most recently watched pending episode
                    var targetSeason: Int? = null
                    try {
                        val allProgress = userRepository.getAllWatchProgressFlow().first()
                        val seriesProgress = allProgress.filter { wp ->
                            (wp.seriesId == seriesId || episodes.values.flatten().any { it.streamId == wp.streamId }) &&
                            rsv.squitv.domain.usecase.WatchProgressUtils.isPendingWatchProgress(wp.position, wp.duration)
                        }.sortedByDescending { it.lastWatched }

                        val latestEpisodeWp = seriesProgress.firstOrNull()
                        if (latestEpisodeWp != null) {
                            targetSeason = seasonsList.firstOrNull { season ->
                                episodes[season.seasonNumber]?.any { it.streamId == latestEpisodeWp.streamId } == true
                            }?.seasonNumber
                        }
                    } catch (_: Exception) {}

                    if (targetSeason == null) {
                        for (season in seasonsList) {
                            val eps = episodes[season.seasonNumber] ?: emptyList()
                            if (eps.any { watchProgress.containsKey(it.streamId) }) {
                                targetSeason = season.seasonNumber
                                break
                            }
                        }
                    }
                    
                    // Fallback to Season 1
                    if (targetSeason == null) {
                        targetSeason = seasonsList.find { it.seasonNumber == 1 }?.seasonNumber ?: seasonsList.first().seasonNumber
                    }

                    _selectedSeason.value = targetSeason
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeason.value = seasonNumber
    }
}
