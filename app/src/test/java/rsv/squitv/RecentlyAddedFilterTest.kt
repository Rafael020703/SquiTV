package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.data.api.XtreamService
import rsv.squitv.data.local.dao.IptvDao
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.data.network.EpgService
import rsv.squitv.data.local.entities.IptvStreamEntity
import rsv.squitv.domain.model.ContentType
import java.lang.reflect.Proxy

class RecentlyAddedFilterTest {

    private val dummyXtreamService = createProxy<XtreamService>()
    private val dummyIptvDao = createProxy<IptvDao>()
    private val dummyEpgService = createProxy<EpgService>()
    private val dummyEpgRepo = EpgRepository(dummyXtreamService, dummyEpgService, dummyIptvDao)

    private val repo = CatalogRepository(
        xtreamService = dummyXtreamService,
        iptvDao = dummyIptvDao,
        epgRepository = dummyEpgRepo
    )

    @Test
    fun testParseAddedTimestamp_formatsAndEpoch() {
        // Numeric string in seconds
        assertEquals(1700000000000L, repo.parseAddedTimestamp("1700000000"))

        // Numeric string in millis
        assertEquals(1700000000000L, repo.parseAddedTimestamp("1700000000000"))

        // Date format yyyy-MM-dd HH:mm:ss
        val parsedDateTime = repo.parseAddedTimestamp("2024-01-15 10:30:00")
        assertTrue(parsedDateTime > 0L)

        // Date format yyyy-MM-dd
        val parsedDate = repo.parseAddedTimestamp("2024-01-15")
        assertTrue(parsedDate > 0L)

        // Blank or null
        assertEquals(0L, repo.parseAddedTimestamp(""))
        assertEquals(0L, repo.parseAddedTimestamp(null))
        assertEquals(0L, repo.parseAddedTimestamp("invalid_date"))
    }

    @Test
    fun testSortAndFilterRecentStreams_14DaysLimit() {
        val referenceTime = 1700000000000L // Reference current time
        val oneDayMs = 24 * 3600 * 1000L

        val itemRecent1Day = IptvStreamEntity(
            id = 1,
            name = "Filme Recente 1 Dia",
            categoryId = "1",
            streamType = "VOD",
            url = null,
            logo = "logo1.png",
            added = ((referenceTime - (1 * oneDayMs)) / 1000).toString() // 1 day ago
        )

        val itemRecent13Days = IptvStreamEntity(
            id = 2,
            name = "Filme Recente 13 Dias",
            categoryId = "1",
            streamType = "VOD",
            url = null,
            logo = "logo2.png",
            added = ((referenceTime - (13 * oneDayMs)) / 1000).toString() // 13 days ago
        )

        val itemOld15Days = IptvStreamEntity(
            id = 3,
            name = "Filme Antigo 15 Dias",
            categoryId = "1",
            streamType = "VOD",
            url = null,
            logo = "logo3.png",
            added = ((referenceTime - (15 * oneDayMs)) / 1000).toString() // 15 days ago
        )

        val itemOld60Days = IptvStreamEntity(
            id = 4,
            name = "Filme Antigo 60 Dias",
            categoryId = "1",
            streamType = "VOD",
            url = null,
            logo = "logo4.png",
            added = ((referenceTime - (60 * oneDayMs)) / 1000).toString() // 60 days ago
        )

        val itemNoDate = IptvStreamEntity(
            id = 5,
            name = "Filme Sem Data",
            categoryId = "1",
            streamType = "VOD",
            url = null,
            logo = "logo5.png",
            added = null
        )

        val itemInvalidFutureDate = IptvStreamEntity(
            id = 6,
            name = "Filme Data Futura Invalida",
            categoryId = "1",
            streamType = "VOD",
            url = null,
            logo = "logo6.png",
            added = ((referenceTime + (30 * oneDayMs)) / 1000).toString() // 30 days in future
        )

        val allStreams = listOf(
            itemRecent1Day,
            itemRecent13Days,
            itemOld15Days,
            itemOld60Days,
            itemNoDate,
            itemInvalidFutureDate
        )

        val recentItems = repo.sortAndFilterRecentStreams(allStreams, limit = 10, currentTimeMillis = referenceTime)

        // Only 1 day and 13 days items should be included!
        assertEquals(2, recentItems.size)
        assertEquals("1", recentItems[0].id) // 1 day ago (most recent first)
        assertEquals("2", recentItems[1].id) // 13 days ago

        // Verify content type is MOVIE
        assertEquals(ContentType.MOVIE, recentItems[0].type)
        assertEquals(ContentType.MOVIE, recentItems[1].type)
    }

    @Test
    fun testResyncPreservesOriginalDates_noArtificialNewItems() {
        val referenceTime = 1700000000000L
        val oneDayMs = 24 * 3600 * 1000L

        // Old movie added 30 days ago
        val oldStream = IptvStreamEntity(
            id = 101,
            name = "Filme Antigo Salvo No Banco",
            categoryId = "1",
            streamType = "VOD",
            url = null,
            logo = "logo.png",
            added = ((referenceTime - (30 * oneDayMs)) / 1000).toString()
        )

        val resultBeforeSync = repo.sortAndFilterRecentStreams(listOf(oldStream), limit = 10, currentTimeMillis = referenceTime)
        assertTrue("Item older than 14 days must not appear in recents", resultBeforeSync.isEmpty())

        // Simulating a re-sync: original 'added' timestamp remains unchanged
        val resyncedStream = oldStream.copy()
        val resultAfterSync = repo.sortAndFilterRecentStreams(listOf(resyncedStream), limit = 10, currentTimeMillis = referenceTime)
        assertTrue("Re-syncing must NOT turn old items into new items", resultAfterSync.isEmpty())
    }

    private inline fun <reified T> createProxy(): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, _, _ -> null } as T
    }
}
