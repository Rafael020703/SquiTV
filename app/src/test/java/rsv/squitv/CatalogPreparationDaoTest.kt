package rsv.squitv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.data.local.AppDatabase
import rsv.squitv.data.local.entities.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class CatalogPreparationDaoTest {
    private fun isolated(action: suspend (AppDatabase) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try { action(db) } finally { db.close() }
    }
    private fun stream(id: Int, type: String = "VOD") = IptvStreamEntity(id, "Title $id", "category", type, null, null)
    private fun row(id: Int, status: String, account: String = "fixture-account", due: Long = 0, attempts: Int = 0) =
        CatalogMetadataEntity(account, "VOD", id, status, nextEligibleAt = due, attempts = attempts)

    @Test fun legacyJoinDoesNotDuplicateTheSamePreparationCandidate() = isolated { db ->
        val dao = db.iptvDao()
        dao.insertStreams(listOf(stream(1), stream(1, "MOVIE"), stream(2)))
        dao.insertOrUpdateMetadataCacheList(listOf(row(1, "PENDING"), row(2, "PENDING")))
        val candidates = dao.getPendingMetadataItems("fixture-account", "VOD", 1000, 50)
        assertEquals(listOf(1, 2), candidates.map { it.streamId })
        assertEquals(2, dao.getMetadataProgress("fixture-account", 1000).sumOf { it.itemCount })
    }
    @Test fun totalsExcludeOtherAccountsRemovedIdsAndExpiredNegativeCache() = isolated { db ->
        val dao = db.iptvDao()
        dao.insertStreams(listOf(stream(1), stream(2), stream(3)))
        dao.insertOrUpdateMetadataCacheList(listOf(row(1, "NO_DATA", due = 999), row(2, "NO_DATA", due = 2000),
            row(3, "SUCCESS"), row(999, "SUCCESS"), row(1, "SUCCESS", account = "other-fixture")))
        val counts = dao.getMetadataProgress("fixture-account", 1000).associate { it.status to it.itemCount }
        assertEquals(mapOf("PENDING" to 1, "NO_DATA" to 1, "SUCCESS" to 1), counts)
        assertEquals(listOf(1), dao.getPendingMetadataItems("fixture-account", "VOD", 1000, 50).map { it.streamId })
    }
    @Test fun manualRetryOnlyResetsFailuresAndBatchUpdatesDetailsAndCheckpointTogether() = isolated { db ->
        val dao = db.iptvDao()
        dao.insertStreams(listOf(stream(1), stream(2)))
        dao.insertOrUpdateMetadataCacheList(listOf(row(1, "FAILED_TEMPORARY", attempts = 5), row(2, "NO_DATA", due = 2000)))
        assertTrue(dao.getPendingMetadataItems("fixture-account", "VOD", 1000, 50).isEmpty())
        dao.retryFailedMetadata("fixture-account")
        assertEquals(listOf(1), dao.getPendingMetadataItems("fixture-account", "VOD", 1000, 50).map { it.streamId })
        val success = row(1, "SUCCESS").copy(genre = "Drama")
        dao.applyPreparationBatch(listOf(success), listOf(CatalogMetadataUpdate(1, "VOD", "Drama", null, null, "Plot", null, null, null)))
        assertEquals("Drama", dao.getStreamsByIds(listOf(1)).single().genre)
        val records = dao.getAllMetadataCacheForAccount("fixture-account", "VOD").associateBy { it.streamId }
        assertEquals("SUCCESS", records.getValue(1).status)
        assertEquals(2000L, records.getValue(2).nextEligibleAt)
    }
}
