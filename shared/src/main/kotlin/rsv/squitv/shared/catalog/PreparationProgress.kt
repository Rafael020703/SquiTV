package rsv.squitv.shared.catalog

import rsv.squitv.domain.model.PreparationSpeed
import kotlin.math.ceil

/** Session-local measurements; paused time and previously cached items are never samples. */
class PreparationProgress {
    private data class Sample(val resolved: Int, val milliseconds: Long)
    private val samples = ArrayDeque<Sample>()
    fun record(resolved: Int, elapsedMs: Long, intervalMs: Long) {
        samples.addLast(Sample(resolved.coerceAtLeast(0), elapsedMs.coerceAtLeast(1) + intervalMs.coerceAtLeast(0)))
        while (samples.size > 20) samples.removeFirst()
    }
    fun remainingMs(total: Int, done: Int, failures: Int): Long? {
        if (total <= 0) return null
        if (done >= total) return 0
        val resolved = samples.sumOf { it.resolved }
        if (failures > 0 || samples.size < 2 || resolved < 5) return null
        return ceil(samples.sumOf { it.milliseconds }.toDouble() / resolved * (total - done)).toLong()
    }
    companion object {
        fun percent(total: Int, done: Int): Int = if (total <= 0) 0 else
            (done.coerceIn(0, total).toLong() * 100 / total).toInt()
        fun duration(milliseconds: Long): String {
            val minutes = ceil(milliseconds.coerceAtLeast(0) / 60_000.0).toLong()
            return when {
                milliseconds < 60_000 -> "menos de 1 min"
                minutes < 60 -> "$minutes min"
                minutes < 1440 -> "${minutes / 60} h ${minutes % 60} min"
                else -> "${minutes / 1440} d ${(minutes % 1440) / 60} h"
            }
        }
    }
}

/** Failures reduce pressure on the provider; three successful waves cautiously restore speed. */
class PreparationBatchPolicy {
    private var degraded = false
    private var failures = 0
    private var successes = 0
    fun size(speed: PreparationSpeed): Int = if (degraded) 1 else speed.concurrentItems
    fun complete(speed: PreparationSpeed, failed: Boolean): Long {
        if (failed) {
            degraded = true; successes = 0; failures = (failures + 1).coerceAtMost(4)
        } else if (degraded && ++successes >= 3) {
            degraded = false; failures = 0; successes = 0
        }
        return if (degraded) maxOf(speed.batchCooldownMs, 5000L * (1L shl failures)) else speed.batchCooldownMs
    }
}
