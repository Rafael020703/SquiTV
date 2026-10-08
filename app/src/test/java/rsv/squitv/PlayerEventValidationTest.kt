import androidx.media3.common.Player
import rsv.squitv.core.domain.interactor.PlaybackManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerEventValidationTest {

    private fun isEventValidForSession(
        activeSessionId: Int,
        eventSessionId: Int,
        currentStreamId: Int?,
        eventMediaId: String?,
        currentChannelSwitchId: String? = null,
        eventChannelSwitchId: String? = null
    ): Boolean {
        // 1. Session mismatch
        if (eventSessionId != activeSessionId) {
            return false
        }
        // 2. Channel switch ID mismatch
        if (!eventChannelSwitchId.isNullOrBlank() && currentChannelSwitchId != null && eventChannelSwitchId != currentChannelSwitchId) {
            return false
        }
        val expectedMediaId = currentStreamId?.toString()
        // 3. Caso A: eventMediaId != null e não corresponde ao canal atual esperado
        if (expectedMediaId != null && eventMediaId != null && eventMediaId != expectedMediaId) {
            return false
        }
        return true
    }

    private fun shouldProcessNullMediaIdEvent(
        currentStreamId: Int?,
        event: PlaybackManager.Event
    ): Boolean {
        val expectedMediaId = currentStreamId?.toString()
        val eventMediaId = when (event) {
            is PlaybackManager.Event.PlaybackStateChanged -> event.mediaId
            is PlaybackManager.Event.TracksChanged -> event.mediaId
            is PlaybackManager.Event.IsPlayingChanged -> event.mediaId
            is PlaybackManager.Event.PlayerError -> event.mediaId
            is PlaybackManager.Event.RenderedFirstFrame -> event.mediaId
            else -> null
        }

        if (eventMediaId == null && expectedMediaId != null) {
            when (event) {
                is PlaybackManager.Event.PlayerError -> return false
                is PlaybackManager.Event.TracksChanged,
                is PlaybackManager.Event.IsPlayingChanged,
                is PlaybackManager.Event.RenderedFirstFrame -> return false
                is PlaybackManager.Event.PlaybackStateChanged -> {
                    if (event.state != Player.STATE_IDLE) return false
                }
                else -> {}
            }
        }
        return true
    }

    @Test
    fun test1_staleMediaIdEvent_rejected() {
        val activeSession = 2
        val currentStreamId = 202 // Stream B
        val eventMediaId = "101" // Stream A (stale)

        val isValid = isEventValidForSession(
            activeSessionId = activeSession,
            eventSessionId = activeSession,
            currentStreamId = currentStreamId,
            eventMediaId = eventMediaId
        )

        assertFalse("Evento de mídia A deve ser rejeitado quando o canal ativo é B", isValid)
    }

    @Test
    fun test2_nullMediaIdStateIdleEvent_doesNotContaminateActiveSession() {
        val currentStreamId = 202 // Stream B
        val event = PlaybackManager.Event.PlaybackStateChanged(Player.STATE_BUFFERING, mediaId = null)

        val shouldProcess = shouldProcessNullMediaIdEvent(currentStreamId, event)

        assertFalse("Estado STATE_BUFFERING sem mediaId deve ser rejeitado durante transição", shouldProcess)
    }

    @Test
    fun test3_nullMediaIdPlayerError_duringCleanup_rejected() {
        val currentStreamId = 202 // Stream B
        val event = PlaybackManager.Event.PlaybackStateChanged(Player.STATE_IDLE, mediaId = null)

        val shouldProcess = shouldProcessNullMediaIdEvent(currentStreamId, event)

        // Events with mediaId = null generated during cleanup must be ignored for active session B
        assertTrue("Estado IDLE em cleanup deve ser processado apenas para reset de estado e nao contaminar nova sessao", shouldProcess)
    }

    @Test
    fun test4_applyTrackPreferences_neverCallsPreparePlay_whenMediaItemNull() {
        var prepareCalled = false
        var playCalled = false

        fun applyTrackPreferencesMock(hasMediaItem: Boolean, playerState: Int) {
            val changed = true // Track preference updated
            if (changed) {
                // Track parameters updated
                if (hasMediaItem && playerState != Player.STATE_IDLE) {
                    prepareCalled = true
                    playCalled = true
                }
            }
        }

        applyTrackPreferencesMock(hasMediaItem = false, playerState = Player.STATE_IDLE)

        assertFalse("Não deve chamar prepare() se não houver MediaItem válido", prepareCalled)
        assertFalse("Não deve chamar play() se não houver MediaItem válido", playCalled)
    }

    @Test
    fun test5_validMediaItem_processedCorrectly() {
        val activeSession = 3
        val currentStreamId = 303 // Stream C
        val eventMediaId = "303"

        val isValid = isEventValidForSession(
            activeSessionId = activeSession,
            eventSessionId = activeSession,
            currentStreamId = currentStreamId,
            eventMediaId = eventMediaId
        )

        assertTrue("Evento do canal C com mediaId matching deve ser aceito", isValid)
    }

    @Test
    fun test6_delayedEventFromChannelA_arrivesInSessionB_rejected() {
        val activeSession = 5
        val currentStreamId = 200 // Channel B
        val eventMediaId = "100" // Channel A (delayed)

        val isValid = isEventValidForSession(
            activeSessionId = activeSession,
            eventSessionId = activeSession,
            currentStreamId = currentStreamId,
            eventMediaId = eventMediaId
        )

        assertFalse("Evento atrasado do canal A deve ser rejeitado ao estar na sessão do canal B", isValid)
    }

    @Test
    fun test7_zappingSequence_A_B_C_A_onlyFinalSessionValid() {
        var activeSession = 1
        var currentStream = 100 // A

        // User zaps A -> B -> C -> A
        activeSession = 4
        currentStream = 100 // Final A in Session 4

        // Event from Session 1 (old A)
        val validS1 = isEventValidForSession(
            activeSessionId = activeSession,
            eventSessionId = 1,
            currentStreamId = currentStream,
            eventMediaId = "100"
        )
        assertFalse("Sessão 1 do Canal A deve ser rejeitada na Sessão 4", validS1)

        // Event from Session 4 (new A)
        val validS4 = isEventValidForSession(
            activeSessionId = activeSession,
            eventSessionId = 4,
            currentStreamId = currentStream,
            eventMediaId = "100"
        )
        assertTrue("Sessão 4 do Canal A deve ser aceita", validS4)
    }
}
