package rsv.squitv.desktop

/** Reveal hidden controls only during sustained mouse motion, never after a stationary delay. */
internal class PlayerPointerMotionGate(
    private val minimumMovementMs: Long = 500,
    private val maximumGapMs: Long = 250
) {
    private var lastX: Int? = null
    private var lastY: Int? = null
    private var startedAt: Long? = null
    private var lastMovedAt: Long? = null
    private var previousVisibility: Boolean? = null

    fun sample(x: Int, y: Int, nowMs: Long, controlsVisible: Boolean, revealEnabled: Boolean): Boolean {
        if (!revealEnabled) { reset(); return false }
        if (previousVisibility != controlsVisible) {
            reset()
            previousVisibility = controlsVisible
        }
        val hasPreviousPoint = lastX != null
        val moved = hasPreviousPoint && (x != lastX || y != lastY)
        lastX = x
        lastY = y
        if (!moved) return false
        if (controlsVisible) {
            startedAt = null
            lastMovedAt = null
            return true
        }
        val previous = lastMovedAt
        if (previous == null || nowMs < previous || nowMs - previous > maximumGapMs) startedAt = nowMs
        lastMovedAt = nowMs
        return nowMs - (startedAt ?: nowMs) >= minimumMovementMs
    }

    fun reset() {
        lastX = null
        lastY = null
        startedAt = null
        lastMovedAt = null
        previousVisibility = null
    }
}
