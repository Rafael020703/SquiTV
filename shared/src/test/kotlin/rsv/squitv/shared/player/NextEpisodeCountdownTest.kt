package rsv.squitv.shared.player

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NextEpisodeCountdownTest {
    @Test fun cancelStopsTheActualChildAndRemovesItsAction() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var calls = 0
            val countdown = NextEpisodeCountdown(scope)
            countdown.start { calls++ }
            val child = scope.coroutineContext[Job]!!.children.single()
            assertEquals(5, countdown.seconds.value)
            countdown.cancel()
            assertFalse(child.isActive)
            assertNull(countdown.seconds.value)
            countdown.finish()
            assertEquals(0, calls)
        } finally { scope.cancel() }
    }
    @Test fun replacingAndFinishingConsumesOnlyTheNewestActionOnce() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var calls = 0
            val countdown = NextEpisodeCountdown(scope)
            countdown.start { calls += 100 }
            val old = scope.coroutineContext[Job]!!.children.single()
            countdown.start { calls++ }
            assertFalse(old.isActive)
            countdown.finish()
            countdown.finish()
            assertEquals(1, calls)
            assertNull(countdown.seconds.value)
        } finally { scope.cancel() }
    }
}
