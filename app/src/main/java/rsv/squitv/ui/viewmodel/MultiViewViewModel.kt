package rsv.squitv.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import rsv.squitv.IptvApplication
import rsv.squitv.core.data.repository.StreamRepository
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.di.PlaybackCache
import rsv.squitv.di.PlayerOkHttpClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import rsv.squitv.shared.catalog.MultiViewPolicy
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.PlaybackSourceRepository
import rsv.squitv.data.model.SettingsBackupCodec
import rsv.squitv.shared.catalog.LiveCatalogView
import rsv.squitv.shared.xtream.LiveCategory
import rsv.squitv.shared.xtream.LiveChannel
import rsv.squitv.domain.model.ChannelQuality
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
@UnstableApi
class MultiViewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamRepository: StreamRepository,
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: CatalogRepository,
    @PlayerOkHttpClient private val okHttpClient: OkHttpClient,
    @PlaybackCache private val playbackCache: SimpleCache
) : ViewModel() {

    private val _players = MutableStateFlow<List<ExoPlayer?>>(List(MultiViewPolicy.SLOT_COUNT) { null })
    val players: StateFlow<List<ExoPlayer?>> = _players.asStateFlow()

    private val _activeChannels = MutableStateFlow<List<XtreamStream?>>(List(MultiViewPolicy.SLOT_COUNT) { null })
    val activeChannels: StateFlow<List<XtreamStream?>> = _activeChannels.asStateFlow()

    private val _focusedIndex = MutableStateFlow(0)
    val focusedIndex: StateFlow<Int> = _focusedIndex.asStateFlow()
    private val _pickerLoading = MutableStateFlow(false)
    val pickerLoading = _pickerLoading.asStateFlow()
    private val _pickerError = MutableStateFlow<String?>(null)
    val pickerError = _pickerError.asStateFlow()
    private var pickerJob: Job? = null
    private var pickerRevision = 0L
    private val playJobs = arrayOfNulls<Job>(MultiViewPolicy.SLOT_COUNT)
    val pickerCatalog = combine(catalogRepository.observeBrowseCatalog("live"), settingsRepository.settingsFlow) { (categories, streams), settings ->
        val credentials = settings.credentials ?: return@combine LiveCatalogView()
        val key = "${SettingsBackupCodec.accountKey(credentials)}_live"
        LiveCatalogView.build(categories.map { LiveCategory(it.id, it.name) }, streams.filter { it.id > 0 }.map {
            LiveChannel(it.id, it.name, it.categoryId, it.logo, it.url)
        }, PlaybackSourceRepository.scope(settings), settings.groupLiveChannels,
            ChannelQuality.fromPreference(settings.preferredChannelQuality), settings.categoryOrderMap[key].orEmpty(),
            settings.hiddenCategoriesMap[key].orEmpty())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveCatalogView())

    fun openPicker() {
        if (pickerJob?.isActive == true) return
        val revision = ++pickerRevision
        pickerJob = viewModelScope.launch {
            _pickerLoading.value = true; _pickerError.value = null
            try {
                val credentials = settingsRepository.settingsFlow.first().credentials ?: return@launch
                withContext(Dispatchers.IO) {
                    catalogRepository.getLiveCategories(credentials)
                    catalogRepository.getLiveStreams(credentials)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                ensureActive()
                if (revision == pickerRevision) _pickerError.value = "Não foi possível carregar as categorias e canais. Tente novamente."
            }
            finally { if (revision == pickerRevision) _pickerLoading.value = false }
        }
    }
    fun closePicker() { ++pickerRevision; pickerJob?.cancel(); pickerJob = null; _pickerLoading.value = false }
    fun playSelection(index: Int, channel: LiveChannel) {
        if (pickerCatalog.value.channels.none { it == channel }) return
        playChannel(index, XtreamStream(streamId = channel.id, name = channel.name,
            categoryId = channel.categoryId, streamIcon = channel.iconUrl, epgChannelId = channel.epgId))
    }

    fun setFocus(index: Int) {
        if (index !in 0 until MultiViewPolicy.SLOT_COUNT) return
        _focusedIndex.value = index
        // Mute all except focused
        _players.value.forEachIndexed { i, player ->
            player?.volume = MultiViewPolicy.audioVolume(i, index)
        }
    }

    fun playChannel(index: Int, stream: XtreamStream) {
        if (index !in 0 until MultiViewPolicy.SLOT_COUNT || (stream.streamId ?: 0) <= 0) return
        playJobs[index]?.cancel()
        playJobs[index] = viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val creds = settings.credentials ?: return@launch
            
            val url = streamRepository.getStreamUrl(creds, stream.streamId ?: 0, "live", null)
            ensureActive()
            
            var player = _players.value[index]
            if (player == null) {
                player = createPlayer()
                val currentPlayers = _players.value.toMutableList()
                currentPlayers[index] = player
                _players.value = currentPlayers
            }

            player.apply {
                stop()
                clearMediaItems()
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
                volume = MultiViewPolicy.audioVolume(index, _focusedIndex.value)
            }

            val currentChannels = _activeChannels.value.toMutableList()
            currentChannels[index] = stream
            _activeChannels.value = currentChannels
        }
    }

    fun removeChannel(index: Int) {
        if (index !in 0 until MultiViewPolicy.SLOT_COUNT) return
        playJobs[index]?.cancel()
        val currentPlayers = _players.value.toMutableList()
        currentPlayers[index]?.release()
        currentPlayers[index] = null
        _players.value = currentPlayers

        val currentChannels = _activeChannels.value.toMutableList()
        currentChannels[index] = null
        _activeChannels.value = currentChannels
    }

    private fun createPlayer(): ExoPlayer {
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(rsv.squitv.data.network.RetrofitClient.USER_AGENT)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(playbackCache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5000, 15000, 1000, 2000)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build()
    }

    override fun onCleared() {
        super.onCleared()
        closePicker()
        playJobs.forEach { it?.cancel() }
        _players.value.forEach { it?.release() }
    }
}
