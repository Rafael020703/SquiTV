package rsv.squitv.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.data.local.entities.EpgProgramEntity
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.worker.EpgSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpgViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val epgRepository: EpgRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _epgChannels = MutableStateFlow<List<IptvItem>>(emptyList())
    val epgChannels: StateFlow<List<IptvItem>> = _epgChannels.asStateFlow()

    private val _epgGridData = MutableStateFlow<Map<String, List<EpgProgramEntity>>>(emptyMap())
    val epgGridData: StateFlow<Map<String, List<EpgProgramEntity>>> = _epgGridData.asStateFlow()

    private val _currentTimeRange = MutableStateFlow(getCurrentTimeRange())
    val currentTimeRange: StateFlow<Pair<Long, Long>> = _currentTimeRange.asStateFlow()

    private fun getCurrentTimeRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis() / 1000
        return now to now + (6 * 3600) // 6 hours range
    }

    fun syncEpg() {
        val workRequest = OneTimeWorkRequestBuilder<EpgSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("epg_sync", ExistingWorkPolicy.KEEP, workRequest)
    }

    private var epgRangeJob: kotlinx.coroutines.Job? = null

    fun loadChannelsForEpg(categoryId: String?, favorites: List<IptvItem>) {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settingsFlow.first()
                val creds = settings.credentials ?: return@launch
                
                val streams = if (categoryId == null) {
                    catalogRepository.getLiveStreams(creds)
                } else {
                    catalogRepository.getLiveStreams(creds, categoryId)
                }
                
                val favoriteLiveIds = favorites.filter { it.type == ContentType.LIVE }.mapTo(HashSet()) { it.id }
                val grouped = catalogRepository.groupLiveStreams(streams).map { item ->
                    item.copy(isFavorite = favoriteLiveIds.contains(item.id))
                }
                _epgChannels.value = grouped
                
                // Load EPG for visible range
                loadEpgForRange()
            } catch (_: Exception) {}
        }
    }

    fun loadEpgForRange() {
        epgRangeJob?.cancel()
        epgRangeJob = viewModelScope.launch {
            val (start, stop) = _currentTimeRange.value
            val programs = epgRepository.getEpgInRange(start, stop)
            _epgGridData.value = programs.groupBy { it.channelId }
        }
    }

    fun shiftTimeRange(hours: Int) {
        val (start, stop) = _currentTimeRange.value
        val shift = hours * 3600L
        _currentTimeRange.value = (start + shift) to (stop + shift)
        loadEpgForRange()
    }
}
