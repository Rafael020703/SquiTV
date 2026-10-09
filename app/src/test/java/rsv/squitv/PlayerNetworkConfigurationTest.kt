package rsv.squitv

import rsv.squitv.data.network.RetrofitClient
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNetworkConfigurationTest {

    @Test
    fun `playerOkHttpClient configuration has robust timeouts for video streaming`() {
        val playerClient = RetrofitClient.playerOkHttpClient

        assertEquals("Connect timeout should be 15000ms", 15000, playerClient.connectTimeoutMillis)
        assertEquals("Read timeout should be 30000ms for media streaming", 30000, playerClient.readTimeoutMillis)
        assertEquals("Write timeout should be 30000ms", 30000, playerClient.writeTimeoutMillis)
        assertTrue("playerOkHttpClient must follow redirects", playerClient.followRedirects)
        assertTrue("playerOkHttpClient must follow SSL redirects", playerClient.followSslRedirects)
    }

    @Test
    fun `apiOkHttpClient configuration has standard 60s timeouts`() {
        val apiClient = RetrofitClient.okHttpClient

        assertEquals("Connect timeout should be 60000ms", 60000, apiClient.connectTimeoutMillis)
        assertEquals("Read timeout should be 60000ms", 60000, apiClient.readTimeoutMillis)
        assertEquals("Write timeout should be 60000ms", 60000, apiClient.writeTimeoutMillis)
    }

    @Test
    fun `playerOkHttpClient sets IPTVSmarters User-Agent and does not enforce Connection close`() {
        var processedUserAgent: String? = null
        var processedConnectionHeader: String? = null

        val mockTerminalInterceptor = Interceptor { chain ->
            val req = chain.request()
            processedUserAgent = req.header("User-Agent")
            processedConnectionHeader = req.header("Connection")
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("OK".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        // Build client with existing interceptors plus terminal mock interceptor at end
        val interceptors = RetrofitClient.playerOkHttpClient.interceptors + mockTerminalInterceptor
        val client = RetrofitClient.playerOkHttpClient.newBuilder()
            .apply {
                interceptors().clear()
                interceptors().addAll(interceptors)
            }
            .build()

        val request = Request.Builder()
            .url("http://localhost:8080/live/user/pass/1.ts")
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)
        assertEquals("IPTVSmarters", processedUserAgent)
        assertNull("Connection close header should not be forced", processedConnectionHeader)
    }
}
