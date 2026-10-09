package rsv.squitv.data.service

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @Inject
    lateinit var player: ExoPlayer

    private val mediaSessionCallback = object : MediaSession.Callback {
        @Suppress("DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            if (playerCommand == Player.COMMAND_SEEK_TO_NEXT || playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                session.broadcastCustomCommand(
                    SessionCommand(ACTION_NEXT_CHANNEL, Bundle.EMPTY),
                    Bundle.EMPTY
                )
                return 0
            } else if (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS || playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                session.broadcastCustomCommand(
                    SessionCommand(ACTION_PREVIOUS_CHANNEL, Bundle.EMPTY),
                    Bundle.EMPTY
                )
                return 0
            }
            return super.onPlayerCommandRequest(session, controllerInfo, playerCommand)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_NEXT_CHANNEL || customCommand.customAction == ACTION_PREVIOUS_CHANNEL) {
                session.broadcastCustomCommand(
                    SessionCommand(customCommand.customAction, Bundle.EMPTY),
                    Bundle.EMPTY
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(mediaSessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player ?: player
        try {
            p.stop()
            p.clearMediaItems()
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        try {
            player.stop()
            player.clearMediaItems()
            player.release()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val ACTION_NEXT_CHANNEL = "rsv.squitv.NEXT_CHANNEL"
        const val ACTION_PREVIOUS_CHANNEL = "rsv.squitv.PREVIOUS_CHANNEL"
    }
}
