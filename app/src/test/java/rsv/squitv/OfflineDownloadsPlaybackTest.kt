package rsv.squitv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.data.local.AppDatabase
import rsv.squitv.data.local.entities.DownloadEntity
import rsv.squitv.core.data.repository.StreamRepository
import rsv.squitv.data.model.XtreamCredentials
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class OfflineDownloadsPlaybackTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private var database: AppDatabase? = null
    private val streamRepository = StreamRepository()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-downloads-${UUID.randomUUID().toString().take(8)}.db"
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun testDownloadEntityCompoundKeyPreventsCollision() = runBlocking {
        val dao = database!!.iptvDao()
        val movieDownload = DownloadEntity(
            streamId = 101,
            type = "movie",
            name = "Filme Teste",
            icon = null,
            localUri = "http://example.com/movie/101.mp4",
            status = "COMPLETED",
            progress = 100f,
            size = 1000000L,
            downloadedBytes = 1000000L
        )
        val seriesDownload = DownloadEntity(
            streamId = 101,
            type = "series",
            name = "Episódio Teste",
            icon = null,
            localUri = "http://example.com/series/101.mp4",
            status = "DOWNLOADING",
            progress = 50f,
            size = 2000000L,
            downloadedBytes = 1000000L,
            seriesId = 55,
            seasonNumber = 1
        )

        dao.insertDownload(movieDownload)
        dao.insertDownload(seriesDownload)

        val fetchedMovie = dao.getDownload(101, "movie")
        val fetchedSeries = dao.getDownload(101, "series")

        assertNotNull(fetchedMovie)
        assertNotNull(fetchedSeries)
        assertEquals("Filme Teste", fetchedMovie?.name)
        assertEquals("Episódio Teste", fetchedSeries?.name)
        assertEquals("COMPLETED", fetchedMovie?.status)
        assertEquals("DOWNLOADING", fetchedSeries?.status)
    }

    @Test
    fun testStreamUrlGeneratorEncodesSpecialCharactersAndNormalizesExtension() {
        val creds = XtreamCredentials("user@test", "pass#123", "http://iptv.server:8080/")
        val movieUrl = streamRepository.getStreamUrl(creds, 500, "movie", "mkv")
        val seriesUrl = streamRepository.getStreamUrl(creds, 500, "series", ".mp4")

        assertTrue(movieUrl.contains("http://iptv.server:8080/movie/user%40test/pass%23123/500.mkv"))
        assertTrue(seriesUrl.contains("http://iptv.server:8080/series/user%40test/pass%23123/500.mp4"))
    }
}
