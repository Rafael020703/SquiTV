package rsv.squitv

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.ui.content.components.ContentGridState

class ContentGridStateCompatibilityTest {
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any) = true
    }

    @Test fun realStreamKeyAndPositionSurviveSavedState() {
        val state = ContentGridState(LazyGridState(3, 12), "group", true, 4, "LIVE:42")
        val encoded = with(ContentGridState.Saver) { scope.save(state) }!!
        val restored = ContentGridState.Saver.restore(encoded)!!
        assertEquals("LIVE:42", restored.focusedStreamKey)
        assertEquals("group", restored.focusedItem)
        assertEquals(3, restored.scroll.firstVisibleItemIndex)
        assertEquals(12, restored.scroll.firstVisibleItemScrollOffset)
        assertEquals(4, restored.focusedIndex)
    }

    @Test fun previousFiveFieldSavedStateStillRestores() {
        val restored = ContentGridState.Saver.restore(listOf(3, 12, "LIVE:42", true, 4))!!
        assertEquals("", restored.focusedStreamKey)
        assertEquals("LIVE:42", restored.focusedItem)
        assertTrue(restored.hasInteracted)
    }
}
