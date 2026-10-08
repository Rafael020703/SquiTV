package rsv.squitv

import rsv.squitv.core.data.repository.StreamRepository
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.network.BaseUrlInterceptor
import rsv.squitv.data.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpHttpsCompatibilityTest {

    private lateinit var streamRepository: StreamRepository

    @Before
    fun setUp() {
        streamRepository = StreamRepository()
    }

    @Test
    fun `streamRepository generates correct HTTP live stream url`() {
        val creds = XtreamCredentials("user123", "pass123", "http://server-http.com:8080")
        val url = streamRepository.getStreamUrl(creds, 101, "live")
        assertEquals("http://server-http.com:8080/live/user123/pass123/101.ts", url)
    }

    @Test
    fun `streamRepository generates correct HTTPS live stream url`() {
        val creds = XtreamCredentials("user123", "pass123", "https://server-https.com:8443")
        val url = streamRepository.getStreamUrl(creds, 202, "live")
        assertEquals("https://server-https.com:8443/live/user123/pass123/202.ts", url)
    }

    @Test
    fun `streamRepository generates correct HTTP VOD stream url`() {
        val creds = XtreamCredentials("user123", "pass123", "http://vod-server.com")
        val url = streamRepository.getStreamUrl(creds, 505, "movie", "mp4")
        assertEquals("http://vod-server.com/movie/user123/pass123/505.mp4", url)
    }

    @Test
    fun `streamRepository generates correct HTTPS VOD stream url`() {
        val creds = XtreamCredentials("user123", "pass123", "https://vod-server.com")
        val url = streamRepository.getStreamUrl(creds, 606, "movie", "mkv")
        assertEquals("https://vod-server.com/movie/user123/pass123/606.mkv", url)
    }

    @Test
    fun `streamRepository prepends http default fallback when scheme is missing`() {
        val creds = XtreamCredentials("user123", "pass123", "raw-domain.com:8080")
        val url = streamRepository.getStreamUrl(creds, 303, "live")
        assertEquals("http://raw-domain.com:8080/live/user123/pass123/303.ts", url)
    }

    @Test
    fun `playerOkHttpClient has followSslRedirects and followRedirects enabled`() {
        val playerClient = RetrofitClient.playerOkHttpClient
        assertTrue("playerOkHttpClient must follow redirects", playerClient.followRedirects)
        assertTrue("playerOkHttpClient must follow SSL cross-protocol redirects", playerClient.followSslRedirects)
    }

    @Test
    fun `baseUrlInterceptor handles switching between HTTP and HTTPS base URLs`() {
        val interceptor = BaseUrlInterceptor()
        
        // 1. Set HTTP base URL
        interceptor.updateBaseUrl("http://http-server.com:8080/")
        var requestedUrl = ""
        var client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                requestedUrl = request.url.toString()
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("OK".toResponseBody("text/plain".toMediaType())).build()
            }.build()

        client.newCall(Request.Builder().url("http://dummy.com/player_api.php").build()).execute()
        assertEquals("http://http-server.com:8080/player_api.php", requestedUrl)

        // 2. Switch to HTTPS base URL
        interceptor.updateBaseUrl("https://https-server.com:8443/")
        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                requestedUrl = request.url.toString()
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("OK".toResponseBody("text/plain".toMediaType())).build()
            }.build()

        client.newCall(Request.Builder().url("http://dummy.com/player_api.php").build()).execute()
        assertEquals("https://https-server.com:8443/player_api.php", requestedUrl)
    }
}
