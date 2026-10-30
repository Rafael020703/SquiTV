package rsv.squitv

import android.app.Application
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
import rsv.squitv.data.local.entities.IptvStreamEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class FtsReleaseTest {
    private lateinit var db: AppDatabase
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.iptvDao().insertStreams(listOf(
            IptvStreamEntity(1, "Canal Teste", "a", "LIVE", null, null),
            IptvStreamEntity(2, "Canal Teste", "b", "LIVE", null, null),
            IptvStreamEntity(1, "Canal Teste", "c", "VOD", null, null)))
    }
    @After fun cleanup() { db.close() }
    @Test fun sameNames() = runBlocking {
        val result = db.iptvDao().searchStreamsFtsFiltered("Canal", "LIVE", listOf("none"))
        assertEquals(2, result.size)
        assertEquals(setOf(1, 2), result.map { it.id }.toSet())
        assertTrue(result.all { it.streamType == "LIVE" })
    }
    @Test fun blocked() = runBlocking {
        val result = db.iptvDao().searchStreamsFtsFiltered("Canal", "LIVE", listOf("a"))
        assertEquals(1, result.size)
        assertEquals(2, result.single().id)
    }
    @Test fun pagedNames() = runBlocking {
        val source = db.iptvDao().searchStreamsFtsPaged("Canal", "LIVE")
        val result = source.load(androidx.paging.PagingSource.LoadParams.Refresh<Int>(null, 20, false))
        assertTrue(result is androidx.paging.PagingSource.LoadResult.Page)
        val rows = (result as androidx.paging.PagingSource.LoadResult.Page).data
        assertEquals(2, rows.size)
        assertEquals(setOf(1, 2), rows.map { it.id }.toSet())
    }
}
