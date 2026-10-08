package rsv.squitv.core.domain.interactor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import rsv.squitv.data.service.MediaPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import rsv.squitv.core.debug.DebugPlayerLogger
import rsv.squitv.core.debug.DebugLogger
import rsv.squitv.core.debug.DebugLevel
import rsv.squitv.core.debug.DebugCategory

/**
 * PlaybackManager responsible for the technical lifecycle of the MediaController.
 * It handles connecting to the MediaPlaybackService and releasing the controller.
 */
@UnstableApi
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed class Event {
        data class PlaybackStateChanged(val state: Int, val mediaId: String? = null) : Event()
        data class TracksChanged(val tracks: Tracks, val mediaId: String? = null) : Event()
        data class IsPlayingChanged(val isPlaying: Boolean, val mediaId: String? = null) : Event()
        data class PlayerError(val error: PlaybackException, val mediaId: String? = null) : Event()
        data class RenderedFirstFrame(val mediaId: String? = null) : Event()
        data object NextChannelRequested : Event()
        data object PreviousChannelRequested : Event()
    }

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    private val _playerState = MutableStateFlow<Player?>(null)
    val playerState: StateFlow<Player?> = _playerState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    
    companion object {
        private var instanceCounter = 0
    }
    val instanceId = ++instanceCounter
    private val debugPlayerLogger = DebugPlayerLogger(instanceId)

    private var _player: Player? = null
    val player: Player? get() = _player

    private val controllerListener = object : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (command.customAction) {
                MediaPlaybackService.ACTION_NEXT_CHANNEL -> {
                    _events.tryEmit(Event.NextChannelRequested)
                }
                MediaPlaybackService.ACTION_PREVIOUS_CHANNEL -> {
                    _events.tryEmit(Event.PreviousChannelRequested)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val mediaId = _player?.currentMediaItem?.mediaId
            Timber.d("[PLAYBACK_MANAGER][instance=$instanceId] onPlaybackStateChanged -> state=$playbackState, mediaId=$mediaId")
            _events.tryEmit(Event.PlaybackStateChanged(playbackState, mediaId))
        }

        override fun onTracksChanged(tracks: Tracks) {
            val mediaId = _player?.currentMediaItem?.mediaId
            Timber.d("[PLAYBACK_MANAGER][instance=$instanceId] onTracksChanged -> mediaId=$mediaId")
            _events.tryEmit(Event.TracksChanged(tracks, mediaId))
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val mediaId = _player?.currentMediaItem?.mediaId
            Timber.d("[PLAYBACK_MANAGER][instance=$instanceId] onIsPlayingChanged -> isPlaying=$isPlaying, mediaId=$mediaId")
            _events.tryEmit(Event.IsPlayingChanged(isPlaying, mediaId))
        }

        override fun onRenderedFirstFrame() {
            val mediaId = _player?.currentMediaItem?.mediaId
            Timber.d("[PLAYBACK_MANAGER][instance=$instanceId] onRenderedFirstFrame -> mediaId=$mediaId")
            _events.tryEmit(Event.RenderedFirstFrame(mediaId))
        }

        override fun onPlayerError(error: PlaybackException) {
            val mediaId = _player?.currentMediaItem?.mediaId
            Timber.e(error, "[PLAYBACK_MANAGER][instance=$instanceId] onPlayerError -> mediaId=$mediaId, errorCode=${error.errorCode}")
            _events.tryEmit(Event.PlayerError(error, mediaId))
        }
    }

    /**
     * Connects to the MediaPlaybackService and retrieves the MediaController.
     * 
     * @param onConnected Callback triggered when the controller is ready.
     */
    fun connect(onConnected: (Player) -> Unit) {
        if (controllerFuture != null) return

        val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                _player = controller
                _playerState.value = controller
                debugPlayerLogger.onPlayerCreated()
                controller.addListener(playerListener)
                controller.addListener(debugPlayerLogger)
                onConnected(controller)
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to MediaSession")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Releases the MediaController connection.
     */
    fun release() {
        debugPlayerLogger.onPlayerReleased()
        _player?.removeListener(debugPlayerLogger)
        _player?.removeListener(playerListener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controllerFuture = null
        _player = null
        _playerState.value = null
    }

    /**
     * Stops playback completely, clears items, releases controller and stops MediaPlaybackService.
     */
    fun stopAndDisconnect() {
        try {
            _player?.stop()
            _player?.clearMediaItems()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping player")
        }
        release()
        try {
            val intent = Intent(context, MediaPlaybackService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error stopping MediaPlaybackService")
        }
    }

    // --- Technical Transport Commands ---

    fun play() {
        debugPlayerLogger.logPlayerCommand("PLAY", _player?.currentMediaItem?.mediaId)
        Timber.d("[PLAYBACK_MANAGER][instance=$instanceId] play -> mediaId=${_player?.currentMediaItem?.mediaId}")
        _player?.play()
    }

    fun pause() {
        debugPlayerLogger.logPlayerCommand("PAUSE", _player?.currentMediaItem?.mediaId)
        Timber.d("[PLAYBACK_MANAGER][instance=$instanceId] pause -> mediaId=${_player?.currentMediaItem?.mediaId}")
        _player?.pause()
    }

    fun stop() {
        debugPlayerLogger.logPlayerCommand("STOP", _player?.currentMediaItem?.mediaId)
        Timber.d("[PLAYBACK_MANAGER][instance=$instanceId] stop -> mediaId=${_player?.currentMediaItem?.mediaId}")
        _player?.stop()
    }

    fun clearMediaItems() {
        debugPlayerLogger.logPlayerCommand("CLEAR_MEDIA_ITEMS", _player?.currentMediaItem?.mediaId)
        Timber.d("[PLAYBACK_MANAGER][instance=$instanceId] clearMediaItems -> mediaId=${_player?.currentMediaItem?.mediaId}")
        _player?.clearMediaItems()
    }

    /**
     * Immediately stops playback and clears media items on the player.
     * Crucial during channel zapping / stream changes to close the previous stream's HTTP socket
     * BEFORE a new stream request is initiated, preventing overlapping active connection count.
     */
    fun stopAndClear() {
        val previousMediaId = _player?.currentMediaItem?.mediaId
        debugPlayerLogger.logPlayerCommand("STOP_AND_CLEAR", previousMediaId)
        val t0 = System.currentTimeMillis()
        Timber.d("[PLAYBACK_STOP_START] instance=$instanceId previousMediaId=$previousMediaId timestamp=$t0")
        try {
            _player?.stop()
            val tStop = System.currentTimeMillis()
            Timber.d("[PLAYBACK_STOP_PLAYER_STOP] instance=$instanceId previousMediaId=$previousMediaId stopDur=${tStop - t0}ms timestamp=$tStop")
            _player?.clearMediaItems()
            val tClear = System.currentTimeMillis()
            Timber.d("[PLAYBACK_CLEAR_MEDIA_ITEMS] instance=$instanceId previousMediaId=$previousMediaId clearDur=${tClear - tStop}ms timestamp=$tClear")
        } catch (e: Exception) {
            Timber.e(e, "[PLAYER_SESSION][instance=$instanceId] Error during stopAndClear")
        }
        val tEnd = System.currentTimeMillis()
        Timber.d("[PLAYBACK_STOP_END] instance=$instanceId previousMediaId=$previousMediaId totalStopDur=${tEnd - t0}ms timestamp=$tEnd")
    }

    fun setMediaItem(item: MediaItem) {
        debugPlayerLogger.logPlayerCommand("SET_MEDIA_ITEM", item.mediaId)
        Timber.d("[PLAYBACK_MANAGER][instance=$instanceId] setMediaItem -> mediaId=${item.mediaId}")
        _player?.setMediaItem(item)
    }

    fun prepare() {
        debugPlayerLogger.logPlayerCommand("PREPARE", _player?.currentMediaItem?.mediaId)
        Timber.d("[PLAYBACK_MANAGER][instance=$instanceId] prepare -> mediaId=${_player?.currentMediaItem?.mediaId}")
        _player?.prepare()
    }

    fun seekTo(positionMs: Long) {
        _player?.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        _player?.setPlaybackSpeed(speed)
    }

    fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        _player?.trackSelectionParameters = parameters
    }

    fun clearTrackOverrides() {
        _player?.let { p ->
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .clearOverrides()
                .build()
        }
    }

    fun requestNextChannel() {
        _events.tryEmit(Event.NextChannelRequested)
    }

    fun requestPreviousChannel() {
        _events.tryEmit(Event.PreviousChannelRequested)
    }
}
