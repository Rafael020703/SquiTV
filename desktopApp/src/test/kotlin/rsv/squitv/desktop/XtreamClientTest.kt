package rsv.squitv.desktop

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.desktop.data.*
import rsv.squitv.shared.xtream.*

class XtreamClientTest {
    @Test fun loginAndCategoryRequestsUseEncodedCredentialsAndSelectedCategory() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"user_info":{"auth":"1","status":"Active"}}"""))
            server.enqueue(MockResponse().setBody("""[{"category_id":5,"category_name":"Notícias"}]"""))
            server.enqueue(MockResponse().setBody("""[{"stream_id":9,"name":"Canal","category_id":"5"}]"""))
            XtreamClient().use { client ->
                val credentials = ServerCredentials(server.url("/base/").toString(), "a&b", "p?+ x")
                assertEquals("a&b", client.login(credentials).username)
                assertEquals("5", client.categories(credentials).single().id)
                assertEquals(9, client.channels(credentials, "5").single().id)
                val login = server.takeRequest()
                assertEquals("/base/player_api.php", login.requestUrl!!.encodedPath)
                assertEquals("a&b", login.requestUrl!!.queryParameter("username"))
                assertEquals("p?+ x", login.requestUrl!!.queryParameter("password"))
                server.takeRequest()
                val request = server.takeRequest()
                assertEquals("get_live_streams", request.requestUrl!!.queryParameter("action"))
                assertEquals("5", request.requestUrl!!.queryParameter("category_id"))
            }
        }
    }

    @Test fun rejectsHttpErrorWithoutLeakingCredentialUrl() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(401))
            XtreamClient().use { client ->
                try { client.login(ServerCredentials(server.url("/").toString(), "fixture-user", "fixture-secret")); fail() }
                catch (e: ServerProblem) {
                    assertFalse(e.message!!.contains("fixture-secret"))
                    assertFalse(e.message!!.contains("fixture-user"))
                    assertFalse(e.message!!.contains("?"))
                }
            }
        }
    }

    @Test fun neverFollowsCredentialRedirect() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://example.invalid/"))
            XtreamClient().use { client ->
                try { client.login(ServerCredentials(server.url("/").toString(), "fixture", "secret")); fail() }
                catch (_: ServerProblem) { assertEquals(1, server.requestCount) }
            }
        }
    }

    @Test fun cancellationInterruptsNetworkWait() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            XtreamClient().use { client ->
                val job = launch(Dispatchers.Default) { client.login(ServerCredentials(server.url("/").toString(), "fixture", "secret")) }
                withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
                withContext(Dispatchers.Default) { withTimeout(2000) { job.cancelAndJoin() } }
                assertTrue(job.isCancelled)
            }
        }
    }

    @Test fun windowsStoreEncryptsRoundTripsAndRemovesOnlyItsOwnFile() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val directory = Files.createTempDirectory("squitv-store-test-")
        try {
            val store = WindowsCredentialStore(directory)
            val credentials = ServerCredentials("https://example.invalid/", "fixture", "fixture-secret")
            assertNull(store.load())
            store.save(credentials)
            assertEquals(credentials, store.load())
            assertFalse(Files.readAllBytes(directory.resolve("account.dpapi")).toString(Charsets.ISO_8859_1).contains("fixture-secret"))
            store.clear()
            assertNull(store.load())
        } finally { Files.deleteIfExists(directory.resolve("account.dpapi")); Files.deleteIfExists(directory) }
    }
}
