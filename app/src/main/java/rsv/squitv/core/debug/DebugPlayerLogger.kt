package rsv.squitv.core.debug

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import rsv.squitv.BuildConfig

class DebugPlayerLogger(
    val instanceId: Int
) : Player.Listener {

    private var lastStateName: String = "STATE_UNKNOWN"
    private var isReleased: Boolean = false

    fun onPlayerCreated() {
        if (!BuildConfig.DEBUG) return
        isReleased = false
        DebugLogger.activePlayerInstanceId.set(instanceId)
        DebugLogger.log(
            level = DebugLevel.INFO,
            category = DebugCategory.PLAYER,
            event = "PLAYER_CREATE",
            playerInstanceId = instanceId
        )
    }

    fun onPlayerReleased() {
        if (!BuildConfig.DEBUG) return
        isReleased = true
        DebugLogger.log(
            level = DebugLevel.INFO,
            category = DebugCategory.PLAYER,
            event = "PLAYER_RELEASE",
            playerInstanceId = instanceId,
            oldState = lastStateName,
            newState = "RELEASED"
        )
        DebugLogger.activePlayerState.set("RELEASED")
    }

    fun logPlayerCommand(command: String, mediaId: String? = null, extra: Map<String, String>? = null) {
        if (!BuildConfig.DEBUG) return
        if (isReleased) {
            DebugLogger.log(
                level = DebugLevel.WARN,
                category = DebugCategory.ANOMALY,
                event = "PLAYER_COMMAND_AFTER_RELEASE",
                playerInstanceId = instanceId,
                context = mapOf("command" to command, "mediaId" to (mediaId ?: "null")) + (extra ?: emptyMap())
            )
        } else {
            DebugLogger.log(
                level = DebugLevel.DEBUG,
                category = DebugCategory.PLAYER,
                event = "PLAYER_$command",
                playerInstanceId = instanceId,
                context = mapOf("mediaId" to (mediaId ?: "null")) + (extra ?: emptyMap())
            )
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (!BuildConfig.DEBUG) return

        val stateName = when (playbackState) {
            Player.STATE_IDLE -> "STATE_IDLE"
            Player.STATE_BUFFERING -> "STATE_BUFFERING"
            Player.STATE_READY -> "STATE_READY"
            Player.STATE_ENDED -> "STATE_ENDED"
            else -> "STATE_UNKNOWN_$playbackState"
        }

        val previousState = lastStateName
        lastStateName = stateName
        DebugLogger.activePlayerState.set(stateName)

        if (isReleased) {
            DebugLogger.log(
                level = DebugLevel.WARN,
                category = DebugCategory.ANOMALY,
                event = "CALLBACK_AFTER_RELEASE",
                playerInstanceId = instanceId,
                oldState = previousState,
                newState = stateName,
                error = "onPlaybackStateChanged received after player release!"
            )
            return
        }

        val level = if (playbackState == Player.STATE_IDLE && previousState == "STATE_READY") {
            DebugLevel.WARN
        } else {
            DebugLevel.INFO
        }

        DebugLogger.log(
            level = level,
            category = DebugCategory.PLAYER_STATE,
            event = "PLAYER_STATE_CHANGED",
            playerInstanceId = instanceId,
            oldState = previousState,
            newState = stateName
        )
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!BuildConfig.DEBUG) return
        if (isReleased) {
            DebugLogger.log(
                level = DebugLevel.WARN,
                category = DebugCategory.ANOMALY,
                event = "CALLBACK_AFTER_RELEASE",
                playerInstanceId = instanceId,
                context = mapOf("isPlaying" to isPlaying.toString())
            )
            return
        }

        DebugLogger.log(
            level = DebugLevel.DEBUG,
            category = DebugCategory.PLAYER_STATE,
            event = "PLAYER_IS_PLAYING_CHANGED",
            playerInstanceId = instanceId,
            context = mapOf("isPlaying" to isPlaying.toString())
        )
    }

    override fun onRenderedFirstFrame() {
        if (!BuildConfig.DEBUG) return
        DebugLogger.log(
            level = DebugLevel.INFO,
            category = DebugCategory.PLAYER,
            event = "PLAYER_RENDERED_FIRST_FRAME",
            playerInstanceId = instanceId
        )
    }

    override fun onPlayerError(error: PlaybackException) {
        if (!BuildConfig.DEBUG) return

        val causeMsg = error.cause?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "No cause"
        val errorText = "ErrorCode=${error.errorCode} (${error.errorCodeName}) - $causeMsg"

        DebugLogger.log(
            level = DebugLevel.ERROR,
            category = DebugCategory.PLAYER_ERROR,
            event = "PLAYER_ERROR",
            playerInstanceId = instanceId,
            error = errorText,
            stackTrace = Log.getStackTraceString(error)
        )
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!BuildConfig.DEBUG) return
        val mediaId = mediaItem?.mediaId ?: "none"
        val reasonName = when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
            else -> "UNKNOWN_$reason"
        }

        DebugLogger.log(
            level = DebugLevel.INFO,
            category = DebugCategory.MEDIA,
            event = "MEDIA_ITEM_TRANSITION",
            playerInstanceId = instanceId,
            context = mapOf("mediaId" to mediaId, "reason" to reasonName)
        )
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (!BuildConfig.DEBUG) return
        val reasonName = when (reason) {
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
            Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
            Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
            Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
            Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
            else -> "UNKNOWN_$reason"
        }

        DebugLogger.log(
            level = DebugLevel.DEBUG,
            category = DebugCategory.PLAYER,
            event = "POSITION_DISCONTINUITY",
            playerInstanceId = instanceId,
            context = mapOf(
                "reason" to reasonName,
                "oldPosMs" to oldPosition.positionMs.toString(),
                "newPosMs" to newPosition.positionMs.toString()
            )
        )
    }

    override fun onTracksChanged(tracks: Tracks) {
        if (!BuildConfig.DEBUG) return
        DebugLogger.log(
            level = DebugLevel.DEBUG,
            category = DebugCategory.PLAYER,
            event = "TRACKS_CHANGED",
            playerInstanceId = instanceId,
            context = mapOf("groupCount" to tracks.groups.size.toString())
        )
    }
}
