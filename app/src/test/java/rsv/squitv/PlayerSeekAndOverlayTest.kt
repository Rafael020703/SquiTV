package rsv.squitv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rsv.squitv.ui.content.player.PlayerOverlayController
import rsv.squitv.ui.content.player.PlayerSeekController

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSeekAndOverlayTest {

    @Test
    fun test1_rapidRightKeys_coalescesToAccumulatedTarget() = runTest {
        val seekCalls = mutableListOf<Long>()
        val controller = PlayerSeekController(durationMs = 120_000L, onSeek = { seekCalls.add(it) })

        val initialPos = 30_000L // 30s

        // 3 rapid taps to the right (+10s each)
        controller.handleDpadSeek(initialPos, 10_000L, this) // -> 40s
        testScheduler.advanceTimeBy(50L)
        controller.handleDpadSeek(initialPos, 10_000L, this) // -> 50s
        testScheduler.advanceTimeBy(50L)
        controller.handleDpadSeek(initialPos, 10_000L, this) // -> 60s

        assertEquals(60_000L, controller.seekTarget)
        assertEquals(0, seekCalls.size) // Not committed yet

        // Advance beyond the 300ms coalescing window
        testScheduler.advanceTimeBy(301L)

        assertEquals(1, seekCalls.size)
        assertEquals(60_000L, seekCalls[0])
        assertNull(controller.seekTarget)
        assertEquals(60_000L, controller.pendingConfirmedSeek)
    }

    @Test
    fun test2_seekLimits_coercedToDurationAndZero() = runTest {
        val seekCalls = mutableListOf<Long>()
        val controller = PlayerSeekController(durationMs = 120_000L, onSeek = { seekCalls.add(it) })

        // Exceed upper bound
        controller.handleDpadSeek(115_000L, 10_000L, this)
        testScheduler.advanceTimeBy(301L)
        assertEquals(120_000L, seekCalls.last())

        // Reset controller state for lower bound test
        controller.cancelSeek()
        seekCalls.clear()

        // Exceed lower bound
        controller.handleDpadSeek(5_000L, -10_000L, this)
        testScheduler.advanceTimeBy(301L)

        assertEquals(1, seekCalls.size)
        assertEquals(0L, seekCalls.last())
    }

    @Test
    fun test3_durationUnknownOrInvalid_seekDisabled() = runTest {
        val seekCalls = mutableListOf<Long>()
        val controller = PlayerSeekController(durationMs = -1L, onSeek = { seekCalls.add(it) })

        controller.handleDpadSeek(10_000L, 10_000L, this)
        testScheduler.advanceTimeBy(500L)

        assertTrue("No seek calls allowed when duration <= 0", seekCalls.isEmpty())
        assertNull(controller.seekTarget)
    }

    @Test
    fun test4_continuousInteractions_keepsControlsVisibleBeyond5s() = runTest {
        val overlay = PlayerOverlayController()
        overlay.showOverlayWithTimer(this)
        assertTrue(overlay.isControlsVisible)

        // Simulate interaction every 1 second for 6 seconds total
        for (i in 1..6) {
            testScheduler.advanceTimeBy(1000L)
            overlay.updateInteraction(this)
            assertTrue("Controls must remain visible at t=${i}s during continuous interaction", overlay.isControlsVisible)
        }

        // Now stop interacting and wait 5 seconds
        testScheduler.advanceTimeBy(4900L)
        assertTrue("Controls still visible before 5s inactivity window elapses", overlay.isControlsVisible)

        testScheduler.advanceTimeBy(200L)
        assertFalse("Controls auto-hide after 5s of inactivity", overlay.isControlsVisible)
    }

    @Test
    fun test5_seekingActive_preventsControlsAutohide() = runTest {
        val overlay = PlayerOverlayController()
        overlay.showOverlayWithTimer(this)

        // Start seeking
        overlay.onSeekingStateChanged(true, this)

        // Wait 10 seconds (exceeding 5s)
        testScheduler.advanceTimeBy(10_000L)
        assertTrue("Controls must NOT hide while seeking is active", overlay.isControlsVisible)

        // End seeking
        overlay.onSeekingStateChanged(false, this)
        assertTrue("Controls still visible immediately after seeking finishes", overlay.isControlsVisible)

        // Now 5s timer runs
        testScheduler.advanceTimeBy(5001L)
        assertFalse("Controls hide 5s after seeking completes", overlay.isControlsVisible)
    }

    @Test
    fun test6_cancelSeek_onBackKey_restoresPositionWithoutCommitting() = runTest {
        val seekCalls = mutableListOf<Long>()
        val controller = PlayerSeekController(durationMs = 120_000L, onSeek = { seekCalls.add(it) })

        controller.handleDpadSeek(30_000L, 10_000L, this)
        assertEquals(40_000L, controller.seekTarget)

        // User presses Back key while seeking
        controller.cancelSeek()

        assertNull(controller.seekTarget)
        assertNull(controller.pendingConfirmedSeek)
        assertFalse(controller.isSeekingActive)

        testScheduler.advanceTimeBy(500L)
        assertTrue("No seek calls emitted on cancel", seekCalls.isEmpty())
    }

    @Test
    fun test7_streamOrEpisodeChange_cancelsPendingSeek() = runTest {
        val seekCalls = mutableListOf<Long>()
        val controller = PlayerSeekController(durationMs = 120_000L, onSeek = { seekCalls.add(it) })

        controller.handleDpadSeek(30_000L, 10_000L, this)
        assertEquals(40_000L, controller.seekTarget)

        // Episode changed -> reset controller
        controller.cancelSeek()

        testScheduler.advanceTimeBy(300L)
        assertTrue("Cancelled seek on episode change must not fire", seekCalls.isEmpty())
    }
}
