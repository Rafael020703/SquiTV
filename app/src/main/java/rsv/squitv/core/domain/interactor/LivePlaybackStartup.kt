package rsv.squitv.core.domain.interactor

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import rsv.squitv.shared.xtream.ProviderServers

/** Called on the player's application thread. A listener is owned by each attempt. */
internal suspend fun Player.startLivePlayback(item: MediaItem): Boolean {
    val outcome = CompletableDeferred<Boolean>()
    val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && currentMediaItem?.mediaId == item.mediaId) outcome.complete(true)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (currentMediaItem?.mediaId == item.mediaId) outcome.complete(false)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && currentMediaItem?.mediaId == item.mediaId) outcome.complete(false)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST &&
                currentMediaItem?.mediaId == item.mediaId) outcome.complete(true)
        }
    }
    addListener(listener)
    return try {
        stop()
        clearMediaItems()
        setMediaItem(item)
        prepare()
        play()
        withTimeoutOrNull(ProviderServers.PLAYBACK_START_TIMEOUT_MS) { outcome.await() } == true
    } finally {
        removeListener(listener)
    }
}
