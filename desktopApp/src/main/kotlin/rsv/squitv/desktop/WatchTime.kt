package rsv.squitv.desktop

internal fun watchTime(timeMs: Long): String {
    val total = timeMs.coerceAtLeast(0) / 1000
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
        else "%d:%02d".format(total / 60, total % 60)
}
