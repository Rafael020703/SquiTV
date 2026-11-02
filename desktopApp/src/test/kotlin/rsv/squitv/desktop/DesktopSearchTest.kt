package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.desktop.data.XtreamClient
import rsv.squitv.shared.xtream.ServerCredentials

class DesktopSearchTest {
    private suspend fun DesktopSearchController.completed() = withTimeout(10_000) { state.first { !it.loading } }
    private val live = """[{"stream_id":1,"name":"Teste Café","category_id":"5"}]"""
    private val movie = """[{"stream_id":1,"name":"Teste Filme"}]"""
    private val series = """[{"series_id":1,"name":"Teste Série"}]"""

    @Test fun globalSearchQueriesAllTypesAndReusesListings() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            listOf(live, movie, series).forEach { server.enqueue(MockResponse().setBody(it)) }
            XtreamClient().use { client ->
                val scope = CoroutineScope(coroutineContext + SupervisorJob())
                try {
                    val search = DesktopSearchController(client, scope)
                    search.open(ServerCredentials(server.url("/").toString(), "fixture", "fixture"))
                    assertEquals(0, server.requestCount)
                    search.query("teste")
                    assertEquals(3, search.completed().results.size)
                    val requests = (1..3).map { server.takeRequest().requestUrl!! }
                    assertEquals(setOf("get_live_streams", "get_vod_streams", "get_series"), requests.map { it.queryParameter("action") }.toSet())
                    assertTrue(requests.all { it.queryParameter("category_id") == null })
                    search.query("CAFE")
                    assertEquals("Teste Café", search.completed().results.single().name)
                    assertEquals(3, server.requestCount)
                    search.reset()
                    assertTrue(search.state.value.results.isEmpty())
                } finally { scope.cancel() }
            }
        }
    }

    @Test fun failedTypeIsVisibleAndRetryDoesNotRefetchSuccessfulTypes() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody(live))
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setBody(series))
            server.enqueue(MockResponse().setBody(movie))
            XtreamClient().use { client ->
                val scope = CoroutineScope(coroutineContext + SupervisorJob())
                try {
                    val search = DesktopSearchController(client, scope)
                    search.open(ServerCredentials(server.url("/").toString(), "fixture", "fixture"))
                    search.query("teste")
                    val partial = search.completed()
                    assertEquals(2, partial.results.size)
                    assertEquals(setOf(DesktopSearchType.MOVIES), partial.errors.keys)
                    search.retry()
                    assertEquals(3, search.completed().results.size)
                    assertTrue(search.state.value.errors.isEmpty())
                    assertEquals(4, server.requestCount)
                } finally { scope.cancel() }
            }
        }
    }

    @Test fun blankInputDoesNotFetchCatalogAndFiltersDoNotExpandItsScope() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody(live))
            XtreamClient().use { client ->
                val scope = CoroutineScope(coroutineContext + SupervisorJob())
                try {
                    val search = DesktopSearchController(client, scope)
                    search.open(ServerCredentials(server.url("/").toString(), "fixture", "fixture"))
                    search.filter(DesktopSearchType.LIVE); search.query("  ")
                    assertEquals(0, server.requestCount)
                    search.query("cafe")
                    assertEquals(DesktopSearchType.LIVE, search.completed().results.single().type)
                    assertEquals(1, server.requestCount)
                    search.query("")
                    assertTrue(search.completed().results.isEmpty())
                    assertEquals(1, server.requestCount)
                } finally { scope.cancel() }
            }
        }
    }
}
