package rsv.squitv.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rsv.squitv.shared.catalog.MultiViewPolicy
import rsv.squitv.shared.xtream.LiveChannel
import rsv.squitv.shared.xtream.ServerCredentials

internal data class DesktopMultiViewState(val slots: List<LiveChannel?> = List(MultiViewPolicy.SLOT_COUNT) { null },
    val focused: Int = 0)

internal class DesktopMultiViewController(private val index: DesktopLiveIndex) {
    private val mutable = MutableStateFlow(DesktopMultiViewState())
    val state = mutable.asStateFlow()
    private var account: ServerCredentials? = null
    private var active = false
    fun open(credentials: ServerCredentials, force: Boolean = false) {
        if (account != credentials) { leave(); account = credentials }
        active = true
        index.load(credentials, force)
    }
    fun focus(slot: Int) { if (slot in 0 until MultiViewPolicy.SLOT_COUNT) mutable.update { it.copy(focused = slot) } }
    fun select(slot: Int, channel: LiveChannel) {
        if (slot !in 0 until MultiViewPolicy.SLOT_COUNT || index.state.value.accountScope != liveCategoryScope(account)) return
        if (index.state.value.channels.none { it.id == channel.id }) return
        mutable.update { it.copy(slots = it.slots.mapIndexed { i, old -> if (i == slot) channel else old }, focused = slot) }
    }
    fun remove(slot: Int) {
        if (slot in 0 until MultiViewPolicy.SLOT_COUNT)
            mutable.update { it.copy(slots = it.slots.mapIndexed { i, old -> if (i == slot) null else old }) }
    }
    fun leave() { if (active) index.cancel(); active = false; mutable.value = DesktopMultiViewState() }
    fun reset() { leave(); account = null }
}
