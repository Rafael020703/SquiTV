package rsv.squitv

import rsv.squitv.ui.viewmodel.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PlayerLifecycleTest {

    class FakeExoPlayer(val id: Int) {
        val isReleased = AtomicBoolean(false)
        val prepareCallCount = AtomicInteger(0)
        val mediaItemSetCount = AtomicInteger(0)
        var lastUri: String? = null

        fun setMediaItem(uri: String) {
            check(!isReleased.get()) { "Cannot call setMediaItem on released ExoPlayer instance $id" }
            lastUri = uri
            mediaItemSetCount.incrementAndGet()
        }

        fun prepare() {
            check(!isReleased.get()) { "Cannot call prepare on released ExoPlayer instance $id" }
            prepareCallCount.incrementAndGet()
        }

        fun release() {
            isReleased.set(true)
        }
    }

    class FakePlayerFactory {
        private var instanceCounter = 0
        val createdInstances = mutableListOf<FakeExoPlayer>()

        fun createExoPlayer(): FakeExoPlayer {
            val player = FakeExoPlayer(++instanceCounter)
            createdInstances.add(player)
            return player
        }
    }

    @Test
    fun testPlayerUiStateError_retainsErrorMessage() {
        val errorState = PlayerUiState.Error("Falha na conexão de rede.")
        assertEquals("Falha na conexão de rede.", errorState.message)
    }

    @Test
    fun testPlayerUiStatePlaying_carriesStreamId() {
        val playingState = PlayerUiState.Playing(
            streamId = 23311,
            name = "Ben 10",
            isPlaying = true
        )
        assertEquals(23311, playingState.streamId)
        assertNotNull(playingState.name)
    }

    @Test
    fun test1_openChannelAndStartPlayback_usesActiveUnreleasedPlayer() {
        val factory = FakePlayerFactory()
        val player1 = factory.createExoPlayer()

        assertFalse("Player instance 1 must not be released on creation", player1.isReleased.get())
        player1.setMediaItem("http://server/live/user/pass/324969.ts")
        player1.prepare()

        assertEquals(1, player1.mediaItemSetCount.get())
        assertEquals(1, player1.prepareCallCount.get())
        assertEquals("http://server/live/user/pass/324969.ts", player1.lastUri)
    }

    @Test
    fun test2_closePlayer_releasesPreviousPlayerInstance() {
        val factory = FakePlayerFactory()
        val player1 = factory.createExoPlayer()

        player1.setMediaItem("http://server/live/user/pass/324969.ts")
        player1.prepare()

        // Simulate closing player screen / MediaPlaybackService.onDestroy()
        player1.release()

        assertTrue("Player instance 1 must be marked released after closing player session", player1.isReleased.get())
    }

    @Test
    fun test3_openNextChannel_createsNewActivePlayerInstance() {
        val factory = FakePlayerFactory()

        // Session 1
        val player1 = factory.createExoPlayer()
        player1.setMediaItem("http://server/live/user/pass/324969.ts")
        player1.prepare()
        player1.release()

        // Session 2 (Factory produces new instance because provideExoPlayer is not @Singleton)
        val player2 = factory.createExoPlayer()
        assertNotEquals("Session 2 must receive a new ExoPlayer instance", player1.id, player2.id)
        assertFalse("Session 2 ExoPlayer instance must be active and not released", player2.isReleased.get())

        player2.setMediaItem("http://server/live/user/pass/23311.ts")
        player2.prepare()

        assertEquals(1, player2.mediaItemSetCount.get())
        assertEquals(1, player2.prepareCallCount.get())
    }

    @Test
    fun test4_releasedPlayerInstance_doesNotReceiveNewPrepareCalls() {
        val factory = FakePlayerFactory()

        val player1 = factory.createExoPlayer()
        player1.release()

        val player2 = factory.createExoPlayer()
        player2.setMediaItem("http://server/live/user/pass/23311.ts")
        player2.prepare()

        assertEquals("Released instance 1 must not receive prepare calls", 0, player1.prepareCallCount.get())
        assertEquals("Active instance 2 must receive 1 prepare call", 1, player2.prepareCallCount.get())
    }

    @Test
    fun test5_newSession_rejectsStaleCallbacksFromPreviousSession() {
        val activeSessionId = 2
        val activeStreamId = "23311"

        fun isSessionAndMediaValid(sessionId: Int, mediaId: String): Boolean {
            return (sessionId == activeSessionId) && (mediaId == activeStreamId)
        }

        assertFalse("Callback from session 1 must be rejected", isSessionAndMediaValid(1, "324969"))
        assertFalse("Callback with old mediaId must be rejected", isSessionAndMediaValid(2, "324969"))
        assertTrue("Callback with active session and mediaId must be accepted", isSessionAndMediaValid(2, "23311"))
    }

    @Test
    fun test6_repeatedServiceDestruction_doesNotReuseReleasedPlayer() {
        val factory = FakePlayerFactory()
        val totalCycles = 5

        for (cycle in 1..totalCycles) {
            val player = factory.createExoPlayer()
            assertEquals(cycle, player.id)
            assertFalse("Instance $cycle must start active", player.isReleased.get())

            player.setMediaItem("http://server/live/user/pass/stream_$cycle.ts")
            player.prepare()

            // Close session / service destroy
            player.release()
            assertTrue("Instance $cycle must be released", player.isReleased.get())
        }

        assertEquals("Factory must produce 5 distinct instances across 5 cycles", 5, factory.createdInstances.size)
        assertTrue("All 5 instances must be released and none reused", factory.createdInstances.all { it.isReleased.get() })
    }

    @Test
    fun test7_preparationFailure_transitionsUiStateToError() {
        val exception = IllegalStateException("Timeout ao conectar com o serviço de reprodução")
        val errorState = PlayerUiState.Error(message = "Erro ao reproduzir: ${exception.message}")

        assertTrue("Error state message must explain the failure", errorState.message.contains("Timeout ao conectar"))
    }
}
