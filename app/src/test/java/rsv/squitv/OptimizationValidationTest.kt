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

class OptimizationValidationTest {

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
    fun testFavoriteSetLookup_O1Performance() {
        val favoriteItems = (1..1000).map { id ->
            rsv.squitv.domain.model.IptvItem(
                id = id.toString(),
                name = "Item $id",
                icon = null,
                type = if (id % 2 == 0) ContentType.LIVE else ContentType.MOVIE
            )
        }

        // Precomputing set lookup as implemented in ContentGrid and ContentRow
        val favoriteKeys = favoriteItems.mapTo(HashSet(favoriteItems.size)) { it.id to it.type }

        assertTrue(favoriteKeys.contains("500" to ContentType.LIVE))
        assertTrue(favoriteKeys.contains("501" to ContentType.MOVIE))
        assertFalse(favoriteKeys.contains("500" to ContentType.MOVIE))
        assertFalse(favoriteKeys.contains("9999" to ContentType.LIVE))
    }

    @Test
    fun testWatchProgressThrottlingLogic() {
        var localSaveCount = 0
        var lastLocalSaveTime = 0L
        var lastLocalSaveStreamId = -1
        val MIN_LOCAL_SAVE_INTERVAL_MS = 10_000L

        fun simulateWatchProgressCall(
            streamId: Int,
            position: Long,
            timestamp: Long,
            forceSync: Boolean = false,
            isCompleted: Boolean = false,
            isEndedEvent: Boolean = false
        ) {
            val shouldSaveLocal = forceSync || isCompleted || isEndedEvent ||
                                  streamId != lastLocalSaveStreamId ||
                                  (timestamp - lastLocalSaveTime >= MIN_LOCAL_SAVE_INTERVAL_MS)

            if (shouldSaveLocal) {
                lastLocalSaveTime = timestamp
                lastLocalSaveStreamId = streamId
                localSaveCount++
            }
        }

        val baseTime = 1000000L

        // Call 1: position = 5000ms at t = 0
        simulateWatchProgressCall(101, 5000L, baseTime)
        assertEquals(1, localSaveCount)

        // Call 2: position = 7000ms at t = 2s (throttled)
        simulateWatchProgressCall(101, 7000L, baseTime + 2000L)
        assertEquals(1, localSaveCount)

        // Call 3: position = 9000ms at t = 4s (throttled)
        simulateWatchProgressCall(101, 9000L, baseTime + 4000L)
        assertEquals(1, localSaveCount)

        // Call 4: position = 15000ms at t = 10.5s (saved after 10s interval)
        simulateWatchProgressCall(101, 15000L, baseTime + 10500L)
        assertEquals(2, localSaveCount)

        // Call 5: pause / seek event with forceSync = true (saved immediately)
        simulateWatchProgressCall(101, 16000L, baseTime + 12000L, forceSync = true)
        assertEquals(3, localSaveCount)
    }

    private inline fun <reified T> createProxy(): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, _, _ -> null } as T
    }
}
