package rsv.squitv

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.data.local.entities.IptvStreamEntity
import rsv.squitv.ui.viewmodel.SearchResult
import rsv.squitv.ui.viewmodel.SearchViewModel
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class SearchReleaseTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var repository: CatalogRepository
    @Before fun setup() { Dispatchers.setMain(dispatcher); repository = mock(CatalogRepository::class.java) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun entity(id: Int, name: String) = IptvStreamEntity(id, name, "cat", "VOD", null, null)
    private fun vm(query: String = "") = SearchViewModel(repository, SavedStateHandle(mapOf("query" to query))).also { store.put("vm", it) }
    private suspend fun answer(query: String, id: Int) {
        `when`(repository.searchStreams(query, "ALL", emptySet())).thenReturn(listOf(entity(id, query)))
        `when`(repository.searchEpisodes(query, emptySet())).thenReturn(emptyList())
    }
    @Test fun prefilled() = runTest {
        answer("initial", 1)
        val vm = vm("initial")
        runCurrent()
        assertEquals(1, (vm.searchResults.value.single() as SearchResult.Stream).stream.id)
        assertFalse(vm.isLoading.value)
    }
    @Test fun debounce() = runTest {
        answer("latest", 2)
        val vm = vm()
        vm.onQueryChanged("old")
        advanceTimeBy(200)
        vm.onQueryChanged("latest")
        advanceTimeBy(249)
        verifyNoInteractions(repository)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, (vm.searchResults.value.single() as SearchResult.Stream).stream.id)
        verify(repository).searchStreams("latest", "ALL", emptySet())
    }
    @Test fun clearPending() = runTest {
        val vm = vm()
        vm.onQueryChanged("old")
        advanceTimeBy(100)
        vm.onQueryChanged("")
        advanceUntilIdle()
        assertTrue(vm.searchResults.value.isEmpty())
        assertFalse(vm.isLoading.value)
        verifyNoInteractions(repository)
    }
    @Test fun staleResult() = runTest {
        var old: Continuation<List<IptvStreamEntity>>? = null
        `when`(repository.searchStreams("old", "ALL", emptySet())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val continuation = invocation.rawArguments.last() as Continuation<List<IptvStreamEntity>>
            old = continuation
            COROUTINE_SUSPENDED
        }
        answer("latest", 2)
        val vm = vm()
        vm.onQueryChanged("old"); advanceTimeBy(250); runCurrent()
        assertNotNull(old)
        vm.onQueryChanged("latest"); advanceTimeBy(250); runCurrent()
        old!!.resume(listOf(entity(1, "old"))); runCurrent()
        assertEquals(2, (vm.searchResults.value.single() as SearchResult.Stream).stream.id)
    }
}
