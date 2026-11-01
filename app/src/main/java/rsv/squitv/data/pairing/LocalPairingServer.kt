package rsv.squitv.data.pairing

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class LocalPairingServer(
    private val port: Int = DEFAULT_PAIRING_PORT,
    private val onLoginSubmitted: suspend (username: String, password: String, token: String) -> LoginResult,
    private val getSessionStatus: () -> PairingSession?,
    private val getPairingState: () -> PairingState = { PairingState.WAITING }
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var actualPort: Int = 0
        private set
    val isBound: Boolean get() = serverSocket?.isBound == true && serverSocket?.isClosed == false

    sealed interface LoginResult {
        data object Success : LoginResult
        data class Error(val message: String) : LoginResult
    }

    suspend fun start(): Int = withContext(Dispatchers.IO) {
        if (isRunning.get()) return@withContext actualPort
        val listener = ServerSocket(port).apply { reuseAddress = true }
        require(listener.isBound && !listener.isClosed && (port == 0 || listener.localPort == port))
        serverSocket = listener
        actualPort = listener.localPort
        isRunning.set(true)
        serverJob = serverScope.launch {
            while (isRunning.get() && !listener.isClosed) {
                try {
                    val client = listener.accept()
                    // Admitted requests can finish their reply when successful login closes the screen.
                    // Read/authentication deadlines bound their lifetime; the repository checks session identity.
                    serverScope.launch { handleClient(client) }
                } catch (e: Exception) {
                    if (isRunning.get()) Timber.w(e, "Erro ao aceitar conexão local")
                }
            }
        }
        actualPort
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        actualPort = 0
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5000
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()
            try {
                val request = readLine(input)?.split(" ") ?: return
                require(request.size >= 2)
                val method = request[0].uppercase()
                val target = request[1]
                val path = target.substringBefore('?')
                var length = 0
                var count = 0
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    require(++count <= 50)
                    if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                }
                when {
                    method == "GET" && path == "/" -> respond(output, 200, "text/html", LocalPairingPage.render(getSessionStatus()))
                    method == "GET" && path == "/pair/status" -> {
                        val session = getSessionStatus()
                        val token = parseUrlEncoded(target.substringAfter('?', ""))["token"]
                        val status = when {
                            session == null || (token != null && token != session.token) -> "EXPIRED"
                            session.isUsed.get() -> "SUCCESS"
                            session.isExpired() -> "EXPIRED"
                            getPairingState() == PairingState.AUTHENTICATING -> "AUTHENTICATING"
                            else -> "WAITING"
                        }
                        json(output, 200, buildJsonObject { put("status", status) })
                    }
                    method == "POST" && path == "/pair/login" -> {
                        require(length in 1..16384)
                        val bytes = ByteArray(length)
                        var offset = 0
                        while (offset < length) {
                            val read = input.read(bytes, offset, length - offset)
                            require(read > 0)
                            offset += read
                        }
                        val params = parseUrlEncoded(String(bytes, Charsets.UTF_8))
                        val session = getSessionStatus()
                        val token = params["token"].orEmpty()
                        val username = params["username"].orEmpty().trim()
                        val password = params["password"].orEmpty()
                        when {
                            session == null || session.isExpired() -> error(output, 410, "Sessão expirada. Abra um novo login no aparelho.")
                            token.isBlank() || session.token != token -> error(output, 403, "Sessão inválida. Confira o endereço exibido no aparelho.")
                            session.isUsed.get() -> error(output, 409, "Este aparelho já foi conectado.")
                            username.isBlank() || password.isBlank() -> error(output, 400, "Preencha o usuário e a senha.")
                            else -> {
                                val result = withTimeout(45_000L) { onLoginSubmitted(username, password, token) }
                                when (result) {
                                    LoginResult.Success -> json(output, 200, buildJsonObject { put("success", true) })
                                    is LoginResult.Error -> error(output, 400, result.message)
                                }
                            }
                        }
                    }
                    method !in setOf("GET", "POST") -> error(output, 405, "Método não permitido.")
                    else -> error(output, 404, "Página não encontrada.")
                }
            } catch (_: TimeoutCancellationException) {
                error(output, 504, "A verificação demorou. Confira o aparelho antes de tentar novamente.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalArgumentException) {
                error(output, 400, "Requisição inválida.")
            } catch (e: Exception) {
                Timber.w(e, "Erro na conexão HTTP local")
            }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>()
        while (bytes.size <= 8192) {
            val value = input.read()
            if (value == -1) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), Charsets.US_ASCII).trimEnd('\r')
            if (value == 10) return String(bytes.toByteArray(), Charsets.US_ASCII).trimEnd('\r')
            bytes.add(value.toByte())
        }
        throw IllegalArgumentException("HTTP line too long")
    }

    private fun parseUrlEncoded(body: String): Map<String, String> =
        body.split('&').mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) null else URLDecoder.decode(pair.substring(0, index), "UTF-8") to
                URLDecoder.decode(pair.substring(index + 1), "UTF-8")
        }.toMap()

    private fun error(output: OutputStream, code: Int, message: String) =
        json(output, code, buildJsonObject { put("success", false); put("error", message) })

    private fun json(output: OutputStream, code: Int, body: JsonObject) =
        respond(output, code, "application/json", body.toString())

    private fun respond(output: OutputStream, code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"
            405 -> "Method Not Allowed"; 409 -> "Conflict"; 410 -> "Gone"; 504 -> "Gateway Timeout"
            else -> "Error"
        }
        val headers = "HTTP/1.1 $code $reason\r\nContent-Type: $contentType; charset=UTF-8\r\n" +
            "Content-Length: " + bytes.size + "\r\nCache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    companion object {
        const val DEFAULT_PAIRING_PORT = 1234
        fun getLocalIpAddress(): String {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (network in interfaces) {
                    if (!network.isUp || network.isLoopback) continue
                    for (address in Collections.list(network.inetAddresses)) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            address.hostAddress?.takeIf { !it.startsWith("169.254") }?.let { return it }
                        }
                    }
                }
            } catch (e: Exception) { Timber.w(e, "Erro ao obter endereço local") }
            return "127.0.0.1"
        }
    }
}
