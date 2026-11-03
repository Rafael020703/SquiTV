package rsv.squitv.shared.catalog

/** Android Multi-view: four independent slots, and audio only in the focused slot. */
object MultiViewPolicy {
    const val SLOT_COUNT = 4
    const val COLUMNS = 2
    fun audioVolume(slot: Int, focused: Int): Float = if (slot == focused) 1f else 0f
}
