package rsv.squitv.core.domain.interactor

import androidx.media3.common.Player
import rsv.squitv.domain.model.ContentType
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlaybackWatchdog session state holder.
 * Active 500ms polling loop has been removed in Phase 2 to allow Media3 ExoPlayer
 * to handle buffering and state management event-driven without active polling overhead.
 */
@Singleton
class PlaybackWatchdog @Inject constructor() {

    @Volatile private var activeSessionId: Int? = null
    @Volatile private var activeStreamId: Int? = null
    @Volatile private var activeChannelSwitchId: String? = null

    val currentSessionId: Int? get() = activeSessionId
    val currentStreamId: Int? get() = activeStreamId
    val currentChannelSwitchId: String? get() = activeChannelSwitchId

    fun startMonitoring(
        player: Player,
        contentType: ContentType,
        sessionId: Int,
        streamId: Int,
        channelSwitchId: String = "default",
        scope: CoroutineScope,
        onHangDetected: (reason: String) -> Unit
    ) {
        stop("NEW_MONITORING_STARTED")
        activeSessionId = sessionId
        activeStreamId = streamId
        activeChannelSwitchId = channelSwitchId
        Timber.d("[WATCHDOG_EVENT_DRIVEN] Registered session=$sessionId stream=$streamId channelSwitchId=$channelSwitchId (polling disabled)")
    }

    fun stop(reason: String = "EXPLICIT_STOP") {
        val sess = activeSessionId
        val str = activeStreamId
        if (sess != null || str != null) {
            Timber.d("[WATCHDOG_EVENT_DRIVEN] Cleared session=$sess stream=$str reason=$reason")
        }
        activeSessionId = null
        activeStreamId = null
        activeChannelSwitchId = null
    }
}
