package rsv.squitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rsv.squitv.data.model.XtreamStream

class ZappingSourceListTest {

    private class ZappingController(
        val categoryId: String?,
        val streams: List<XtreamStream>
    ) {
        var currentStreamId: Int? = null

        fun playNextChannel(): XtreamStream? {
            if (streams.isEmpty()) return null
            val curId = currentStreamId
            val currentIndex = if (curId != null) streams.indexOfFirst { it.streamId == curId } else -1
            val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % streams.size
            val target = streams[nextIndex]
            currentStreamId = target.streamId
            return target
        }

        fun playPreviousChannel(): XtreamStream? {
            if (streams.isEmpty()) return null
            val curId = currentStreamId
            val currentIndex = if (curId != null) streams.indexOfFirst { it.streamId == curId } else -1
            val prevIndex = if (currentIndex <= 0) streams.size - 1 else currentIndex - 1
            val target = streams[prevIndex]
            currentStreamId = target.streamId
            return target
        }
    }

    @Test
    fun zapping_normalCategory_preservesCategoryAndWrapsBothWays() {
        val catStreams = listOf(
            XtreamStream(streamId = 1, name = "C1", categoryId = "15"),
            XtreamStream(streamId = 2, name = "C2", categoryId = "15"),
            XtreamStream(streamId = 3, name = "C3", categoryId = "15")
        )
        val controller = ZappingController("15", catStreams)
        controller.currentStreamId = 1

        // Next: 1 -> 2
        var next = controller.playNextChannel()
        assertEquals(2, next?.streamId)
        assertEquals("15", controller.categoryId)

        // Next: 2 -> 3
        next = controller.playNextChannel()
        assertEquals(3, next?.streamId)

        // Next: 3 -> 1 (Wrap around to first)
        next = controller.playNextChannel()
        assertEquals(1, next?.streamId)

        // Previous: 1 -> 3 (Wrap around to last)
        val prev = controller.playPreviousChannel()
        assertEquals(3, prev?.streamId)
    }

    @Test
    fun zapping_favoritesCategory_staysInFavoritesList() {
        val favStreams = listOf(
            XtreamStream(streamId = 10, name = "Fav Globo", categoryId = "1"),
            XtreamStream(streamId = 30, name = "Fav SBT", categoryId = "2")
        )
        val controller = ZappingController("FAVORITES", favStreams)
        controller.currentStreamId = 10

        val next = controller.playNextChannel()
        assertEquals(30, next?.streamId)
        assertEquals("FAVORITES", controller.categoryId)

        val wrapNext = controller.playNextChannel()
        assertEquals(10, wrapNext?.streamId)
    }

    @Test
    fun zapping_emptySourceList_returnsNullWithoutFallbackToOtherCategories() {
        val controller = ZappingController("EMPTY_CAT", emptyList())
        controller.currentStreamId = 100

        val next = controller.playNextChannel()
        val prev = controller.playPreviousChannel()

        assertNull(next)
        assertNull(prev)
        assertEquals("EMPTY_CAT", controller.categoryId)
    }

    @Test
    fun zapping_singleChannel_staysOnSameChannel() {
        val singleStream = listOf(
            XtreamStream(streamId = 50, name = "Single Channel", categoryId = "5")
        )
        val controller = ZappingController("5", singleStream)
        controller.currentStreamId = 50

        val next = controller.playNextChannel()
        assertEquals(50, next?.streamId)

        val prev = controller.playPreviousChannel()
        assertEquals(50, prev?.streamId)
    }

    @Test
    fun zapping_channelNotFoundInSourceList_safelyPicksFirstOrLast() {
        val catStreams = listOf(
            XtreamStream(streamId = 100, name = "C100"),
            XtreamStream(streamId = 200, name = "C200")
        )
        val controller = ZappingController("10", catStreams)
        controller.currentStreamId = 999 // Not in list

        val next = controller.playNextChannel()
        assertEquals(100, next?.streamId) // Picks first item

        controller.currentStreamId = 999
        val prev = controller.playPreviousChannel()
        assertEquals(200, prev?.streamId) // Picks last item
    }
}
