package rsv.squitv.shared.player

/** One press changes only the app's gain, without amplifying above the original signal. */
object PlayerVolume {
    const val STEP = 5
    fun adjust(percent: Int, direction: Int): Int =
        (percent.coerceIn(0, 100) + direction.coerceIn(-1, 1) * STEP).coerceIn(0, 100)
}
