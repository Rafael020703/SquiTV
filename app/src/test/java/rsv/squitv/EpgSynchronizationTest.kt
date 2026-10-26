package rsv.squitv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.data.api.XtreamService
import rsv.squitv.data.local.AppDatabase
import rsv.squitv.data.local.entities.EpgProgramEntity
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.network.EpgService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class EpgSynchronizationTest {
    private lateinit var db: AppDatabase
    private val service = mock(EpgService::class.java)
    private val credentials = XtreamCredentials("test", "test", "https://example.invalid")
    private val url = "https://example.invalid/xmltv.php?username=test&password=test"
    private val now = System.currentTimeMillis() / 1000
    private val dao get() = db.iptvDao()
    private val repository get() = EpgRepository(mock(XtreamService::class.java), service, dao)

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao.insertEpgPrograms(listOf(EpgProgramEntity("old", now, now + 7200, "Existing", null)))
    }

    @After fun close() = db.close()

    @Test fun networkFailureIsPropagatedAndKeepsThePreviousGuide() = runBlocking {
        doAnswer { throw IOException("offline") }.`when`(service).getEpg(url)
        try { repository.syncFullEpg(credentials); fail("Expected network failure") } catch (_: IOException) { }
        assertEquals("Existing", dao.getEpgInRange(now, now + 7200).single().title)
    }

    @Test fun emptyGuideIsRejectedWithoutErasingPreviousProgramsAndClosesResponse() = runBlocking {
        val response = spy("<tv/>".toResponseBody("text/xml".toMediaType()))
        doReturn(response).`when`(service).getEpg(url)
        try { repository.syncFullEpg(credentials); fail("Expected empty guide failure") } catch (_: IllegalArgumentException) { }
        assertEquals("Existing", dao.getEpgInRange(now, now + 7200).single().title)
        verify(response).close()
    }

    private fun validXml(): String {
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        return """<tv><programme channel="new" start="${format.format(Date(now * 1000))}" stop="${format.format(Date((now + 3600) * 1000))}"><title>Updated</title></programme></tv>"""
    }

    @Test fun validatedGuideIsPublishedAndReturnsActualProgramCount() = runBlocking {
        doReturn(validXml().toResponseBody("text/xml".toMediaType())).`when`(service).getEpg(url)
        assertEquals(1, repository.syncFullEpg(credentials))
        assertEquals("Updated", dao.getEpgInRange(now, now + 7200).single().title)
        assertTrue(dao.getEpgForChannel("old", now).first().isEmpty())
    }

    @Test fun accountChangeBeforePublicationKeepsTheOldGuideAndPropagatesCancellation() = runBlocking {
        doReturn(validXml().toResponseBody("text/xml".toMediaType())).`when`(service).getEpg(url)
        try {
            repository.syncFullEpg(credentials) { throw CancellationException("Account changed") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals("Existing", dao.getEpgInRange(now, now + 7200).single().title)
    }
}
