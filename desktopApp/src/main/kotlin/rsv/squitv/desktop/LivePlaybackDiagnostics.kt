package rsv.squitv.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import rsv.squitv.shared.network.AppDnsMode
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal enum class LivePlaybackOrigin(val label: String) { PLAYER("Ao vivo"), MULTIVIEW("Multi-view") }
internal enum class LiveDiagnosticEvent(val label: String) {
    OPEN("Abertura solicitada"),
    MANUAL_RETRY("Nova tentativa solicitada"),
    START("Entrega ao LibVLC"),
    OPENING("LibVLC abrindo a entrada"),
    BUFFERING("LibVLC carregando o buffer"),
    PLAYING("LibVLC entrou em reprodução"),
    END("LibVLC encerrou a entrada"),
    ERROR("LibVLC sinalizou falha"),
    RETRY_SCHEDULED("Uma reconexão automática programada"),
    RETRY_STARTED("Reconexão automática iniciada"),
    SWITCHED("Tentativa substituída por outra abertura"),
    CLOSED("Player fechado"),
    ENGINE_FAILURE("Falha ao inicializar ou comandar o LibVLC"),
    EVENT_MONITOR_UNAVAILABLE("Eventos nativos indisponíveis; usando consulta do estado"),
    LOG_MONITOR_UNAVAILABLE("Avisos nativos indisponíveis"),
    HTTP_HINT("LibVLC indicou erro ou recusa HTTP (código não capturado)"),
    DNS_HINT("LibVLC indicou falha de resolução de nome"),
    TLS_HINT("LibVLC indicou problema de TLS ou certificado"),
    TIMEOUT_HINT("LibVLC indicou tempo limite"),
    CONNECTION_HINT("LibVLC indicou problema de conexão ou leitura"),
    DECODER_HINT("LibVLC indicou problema no formato ou na decodificação"),
    NATIVE_WARNING("LibVLC emitiu um aviso sem classificação"),
    NATIVE_ERROR("LibVLC emitiu um erro sem classificação")
}

@Serializable
internal data class LiveDiagnosticEntry(
    val session: String,
    val channelId: Int?,
    val origin: LivePlaybackOrigin,
    val attempt: Int,
    val time: Long,
    val elapsedMs: Long,
    val event: LiveDiagnosticEvent,
    val appVersion: String = DesktopBuildInfo.VERSION,
    val dns: AppDnsMode? = null,
    val proxy: Boolean? = null,
    val https: Boolean? = null
) {
    fun display(): String = buildString {
        append(timestamp.format(Instant.ofEpochMilli(time)))
        append(" · ").append(origin.label)
        channelId?.let { append(" · Canal #").append(it) }
        append(" · ").append(session.take(8)).append("/").append(attempt)
        append(" · +").append(elapsedMs).append(" ms · ").append(event.label)
        dns?.let { append(" · DNS ").append(it.label) }
        proxy?.let { append(if (it) " · ponte DNS configurada" else " · sem ponte DNS do app") }
        https?.let { append(if (it) " · entrada HTTPS" else " · entrada HTTP") }
        append(" · v").append(appVersion)
    }

    companion object {
        private val timestamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }
}

/** Only typed events and numeric channel IDs reach the journal. Native log strings never do. */
internal class LivePlaybackAttempt(
    private val log: LivePlaybackDiagnostics?,
    private val channelId: Int?,
    private val origin: LivePlaybackOrigin,
    private val session: String = UUID.randomUUID().toString(),
    val number: Int = 1
) {
    private val started = System.nanoTime()
    private val seen = EnumSet.noneOf(LiveDiagnosticEvent::class.java)
    val terminal = AtomicInteger()

    fun record(event: LiveDiagnosticEvent, dns: AppDnsMode? = null, proxy: Boolean? = null, https: Boolean? = null) {
        val target = log ?: return
        synchronized(seen) {
            if (!seen.add(event)) return
            target.record(LiveDiagnosticEntry(session, channelId, origin, number, System.currentTimeMillis(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started).coerceAtLeast(0), event,
                dns = dns, proxy = proxy, https = https))
        }
    }

    fun next() = LivePlaybackAttempt(log, channelId, origin, session, number + 1)
}

internal class LivePlaybackDiagnostics(directory: Path, scope: CoroutineScope) {
    private val file = DesktopJsonFile(directory.resolve("live-playback-diagnostics.json"), LiveDiagnosticJournal(),
        LiveDiagnosticJournal.serializer(), 128 * 1024, "o diagnóstico ao vivo") { journal ->
        require(journal.schema == 1 && journal.entries.size <= LIMIT)
        require(journal.entries.all { it.session.matches(uuid) && it.appVersion.matches(version) &&
            (it.channelId == null || it.channelId > 0) && it.attempt in 1..2 &&
            it.time in 1..253402300799999L && it.elapsedMs >= 0 })
    }
    private val lock = Any()
    private val writer = Mutex()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val mutable = MutableStateFlow<List<LiveDiagnosticEntry>>(emptyList())
    val entries = mutable.asStateFlow()
    val error = file.error
    private var loaded = false
    private var cleared = false
    private var dirty = false

    init {
        scope.launch(Dispatchers.IO) {
            flush()
            for (signal in changes) {
                delay(250)
                flush()
            }
        }
    }

    fun record(entry: LiveDiagnosticEntry) {
        synchronized(lock) {
            mutable.value = retained(mutable.value + entry)
            dirty = true
        }
        changes.trySend(Unit)
    }

    fun clear() {
        synchronized(lock) {
            cleared = true
            mutable.value = emptyList()
            dirty = true
        }
        changes.trySend(Unit)
    }

    fun prune() {
        synchronized(lock) {
            val next = retained(mutable.value)
            if (next == mutable.value) return
            mutable.value = next
            dirty = true
        }
        changes.trySend(Unit)
    }

    suspend fun flush() = writer.withLock {
        if (!loaded) {
            val restored = file.load().entries
            synchronized(lock) {
                mutable.value = retained((if (cleared) emptyList() else restored) + mutable.value)
                dirty = dirty || mutable.value != restored
                loaded = true
            }
        }
        val snapshot = synchronized(lock) {
            if (!dirty) return@withLock
            dirty = false
            LiveDiagnosticJournal(entries = mutable.value)
        }
        if (!file.update { snapshot }) synchronized(lock) { dirty = true }
    }

    private fun retained(entries: List<LiveDiagnosticEntry>): List<LiveDiagnosticEntry> {
        val now = System.currentTimeMillis()
        return entries.filter { it.time in (now - TimeUnit.HOURS.toMillis(72))..now }.takeLast(LIMIT)
    }

    companion object {
        private const val LIMIT = 200
        private val uuid = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        private val version = Regex("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")
    }
}

@Serializable
private data class LiveDiagnosticJournal(val schema: Int = 1, val entries: List<LiveDiagnosticEntry> = emptyList())
