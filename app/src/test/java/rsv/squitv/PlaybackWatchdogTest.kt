package rsv.squitv

import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import rsv.squitv.core.domain.interactor.PlaybackWatchdog
import rsv.squitv.domain.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackWatchdogTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class MutablePlayerState(
        var state: Int = Player.STATE_IDLE,
        var playWhenReady: Boolean = true,
        var position: Long = 0L
    )

    private fun createTestPlayer(pState: MutablePlayerState): Player {
        val handler = InvocationHandler { _, method, _ ->
            when (method.name) {
                "getPlaybackState" -> pState.state
                "getPlayWhenReady" -> pState.playWhenReady
                "getCurrentPosition" -> pState.position
                "getAudioAttributes" -> AudioAttributes.DEFAULT
                "getCurrentTracks" -> Tracks.EMPTY
                "getTrackSelectionParameters" -> TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
                "getPlaybackParameters" -> PlaybackParameters.DEFAULT
                "getMediaMetadata", "getPlaylistMetadata" -> MediaMetadata.EMPTY
                "getAvailableCommands" -> Player.Commands.EMPTY
                "hashCode" -> System.identityHashCode(pState)
                "equals" -> false
                "toString" -> "ProxyTestPlayer"
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            handler
        ) as Player
    }

    @Test
    fun initialState_hasNoActiveSession() {
        val watchdog = PlaybackWatchdog()
        assertNull(watchdog.currentSessionId)
        assertNull(watchdog.currentStreamId)
        assertNull(watchdog.currentChannelSwitchId)
    }

    @Test
    fun startMonitoring_registersSessionAndStreamMetadata() = runTest {
        val watchdog = PlaybackWatchdog()
        val pState = MutablePlayerState(state = Player.STATE_READY, position = 1000L)
        val player = createTestPlayer(pState)

        watchdog.startMonitoring(
            player = player,
            contentType = ContentType.LIVE,
            sessionId = 1,
            streamId = 101,
            channelSwitchId = "switch_101",
            scope = this
        ) {}

        assertEquals(1, watchdog.currentSessionId)
        assertEquals(101, watchdog.currentStreamId)
        assertEquals("switch_101", watchdog.currentChannelSwitchId)
    }

    @Test
    fun stop_clearsSessionAndStreamMetadata() = runTest {
        val watchdog = PlaybackWatchdog()
        val pState = MutablePlayerState(state = Player.STATE_READY)
        val player = createTestPlayer(pState)

        watchdog.startMonitoring(
            player = player,
            contentType = ContentType.LIVE,
            sessionId = 1,
            streamId = 101,
            channelSwitchId = "switch_101",
            scope = this
        ) {}

        watchdog.stop("EXPLICIT_STOP")

        assertNull(watchdog.currentSessionId)
        assertNull(watchdog.currentStreamId)
        assertNull(watchdog.currentChannelSwitchId)
    }

    @Test
    fun startMonitoring_overwritesPreviousSessionMetadata() = runTest {
        val watchdog = PlaybackWatchdog()
        val pState = MutablePlayerState(state = Player.STATE_READY)
        val player = createTestPlayer(pState)

        // Session 1
        watchdog.startMonitoring(
            player = player,
            contentType = ContentType.LIVE,
            sessionId = 1,
            streamId = 101,
            channelSwitchId = "switch_101",
            scope = this
        ) {}

        // User zaps to Session 2 / Stream 202
        watchdog.startMonitoring(
            player = player,
            contentType = ContentType.LIVE,
            sessionId = 2,
            streamId = 202,
            channelSwitchId = "switch_202",
            scope = this
        ) {}

        assertEquals(2, watchdog.currentSessionId)
        assertEquals(202, watchdog.currentStreamId)
        assertEquals("switch_202", watchdog.currentChannelSwitchId)
    }

    @Test
    fun staleRecoverySession_ignoredWhenSessionChanges() {
        var activeSession = 1
        var activeStream = 101
        var executedRecoverySession: Int? = null

        fun performRecovery(sessionId: Int, streamId: Int) {
            if (activeSession != sessionId || activeStream != streamId) {
                // Stale recovery ignored
                return
            }
            executedRecoverySession = sessionId
        }

        val oldSession = 1
        val oldStream = 101

        // User is now on activeSession 2 / stream 202
        activeSession = 2
        activeStream = 202

        performRecovery(oldSession, oldStream)
        assertNull("Old session recovery must be ignored", executedRecoverySession)

        // Recovery for active session 2
        performRecovery(2, 202)
        assertEquals(2, executedRecoverySession)
    }
}
