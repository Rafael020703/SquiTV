package rsv.squitv.ui.viewmodel.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.core.data.repository.StreamRepository
import rsv.squitv.core.data.repository.UserRepository
import rsv.squitv.data.local.entities.DownloadEntity
import rsv.squitv.data.local.entities.EpgReminderEntity
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.service.DownloadTracker
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.domain.usecase.ToggleFavoriteUseCase
import timber.log.Timber
import rsv.squitv.worker.ReminderWorker
import androidx.work.*
import androidx.media3.common.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

import androidx.media3.common.util.UnstableApi
import rsv.squitv.data.model.XtreamSeries
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.model.XtreamVodInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

@HiltViewModel
@UnstableApi
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val userRepository: UserRepository,
    private val epgRepository: EpgRepository,
    private val streamRepository: StreamRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadTracker: DownloadTracker,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<IptvItem>>(emptyList())
    val favorites: StateFlow<List<IptvItem>> = _favorites.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadEntity>>(emptyList())
    val downloads: StateFlow<List<DownloadEntity>> = _downloads.asStateFlow()

    private val _reminders = MutableStateFlow<List<EpgReminderEntity>>(emptyList())
    val reminders: StateFlow<List<EpgReminderEntity>> = _reminders.asStateFlow()

    private val _watchProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val watchProgress: StateFlow<Map<Int, Float>> = _watchProgress.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.getFavoritesListFlow().collect { list ->
                _favorites.value = list.map {
                    IptvItem(
                        id = it.streamId.toString(),
                        name = it.name,
                        icon = it.logo,
                        type = ContentType.fromString(it.streamType),
                        epgId = it.url,
                        containerExtension = it.containerExtension,
                        isFavorite = true,
                        rating = it.rating,
                        releaseDate = it.releaseDate,
                        categoryId = it.categoryId
                    )
                }
            }
        }

        viewModelScope.launch {
            userRepository.getAllWatchProgressFlow().collect { list ->
                val progressMap = list.associate { 
                    it.streamId to (it.position.toFloat() / it.duration.coerceAtLeast(1L).toFloat()) 
                }
                _watchProgress.value = progressMap
            }
        }

        viewModelScope.launch {
            userRepository.getAllDownloadsFlow().collect { _downloads.value = it }
        }

        viewModelScope.launch {
            epgRepository.getAllRemindersFlow().collect { _reminders.value = it }
        }
    }

    fun toggleFavorite(item: IptvItem, isFav: Boolean) {
        viewModelScope.launch { 
            val settings = settingsRepository.settingsFlow.first()
            val profileId = settings.activeProfileId
            toggleFavoriteUseCase(item, isFav, profileId)
        }
    }

    fun startDownload(item: IptvItem, seriesId: Int? = null, seasonNumber: Int? = null) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val creds = settings.credentials ?: return@launch
            val url = streamRepository.getStreamUrl(creds, item.id.toInt(), item.type.toString(), item.containerExtension)
            val mediaItem = MediaItem.Builder().setMediaId(item.id).setUri(url).build()
            
            var duration: String? = null
            try {
                if (item.type == ContentType.MOVIE) {
                    duration = catalogRepository.getVodInfo(creds, item.id.toInt()).info?.duration
                } else if (item.type == ContentType.SERIES && seriesId != null && seasonNumber != null) {
                    val (_, episodesMap) = catalogRepository.getSeriesInfo(creds, seriesId)
                    duration = episodesMap[seasonNumber]?.find { it.streamId == item.id.toInt() }?.duration
                }
            } catch (e: Exception) {
                Timber.e(e, "Error fetching duration for download")
            }

            val download = DownloadEntity(
                streamId = item.id.toInt(), name = item.name, type = item.type.toString(), icon = item.icon,
                localUri = url, status = "DOWNLOADING", container = item.containerExtension,
                duration = duration, seriesId = seriesId, seasonNumber = seasonNumber
            )
            userRepository.insertDownload(download)
            downloadTracker.startDownload(mediaItem, item.name)
        }
    }

    fun removeDownload(streamId: Int) {
        viewModelScope.launch {
            userRepository.deleteDownload(streamId)
            downloadTracker.removeDownload(streamId.toString())
        }
    }

    fun downloadSeason(seriesId: Int, seasonNumber: Int) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val creds = settings.credentials ?: return@launch
            
            val (_, episodesMap) = catalogRepository.getSeriesInfo(creds, seriesId)
            val episodes = episodesMap[seasonNumber] ?: return@launch

            episodes.forEach { episode ->
                val streamId = episode.streamId
                // Verificar se já existe ou está baixando
                if (_downloads.value.none { it.streamId == streamId }) {
                    val url = streamRepository.getStreamUrl(creds, streamId, "series", episode.containerExtension)
                    val mediaItem = MediaItem.Builder().setMediaId(streamId.toString()).setUri(url).build()
                    
                    val download = DownloadEntity(
                        streamId = streamId, 
                        name = episode.title, 
                        type = "series", 
                        icon = episode.image,
                        localUri = url, 
                        status = "DOWNLOADING", 
                        container = episode.containerExtension,
                        duration = episode.duration, 
                        seriesId = seriesId, 
                        seasonNumber = seasonNumber
                    )
                    userRepository.insertDownload(download)
                    downloadTracker.startDownload(mediaItem, download.name)
                }
            }
        }
    }

    fun addReminder(streamId: Int, programTitle: String, startTimeMillis: Long, channelName: String, channelIcon: String?) {
        viewModelScope.launch {
            val reminder = EpgReminderEntity(
                streamId = streamId,
                programTitle = programTitle,
                startTime = startTimeMillis,
                channelName = channelName,
                channelIcon = channelIcon
            )
            epgRepository.insertReminder(reminder)
            scheduleReminderWorker(reminder)
        }
    }

    fun removeReminder(streamId: Int, startTimeMillis: Long) {
        viewModelScope.launch {
            epgRepository.deleteReminder(streamId, startTimeMillis)
            WorkManager.getInstance(context).cancelUniqueWork("reminder_${streamId}_${startTimeMillis}")
        }
    }

    private fun scheduleReminderWorker(reminder: EpgReminderEntity) {
        val delay = reminder.startTime - System.currentTimeMillis()
        if (delay <= 0) return
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                ReminderWorker.KEY_PROGRAM_TITLE to reminder.programTitle,
                ReminderWorker.KEY_CHANNEL_NAME to reminder.channelName,
                ReminderWorker.KEY_STREAM_ID to reminder.streamId
            )).build()
        WorkManager.getInstance(context).enqueueUniqueWork("reminder_${reminder.streamId}_${reminder.startTime}", ExistingWorkPolicy.REPLACE, workRequest)
    }

    fun clearWatchProgress() {
        viewModelScope.launch { 
            userRepository.clearAllWatchProgress()
            _watchProgress.value = emptyMap()
        }
    }

    fun checkAndDownloadNextEpisode(seriesId: Int, currentSeason: Int, currentStreamId: Int) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.smartDownloadsEnabled) return@launch

            val creds = settings.credentials ?: return@launch
            val (_, episodesMap) = catalogRepository.getSeriesInfo(creds, seriesId)
            val episodes = episodesMap[currentSeason] ?: return@launch
            
            val currentIndex = episodes.indexOfFirst { it.streamId == currentStreamId }
            if (currentIndex != -1 && currentIndex < episodes.size - 1) {
                val nextEpisode = episodes[currentIndex + 1]
                
                // Check if already downloading or downloaded
                val allDownloads = downloads.value
                if (allDownloads.none { it.streamId == nextEpisode.streamId }) {
                    Timber.i("Smart Download: Iniciando download do próximo episódio: ${nextEpisode.title}")
                    val item = IptvItem(
                        id = nextEpisode.streamId.toString(),
                        name = nextEpisode.title,
                        icon = nextEpisode.image,
                        type = ContentType.SERIES,
                        containerExtension = nextEpisode.containerExtension
                    )
                    startDownload(item, seriesId, currentSeason)
                }
            }
        }
    }

    suspend fun getVodInfo(creds: XtreamCredentials, vodId: Int): XtreamVodInfo {
        return catalogRepository.getVodInfo(creds, vodId)
    }

    suspend fun getSeriesMetadata(creds: XtreamCredentials, seriesId: Int): XtreamSeries? {
        return catalogRepository.getSeriesMetadata(creds, seriesId)
    }

    fun getSimilarContent(item: IptvItem): StateFlow<List<IptvItem>> {
        val similarFlow = MutableStateFlow<List<IptvItem>>(emptyList())
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settingsFlow.first()
                val creds = settings.credentials ?: return@launch
                val streamId = item.id.toInt()
                val streams = catalogRepository.getStreamsByIds(listOf(streamId))
                val mainStream = streams.firstOrNull { it.streamType.lowercase() == item.type.toString() } ?: return@launch
                
                val similarItems = when(item.type) {
                    ContentType.MOVIE -> {
                        val info = catalogRepository.getVodInfo(creds, streamId).info
                        catalogRepository.getVodStreams(creds, mainStream.categoryId)
                            .filter { it.streamId.toString() != item.id }
                            .take(15)
                            .map { stream ->
                                IptvItem(stream.streamId.toString(), stream.name ?: "", stream.streamIcon, ContentType.MOVIE, categoryId = stream.categoryId)
                            }
                    }
                    ContentType.SERIES -> {
                        catalogRepository.getSeries(creds, mainStream.categoryId)
                            .filter { it.seriesId.toString() != item.id }
                            .take(15)
                            .map { s ->
                                IptvItem(s.seriesId.toString(), s.name ?: "", s.cover, ContentType.SERIES, categoryId = s.categoryId)
                            }
                    }
                    else -> emptyList()
                }
                similarFlow.value = similarItems
            } catch (e: Exception) {
                Timber.e(e, "Error fetching similar content")
            }
        }
        return similarFlow.asStateFlow()
    }
}
