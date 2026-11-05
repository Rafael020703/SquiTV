package rsv.squitv.shared.network

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.WeakHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import okhttp3.Dns
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps

enum class AppDnsMode(val label: String) {
    SYSTEM("Desativado — usar DNS do aparelho"), AUTO("Automático"), GOOGLE("Google"), CLOUDFLARE("Cloudflare")
}
data class AppDnsState(val mode: AppDnsMode = AppDnsMode.AUTO,
    val milliseconds: Map<AppDnsMode, Long?> = emptyMap(), val measuring: Boolean = false,
    val saving: Boolean = false, val fastest: AppDnsMode? = null, val error: String? = null)

/** Process-local resolver. Never changes OS, network adapters, URLs, TLS names or credentials. */
object AppDns : Dns {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val measurement = Mutex()
    private val selection = Mutex()
    private val mutable = MutableStateFlow(AppDnsState())
    val state = mutable.asStateFlow()
    private var save: (String) -> Unit = { error("DNS settings unavailable") }
    @Volatile private var measuredAt = 0L
    private data class Cached(val addresses: List<InetAddress>, val until: Long)
    private val cache = linkedMapOf<String, Cached>()
    private val pools = WeakHashMap<ConnectionPool, Unit>()

    fun register(client: OkHttpClient) {
        synchronized(pools) { pools[client.connectionPool] = Unit }
    }

    private fun discardIdleConnections() {
        val idlePools = synchronized(pools) { pools.keys.toList() }
        idlePools.forEach { it.evictAll() } // Active streams/calls stay open.
    }
    private val bootstrap by lazy { OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS).callTimeout(4, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build() }
    private val resolvers by lazy { listOf(AppDnsMode.GOOGLE, AppDnsMode.CLOUDFLARE).associateWith(::resolver) }

    fun configure(mode: String?, persist: (String) -> Unit, error: String? = null) {
        save = persist
        val parsed = AppDnsMode.entries.firstOrNull { it.name == mode }
            ?: if (mode == null) AppDnsMode.AUTO else AppDnsMode.SYSTEM
        mutable.update { it.copy(mode = parsed, error = error) }
        synchronized(cache) { cache.clear() }
    }

    private fun resolver(provider: AppDnsMode): DnsOverHttps {
        val google = provider == AppDnsMode.GOOGLE
        val ips = if (google) listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888")
            else listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111")
        return DnsOverHttps.Builder().client(bootstrap)
            .url((if (google) "https://dns.google/dns-query" else "https://cloudflare-dns.com/dns-query").toHttpUrl())
            .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) }).includeIPv6(true).post(true).build()
    }

    suspend fun select(mode: AppDnsMode) {
        if (!selection.tryLock()) return
        mutable.update { it.copy(saving = true, error = null) }
        try {
            // A completed disk write must also become the active choice if the screen closes.
            withContext(NonCancellable + Dispatchers.IO) {
                save(mode.name)
                synchronized(cache) { cache.clear() }
                mutable.update { it.copy(mode = mode) }
                discardIdleConnections()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(error = "Não foi possível salvar o DNS. A escolha anterior foi mantida.") } }
        finally { mutable.update { it.copy(saving = false) }; selection.unlock() }
        if (mode != AppDnsMode.SYSTEM && mutable.value.mode == mode) measure()
    }

    suspend fun measure() {
        if (!measurement.tryLock()) return
        mutable.update { it.copy(measuring = true) }
        try {
            val results = coroutineScope {
                listOf(AppDnsMode.GOOGLE, AppDnsMode.CLOUDFLARE).map { provider -> async {
                    val milliseconds = try {
                        val fresh = resolver(provider)
                        var elapsed = 0L
                        for (host in listOf("example.com", "www.iana.org")) {
                            val started = System.nanoTime()
                            val addresses = runInterruptible(Dispatchers.IO) { fresh.lookup(host) }
                            require(addresses.isNotEmpty())
                            elapsed += System.nanoTime() - started
                        }
                        (elapsed / 2 / 1_000_000).coerceAtLeast(1)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    provider to milliseconds
                } }.awaitAll().toMap()
            }
            measuredAt = System.nanoTime()
            mutable.update { it.copy(milliseconds = results,
                fastest = results.filterValues { value -> value != null }.minByOrNull { it.value!! }?.key) }
        } finally { mutable.update { it.copy(measuring = false) }; measurement.unlock() }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.lowercase().removeSuffix(".")
        val mode = mutable.value.mode
        // Private/local names stay in the local resolver; public DNS must not receive them.
        if (mode == AppDnsMode.SYSTEM || !host.contains('.') || host.endsWith(".local") || host.endsWith(".lan") ||
            host.endsWith(".home.arpa") || host.endsWith(".internal") || host.endsWith(".localhost") ||
            host.contains(':') || host.matches(Regex("[0-9.]+"))) return Dns.SYSTEM.lookup(host)
        if (mode == AppDnsMode.AUTO && !mutable.value.measuring &&
            (measuredAt == 0L || System.nanoTime() - measuredAt > TimeUnit.MINUTES.toNanos(15))) {
            scope.launch { measure() }
        }
        val key = "${mode.name}/$host"
        synchronized(cache) { cache[key]?.takeIf { it.until > System.nanoTime() }?.let { return it.addresses } }
        val first = if (mode == AppDnsMode.AUTO) mutable.value.fastest ?: AppDnsMode.CLOUDFLARE else mode
        val choices = if (mode == AppDnsMode.AUTO) listOf(first,
            if (first == AppDnsMode.GOOGLE) AppDnsMode.CLOUDFLARE else AppDnsMode.GOOGLE) else listOf(first)
        for (provider in choices) {
            try {
                val result = resolvers.getValue(provider).lookup(host)
                if (result.isEmpty()) continue
                synchronized(cache) {
                    cache[key] = Cached(result, System.nanoTime() + TimeUnit.SECONDS.toNanos(60))
                    while (cache.size > 128) cache.remove(cache.keys.first())
                }
                return result
            } catch (_: UnknownHostException) { /* Auto tries the other public provider; no negative cache. */ }
        }
        throw UnknownHostException("O DNS do aplicativo não conseguiu resolver o endereço.")
    }
}
