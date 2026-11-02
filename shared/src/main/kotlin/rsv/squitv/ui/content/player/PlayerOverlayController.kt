package rsv.squitv.ui.content.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Production controller managing overlay visibility, interaction timers, and seeking freeze.
 */
class PlayerOverlayController(
    val hideTimeoutMs: Long = 5000L
) {
    var isControlsVisible: Boolean = false
        private set
    var isSeekingActive: Boolean = false
        private set

    private var timerJob: Job? = null

    fun showOverlayWithTimer(scope: CoroutineScope, onVisibilityChanged: (Boolean) -> Unit = {}) {
        isControlsVisible = true
        onVisibilityChanged(true)
        renewTimer(scope, onVisibilityChanged)
    }

    fun hideOverlayImmediately(onVisibilityChanged: (Boolean) -> Unit = {}) {
        timerJob?.cancel()
        timerJob = null
        isControlsVisible = false
        onVisibilityChanged(false)
    }

    fun updateInteraction(scope: CoroutineScope, onVisibilityChanged: (Boolean) -> Unit = {}) {
        if (isControlsVisible) {
            renewTimer(scope, onVisibilityChanged)
        }
    }

    fun onSeekingStateChanged(active: Boolean, scope: CoroutineScope, onVisibilityChanged: (Boolean) -> Unit = {}) {
        isSeekingActive = active
        if (active) {
            timerJob?.cancel()
            timerJob = null
        } else if (isControlsVisible) {
            renewTimer(scope, onVisibilityChanged)
        }
    }

    private fun renewTimer(scope: CoroutineScope, onVisibilityChanged: (Boolean) -> Unit) {
        timerJob?.cancel()
        if (isControlsVisible && !isSeekingActive) {
            timerJob = scope.launch {
                delay(hideTimeoutMs)
                isControlsVisible = false
                onVisibilityChanged(false)
            }
        }
    }
}
