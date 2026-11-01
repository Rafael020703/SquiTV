package rsv.squitv.data.pairing

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import android.content.Context
import rsv.squitv.core.domain.interactor.AuthManager
import rsv.squitv.data.model.*
import rsv.squitv.data.repository.SettingsRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

class LocalPairingHttpTest {
    private var server: LocalPairingServer? = null
    private var repository: PairingRepository? = null
    private var session = PairingSession.generate()
    @After fun close() { repository?.stopPairingSession(); server?.stop() }

    private data class Reply(val code: Int, val text: String, val length: Int)
    private fun request(port: Int, path: String, body: String? = null): Reply {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 6000
        if (body != null) {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val bytes = body.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
        }
        return try {
            val code = connection.responseCode
            val text = (if (code >= 400) connection.errorStream else connection.inputStream)
                .use { it.readBytes().toString(Charsets.UTF_8) }
            Reply(code, text, connection.getHeaderFieldInt("Content-Length", -1))
        } finally { connection.disconnect() }
    }
    private fun form(password: String, token: String = session.token) =
        "token=" + URLEncoder.encode(token, "UTF-8") +
            "&username=" + URLEncoder.encode("usuário", "UTF-8") +
            "&password=" + URLEncoder.encode(password, "UTF-8")

    private suspend fun start(callback: suspend (String, String, String) -> LocalPairingServer.LoginResult): Int {
        server = LocalPairingServer(0, callback, { session })
        return server!!.start()
    }
    @Test fun unicodeAndQuotedErrorsHaveValidJsonAndByteLengths() = runBlocking {
        val port = start { username, password, _ ->
            assertEquals("usuário", username)
            assertEquals(" senha + ç ", password)
            LocalPairingServer.LoginResult.Error("Senha \"inválida\"\nTente novamente.")
        }
        val reply = request(port, "/pair/login", form(" senha + ç "))
        assertEquals(400, reply.code)
        assertEquals(reply.text.toByteArray(Charsets.UTF_8).size, reply.length)
        val json = kotlinx.serialization.json.Json.parseToJsonElement(reply.text).toString()
        assertTrue(json.contains("\\\"inválida\\\""))
        assertTrue(json.contains("\\n"))
    }
    @Test fun invalidExpiredAndUsedSessionsNeverReachAuthentication() = runBlocking {
        val calls = AtomicInteger()
        val port = start { _, _, _ -> calls.incrementAndGet(); LocalPairingServer.LoginResult.Success }
        assertEquals(403, request(port, "/pair/login", form("test", "wrong")).code)
        session = session.copy(expirationTime = 1)
        assertEquals(410, request(port, "/pair/login", form("test")).code)
        session = PairingSession.generate().also { it.isUsed.set(true) }
        assertEquals(409, request(port, "/pair/login", form("test")).code)
        assertEquals(0, calls.get())
        assertTrue(request(port, "/pair/status?token=wrong").text.contains("EXPIRED"))
    }
    @Test fun successfulReplySurvivesClosingTheListeningSocket() = runBlocking {
        val port = start { _, _, _ -> server!!.stop(); LocalPairingServer.LoginResult.Success }
        val reply = request(port, "/pair/login", form("test"))
        assertEquals(200, reply.code)
        assertTrue(reply.text.contains("\"success\":true"))
        assertFalse(server!!.isBound)
    }
    @Test fun failedCredentialsCanBeRetriedWithTheSameRepositorySession() = runBlocking {
        val auth = mock(AuthManager::class.java)
        val settings = mock(SettingsRepository::class.java)
        `when`(settings.settingsFlow).thenReturn(flowOf(SettingsRepository.AppSettings(
            null, lastSyncTimestamp = 0L, syncIntervalHours = 24)))
        val credentials = XtreamCredentials("usuário", " senha + ç ", "http://ded30.com/")
        `when`(auth.login(credentials)).thenReturn(
            XtreamResponse(userInfo = UserInfo(auth = 0, message = "Dados inválidos")),
            XtreamResponse(userInfo = UserInfo(auth = 1)))
        repository = PairingRepository(mock(Context::class.java), auth, settings)
        repository!!.startPairingSession()
        withTimeout(5000) { while (repository!!.serverPort.value == 0) delay(10) }
        session = repository!!.currentSession.value!!
        val port = repository!!.serverPort.value
        assertEquals(400, request(port, "/pair/login", form(" senha + ç ")).code)
        assertFalse(session.isUsed.get())
        assertEquals(200, request(port, "/pair/login", form(" senha + ç ")).code)
        assertTrue(session.isUsed.get())
        verify(settings, times(1)).saveCredentials(credentials)
    }
    @Test fun pageIsSelfContainedAndExportsAVisualPreview() {
        val html = LocalPairingPage.render(PairingSession.generate())
        assertTrue(html.contains("document.execCommand('copy')"))
        assertTrue(html.contains("aria-pressed"))
        assertTrue(html.contains("method=\"post\" action=\"/pair/login\""))
        assertTrue(html.contains("/pair/status?token="))
        assertFalse(html.contains("squitv://"))
        assertFalse(html.contains("fonts.googleapis"))
        assertFalse(html.contains("__TOKEN__"))
        val output = File("build/reports/local-pairing/index.html")
        output.parentFile?.mkdirs()
        output.writeText(html)
        assertTrue(LocalPairingPage.render(null).contains("data-remaining=\"0\""))
    }
}
