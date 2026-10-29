package rsv.squitv.ui.content.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** Small navigation state only; the catalogue itself never goes into the saved-state Bundle. */
@Stable
class ContentGridState(
    val scroll: LazyGridState = LazyGridState(),
    focusedItem: String = "",
    hasInteracted: Boolean = false,
    focusedIndex: Int = 0
) {
    var focusedItem by mutableStateOf(focusedItem)
    var hasInteracted by mutableStateOf(hasInteracted)
    var focusedIndex by mutableIntStateOf(focusedIndex)

    companion object {
        val Saver = listSaver<ContentGridState, Any>(
            save = {
                listOf(it.scroll.firstVisibleItemIndex, it.scroll.firstVisibleItemScrollOffset,
                    it.focusedItem, it.hasInteracted, it.focusedIndex)
            },
            restore = {
                ContentGridState(LazyGridState(it[0] as Int, it[1] as Int), it[2] as String, it[3] as Boolean, it[4] as Int)
            }
        )
    }
}

@Composable
fun rememberContentGridState(scopeKey: String): ContentGridState =
    rememberSaveable(scopeKey, saver = ContentGridState.Saver) { ContentGridState() }
