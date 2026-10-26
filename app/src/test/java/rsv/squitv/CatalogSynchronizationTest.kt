package rsv.squitv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.data.api.XtreamService
import rsv.squitv.data.local.AppDatabase
import rsv.squitv.data.local.entities.*
import rsv.squitv.data.model.*
import rsv.squitv.data.network.EpgService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CatalogSynchronizationTest {
    private lateinit var db: AppDatabase
    private val api = mock(XtreamService::class.java)
    private val credentials = XtreamCredentials("test", "test", "https://example.invalid")
    private val dao get() = db.iptvDao()
    private val repository get() = CatalogRepository(api, dao, EpgRepository(api, mock(EpgService::class.java), dao))

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun stream(id: Int, type: String = "LIVE", name: String = "Original") =
        IptvStreamEntity(id, name, "1", type, null, null)

    @Test fun publicationPreservesLocalPreferencesAndDetailsAndRemovesOnlyMissingType() = runBlocking {
        dao.insertCategories(listOf(IptvCategoryEntity("1", "Old", "VOD", isLocked = true, isPinned = true)))
        dao.insertStreams(listOf(stream(1, "VOD").copy(plot = "Synopsis", cast = "Cast", backdrop = "image", isFavorite = true),
            stream(2, "VOD"), stream(2, "LIVE")))
        dao.publishCatalogSnapshot("VOD", listOf(IptvCategoryEntity("1", "Updated", "VOD")),
            listOf(stream(1, "VOD", "New name")))
        val category = dao.getCategoriesByType("VOD").single()
        assertTrue(category.isLocked)
        assertTrue(category.isPinned)
        assertEquals("Updated", category.name)
        val movie = dao.getStreamsByType("VOD").single()
        assertEquals("New name", movie.name)
        assertEquals("Synopsis", movie.plot)
        assertEquals("Cast", movie.cast)
        assertEquals("image", movie.backdrop)
        assertTrue(movie.isFavorite)
        assertEquals(2, dao.getStreamsByType("LIVE").single().id)
    }

    @Test fun failedDownloadDoesNotPublishCategoriesOrDeleteExistingStreams() = runBlocking {
        dao.insertCategories(listOf(IptvCategoryEntity("1", "Original", "LIVE", isLocked = true)))
        dao.insertStreams(listOf(stream(1)))
        doReturn(listOf(XtreamCategory("1", "Changed remotely"))).`when`(api).getLiveCategories("test", "test")
        doAnswer { throw IOException("offline") }.`when`(api).getLiveStreams("test", "test")
        try {
            repository.syncCatalogType(credentials, "LIVE")
            fail("Expected download failure")
        } catch (_: IOException) { }
        assertEquals("Original", dao.getCategoriesByType("LIVE").single().name)
        assertTrue(dao.getCategoriesByType("LIVE").single().isLocked)
        assertEquals(1, dao.getStreamsByType("LIVE").single().id)
    }

    @Test fun invalidRemoteRecordDoesNotPublishAPartialSnapshot() = runBlocking {
        dao.insertStreams(listOf(stream(1)))
        doReturn(listOf(XtreamCategory("1", "Category"))).`when`(api).getLiveCategories("test", "test")
        doReturn(listOf(XtreamStream(name = "Missing ID"))).`when`(api).getLiveStreams("test", "test")
        try {
            repository.syncCatalogType(credentials, "LIVE")
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, dao.getStreamsByType("LIVE").single().id)
    }

    @Test fun validatedEmptySnapshotRemovesOnlyItsCatalogTypeAndKeepsFavorites() = runBlocking {
        dao.insertStreams(listOf(stream(1), stream(1, "VOD")))
        dao.insertFavorite(FavoriteEntity(streamId = 1, streamType = "LIVE", name = "Saved"))
        doReturn(emptyList<XtreamCategory>()).`when`(api).getLiveCategories("test", "test")
        doReturn(emptyList<XtreamStream>()).`when`(api).getLiveStreams("test", "test")
        assertEquals(0, repository.syncCatalogType(credentials, "LIVE"))
        assertTrue(dao.getStreamsByType("LIVE").isEmpty())
        assertEquals(1, dao.getStreamsByType("VOD").size)
        assertTrue(dao.isFavorite(1, "LIVE"))
    }

    @Test fun databaseFailureRollsBackCategoryAndStreamChangesTogether() = runBlocking {
        dao.insertCategories(listOf(IptvCategoryEntity("1", "Original", "LIVE")))
        dao.insertStreams(listOf(stream(1)))
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_update BEFORE UPDATE ON iptv_streams
            BEGIN SELECT RAISE(ABORT, 'simulated disk failure'); END
        """.trimIndent())
        try {
            dao.publishCatalogSnapshot("LIVE", listOf(IptvCategoryEntity("1", "Changed", "LIVE")),
                listOf(stream(1, name = "Changed")))
            fail("Expected transaction failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals("Original", dao.getCategoriesByType("LIVE").single().name)
        assertEquals("Original", dao.getStreamsByType("LIVE").single().name)
    }

    @Test fun updatingSeriesDoesNotCascadeDeleteItsSeasons() = runBlocking {
        dao.insertStreams(listOf(stream(1, "SERIES"), stream(2, "SERIES")))
        dao.insertSeasons(listOf(SeasonEntity(1, 1, "Season", null), SeasonEntity(2, 1, "Removed", null)))
        dao.publishCatalogSnapshot("SERIES", emptyList(), listOf(stream(1, "SERIES", "Updated")))
        assertEquals("Season", dao.getSeasons(1).single().name)
        assertTrue(dao.getSeasons(2).isEmpty())
    }

    @Test fun accountChangeBeforePublicationPreservesThePreviousCatalog() = runBlocking {
        dao.insertStreams(listOf(stream(1)))
        doReturn(listOf(XtreamCategory("1", "New"))).`when`(api).getLiveCategories("test", "test")
        doReturn(listOf(XtreamStream(streamId = 2, name = "New"))).`when`(api).getLiveStreams("test", "test")
        try {
            repository.syncCatalogType(credentials, "LIVE") { throw kotlinx.coroutines.CancellationException("Account changed") }
            fail("Expected cancellation")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(1, dao.getStreamsByType("LIVE").single().id)
    }
}
