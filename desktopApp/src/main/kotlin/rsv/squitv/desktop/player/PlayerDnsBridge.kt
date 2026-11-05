package rsv.squitv.desktop.player

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import rsv.squitv.shared.network.AppDns

/**
 * LibVLC does not use OkHttp's DNS. Its private HTTP proxy routes HTTP(S) sockets through
 * the same resolver, keeping the original TLS stream, SNI and certificate validation.
 * Nothing is registered with Windows; authentication is random and process-local.
 */
internal object PlayerDnsBridge : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val capacity = Semaphore(32)
    private val workers = Executors.newFixedThreadPool(64) { task ->
        Thread(task, "squitv-player-network").apply { isDaemon = true }
    }
    private val secret = ByteArray(24).also { SecureRandom().nextBytes(it) }
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private val authorization = "Basic " + Base64.getEncoder()
        .encodeToString("squitv:$secret".toByteArray(Charsets.US_ASCII))
    private val server = ServerSocket().apply {
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 32)
    }
    val proxyUrl: String = "http://squitv:$secret@127.0.0.1:${server.localPort}"

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ close() }, "squitv-player-network-close"))
        Thread({
            while (!closed.get()) {
                val client = try { server.accept() } catch (_: Exception) { break }
                if (!capacity.tryAcquire()) { client.close(); continue }
                sockets.add(client)
                try { workers.execute { handle(client) } }
                catch (_: Exception) { sockets.remove(client); client.close(); capacity.release() }
            }
        }, "squitv-player-network-accept").apply { isDaemon = true; start() }
    }

    private fun handle(client: Socket) {
        var upstream: Socket? = null
        var responseStarted = false
        try {
            client.soTimeout = 10_000
            client.tcpNoDelay = true
            val input = BufferedInputStream(client.getInputStream(), 8192)
            val request = readHeaders(input)
            val start = request.first().split(' ', limit = 3)
            require(start.size == 3 && start[2] in listOf("HTTP/1.0", "HTTP/1.1"))
            val headers = request.drop(1).map { line ->
                val colon = line.indexOf(':')
                require(colon > 0 && line.take(colon).all { it.isLetterOrDigit() || it == '-' })
                line.substring(0, colon) to line.substring(colon + 1).trim()
            }
            val auth = headers.filter { it.first.equals("proxy-authorization", true) }.singleOrNull()?.second
            if (auth == null || !MessageDigest.isEqual(auth.toByteArray(Charsets.US_ASCII),
                    authorization.toByteArray(Charsets.US_ASCII))) {
                reply(client, "407 Proxy Authentication Required", "Proxy-Authenticate: Basic realm=\"SquiTV\"\r\n")
                return
            }
            val tunnel = start[0] == "CONNECT"
            require(tunnel || start[0] in listOf("GET", "HEAD"))
            val target = start[1]
            if (tunnel) require(target.none { it in "/?#@" })
            val url = (if (tunnel) "http://$target/" else target).toHttpUrlOrNull()
                ?: error("Invalid destination")
            require(url.scheme == "http" && url.username.isEmpty() && url.password.isEmpty())
            require(headers.none { it.first.equals("transfer-encoding", true) ||
                (it.first.equals("content-length", true) && it.second != "0") })
            upstream = connect(url.host, url.port)
            if (tunnel) {
                client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII))
                client.getOutputStream().flush()
                responseStarted = true
            } else {
                val connectionTokens = headers.filter { it.first.equals("connection", true) }
                    .flatMap { it.second.lowercase().split(',').map(String::trim) }.toSet()
                val excluded = setOf("host", "proxy-authorization", "proxy-connection", "connection",
                    "keep-alive", "te", "trailer", "upgrade", "content-length") + connectionTokens
                val authority = (if (url.host.contains(':')) "[${url.host}]" else url.host) +
                    if (url.port != 80) ":${url.port}" else ""
                val path = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
                val forwarded = buildString {
                    append("${start[0]} $path ${start[2]}\r\nHost: $authority\r\n")
                    headers.filter { it.first.lowercase() !in excluded }.forEach { (name, value) -> append("$name: $value\r\n") }
                    append("Connection: close\r\n\r\n")
                }
                upstream.getOutputStream().write(forwarded.toByteArray(Charsets.ISO_8859_1))
                upstream.getOutputStream().flush()
                responseStarted = true
            }
            client.soTimeout = 0
            val remote = upstream
            // Also watch GET/HEAD cancellation while the origin is silent. Otherwise quick
            // channel changes could keep all slots occupied until the upstream read timeout.
            workers.execute {
                try {
                    if (tunnel) input.copyTo(remote.getOutputStream(), 32 * 1024)
                    else input.read() // No request body/pipelining is accepted on this connection.
                } catch (_: Exception) { /* Disconnection/cancellation is normal. */ }
                finally { runCatching { remote.close() }; runCatching { client.close() } }
            }
            upstream.getInputStream().copyTo(client.getOutputStream(), 32 * 1024)
        } catch (_: Exception) {
            if (!responseStarted) runCatching { reply(client, "502 Bad Gateway") }
        } finally {
            upstream?.let { sockets.remove(it); runCatching { it.close() } }
            sockets.remove(client)
            runCatching { client.close() }
            capacity.release()
        }
    }

    private fun connect(host: String, port: Int): Socket {
        val deadline = System.nanoTime() + 12_000_000_000L
        for (address in AppDns.lookup(host)) {
            require(port != server.localPort || !address.isLoopbackAddress)
            val remaining = ((deadline - System.nanoTime()) / 1_000_000).coerceAtMost(3000).toInt()
            if (remaining <= 0 || closed.get()) break
            val socket = Socket()
            sockets.add(socket)
            try {
                socket.connect(InetSocketAddress(address, port), remaining)
                socket.soTimeout = 60_000
                socket.tcpNoDelay = true
                return socket
            } catch (_: Exception) { sockets.remove(socket); runCatching { socket.close() } }
        }
        error("Connection unavailable")
    }

    private fun readHeaders(input: InputStream): List<String> {
        val bytes = ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf(13, 10, 13, 10)
        while (bytes.size() < 32 * 1024) {
            val next = input.read()
            require(next >= 0 && next != 0)
            bytes.write(next)
            matched = if (next == end[matched].toInt()) matched + 1 else if (next == 13) 1 else 0
            if (matched == 4) {
                return bytes.toString(Charsets.ISO_8859_1.name()).dropLast(4).split("\r\n")
                    .also { lines -> require(lines.all { line -> line.none { it.code < 32 || it.code == 127 } }) }
            }
        }
        error("Headers too large")
    }

    private fun reply(client: Socket, status: String, extra: String = "") {
        client.getOutputStream().write(
            "HTTP/1.1 $status\r\n${extra}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
        client.getOutputStream().flush()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }
}
