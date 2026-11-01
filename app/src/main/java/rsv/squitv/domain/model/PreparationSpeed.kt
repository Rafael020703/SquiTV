package rsv.squitv.domain.model

import java.util.Locale

/**
 * Speed configuration for background catalog metadata preparation.
 * SLOW: min 5s per item, 30s batch cooldown.
 * NORMAL: min 2s per item, 20s batch cooldown.
 * FAST: min 1s per item, 10s batch cooldown.
 */
enum class PreparationSpeed(val itemDelayMs: Long, val batchCooldownMs: Long) {
    SLOW(5000L, 30_000L),
    NORMAL(2000L, 20_000L),
    FAST(1000L, 10_000L);

    companion object {
        fun fromString(value: String?): PreparationSpeed = when (value?.trim()?.uppercase(Locale.ROOT)) {
            "SLOW" -> SLOW
            "FAST" -> FAST
            else -> NORMAL
        }
    }
}
