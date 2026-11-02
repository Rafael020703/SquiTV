package rsv.squitv.ui.content.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Production controller managing D-pad seeking, coalescing, visual targets, and cancellation.
 */
class PlayerSeekController(
    val durationMs: Long,
    val onSeek: (Long) -> Unit,
    val onInteraction: () -> Unit = {},
    val onSeekingStateChanged: (Boolean) -> Unit = {}
) {
    var seekTarget: Long? = null
        private set
    var pendingConfirmedSeek: Long? = null
        private set
    var isSeekingActive: Boolean = false
        private set

    private var seekJob: Job? = null
    private var seekStartPosition: Long = 0L
    private var pendingForward: Boolean = true

    fun handleDpadSeek(currentPos: Long, deltaMs: Long, scope: CoroutineScope, debounceMs: Long = 300L) {
        if (durationMs <= 0L) return
        onInteraction()
        isSeekingActive = true
        onSeekingStateChanged(true)

        if (seekTarget == null) seekStartPosition = currentPos
        val current = seekTarget ?: pendingConfirmedSeek ?: currentPos
        val newTarget = (current + deltaMs).coerceIn(0L, durationMs)
        seekTarget = newTarget

        seekJob?.cancel()
        seekJob = scope.launch {
            delay(debounceMs)
            commitSeek()
        }
    }

    fun commitSeek() {
        val target = seekTarget
        seekJob?.cancel()
        seekJob = null
        if (target != null) {
            seekTarget = null
            pendingConfirmedSeek = target
            pendingForward = target >= seekStartPosition
            onSeek(target)
        }
        isSeekingActive = false
        onSeekingStateChanged(false)
        onInteraction()
    }

    fun cancelSeek() {
        seekJob?.cancel()
        seekJob = null
        seekTarget = null
        pendingConfirmedSeek = null
        isSeekingActive = false
        onSeekingStateChanged(false)
        onInteraction()
    }

    fun onPositionUpdate(newPos: Long) {
        pendingConfirmedSeek?.let { target ->
            if (kotlin.math.abs(newPos - target) <= 1500L ||
                (if (pendingForward) newPos >= target else newPos <= target)) {
                pendingConfirmedSeek = null
            }
        }
    }
}
