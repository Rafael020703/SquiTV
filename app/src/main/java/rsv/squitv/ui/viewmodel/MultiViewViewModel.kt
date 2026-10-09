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

@HiltViewModel
@UnstableApi
class MultiViewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamRepository: StreamRepository,
    private val settingsRepository: SettingsRepository,
    @PlayerOkHttpClient private val okHttpClient: OkHttpClient,
    @PlaybackCache private val playbackCache: SimpleCache
) : ViewModel() {

    private val _players = MutableStateFlow<List<ExoPlayer?>>(listOf(null, null, null, null))
    val players: StateFlow<List<ExoPlayer?>> = _players.asStateFlow()

    private val _activeChannels = MutableStateFlow<List<XtreamStream?>>(listOf(null, null, null, null))
    val activeChannels: StateFlow<List<XtreamStream?>> = _activeChannels.asStateFlow()

    private val _focusedIndex = MutableStateFlow(0)
    val focusedIndex: StateFlow<Int> = _focusedIndex.asStateFlow()

    fun setFocus(index: Int) {
        _focusedIndex.value = index
        // Mute all except focused
        _players.value.forEachIndexed { i, player ->
            player?.volume = if (i == index) 1.0f else 0.0f
        }
    }

    fun playChannel(index: Int, stream: XtreamStream) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val creds = settings.credentials ?: return@launch
            
            val url = streamRepository.getStreamUrl(creds, stream.streamId ?: 0, "live", null)
            
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
                volume = if (index == _focusedIndex.value) 1.0f else 0.0f
            }

            val currentChannels = _activeChannels.value.toMutableList()
            currentChannels[index] = stream
            _activeChannels.value = currentChannels
        }
    }

    fun removeChannel(index: Int) {
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
        _players.value.forEach { it?.release() }
    }
}
