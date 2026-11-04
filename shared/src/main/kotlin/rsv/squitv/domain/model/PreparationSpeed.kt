package rsv.squitv.domain.model

import java.util.Locale

/**
 * Speed configuration for background catalog metadata preparation.
 * Bounded waves, with one interval between waves (no additional per-item sleep).
 */
enum class PreparationSpeed(val itemDelayMs: Long, val batchCooldownMs: Long, val concurrentItems: Int) {
    SLOW(5000L, 5000L, 1),
    NORMAL(2000L, 2000L, 2),
    FAST(1000L, 1000L, 5);

    companion object {
        fun fromString(value: String?): PreparationSpeed = when (value?.trim()?.uppercase(Locale.ROOT)) {
            "SLOW" -> SLOW
            "FAST" -> FAST
            else -> NORMAL
        }
    }
}
