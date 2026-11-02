package rsv.squitv.shared.xtream

/** Navigation is strictly local to the immutable source list supplied by the caller. */
object LivePlayback {
    fun stepIndex(size: Int, current: Int, offset: Int): Int {
        require(size > 0)
        if (current < 0) return if (offset < 0) size - 1 else 0
        return Math.floorMod(current.toLong() + offset, size.toLong()).toInt()
    }
}

data class LiveProgramme(val title: String, val description: String?, val start: Long?, val end: Long?)
