package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import rsv.squitv.desktop.data.XtreamClient
import rsv.squitv.domain.model.PreparationSpeed
import rsv.squitv.domain.usecase.GenreUtils
import rsv.squitv.shared.xtream.ServerCredentials
import java.nio.file.Path
import kotlin.math.min
import kotlin.random.Random

@Serializable
internal data class DesktopGenreRecord(val id: Int, val genre: String? = null, val status: String,
    val updatedAt: Long, val attempts: Int = 0, val nextEligibleAt: Long = 0)
@Serializable
internal data class DesktopGenreData(val schema: Int = 1, val records: Map<Int, DesktopGenreRecord> = emptyMap())
internal data class DesktopPreparationState(val kind: CatalogKind? = null, val total: Int = 0, val done: Int = 0,
    val withGenre: Int = 0, val noData: Int = 0, val failed: Int = 0, val running: Boolean = false,
    val paused: Boolean = false, val speed: PreparationSpeed = PreparationSpeed.NORMAL,
    val message: String? = null, val error: String? = null)

/** Windows transport/lifecycle adapter for Android's economical preparation policy. */
internal class DesktopCatalogPreparation(private val client: XtreamClient, private val directory: Path,
    private val scope: CoroutineScope, private val publish: (CatalogKind, Map<Int, DesktopGenreRecord>) -> Unit) {
    private val stores = mutableMapOf<String, DesktopJsonFile<DesktopGenreData>>()
    private val sources = mutableMapOf<CatalogKind, List<CatalogItem>>()
    private var account: ServerCredentials? = null
    private var active: CatalogKind? = null
    private var foreground = true
    private var paused = false
    private var speed = PreparationSpeed.NORMAL
    private var job: Job? = null
    private var resetBarrier: Job? = null
    private var revision = 0L
    private val mutable = MutableStateFlow(DesktopPreparationState())
    val state = mutable.asStateFlow()
    private suspend fun store(key: String, kind: CatalogKind): DesktopJsonFile<DesktopGenreData> {
        resetBarrier?.join()
        require(key.matches(Regex("[a-f0-9]{64}")))
        return stores.getOrPut("$key/${kind.name}") {
            DesktopJsonFile(directory.resolve("catalog-metadata").resolve(key).resolve("${kind.name}.json"),
                DesktopGenreData(), DesktopGenreData.serializer(), 16 * 1024 * 1024, "o cache de gêneros") { data ->
                require(data.schema == 1 && data.records.size <= 100_000 && data.records.all { (id, value) ->
                    id > 0 && id == value.id && value.status in setOf("SUCCESS", "NO_DATA", "FAILED") &&
                        (value.genre?.length ?: 0) <= 4096 && value.attempts in 0..20 &&
                        value.updatedAt >= 0 && value.nextEligibleAt >= 0 &&
                        if (value.status == "SUCCESS") GenreUtils.extractNormalizedGenres(value.genre).isNotEmpty() else value.genre == null
                })
            }
        }
    }

    suspend fun enrich(credentials: ServerCredentials, kind: CatalogKind, items: List<CatalogItem>): List<CatalogItem> {
        val key = liveCategoryScope(credentials) ?: return items
        val cache = store(key, kind)
        cache.load()
        val seeded = withContext(Dispatchers.Default) {
            items.mapNotNull { item ->
                ensureActive()
                if (GenreUtils.extractNormalizedGenres(item.genre).isEmpty() || cache.state.value.records[item.id]?.genre == item.genre) null
                else item.id to DesktopGenreRecord(item.id, item.genre?.take(4096), "SUCCESS", System.currentTimeMillis())
            }.toMap()
        }
        if (seeded.isNotEmpty()) cache.update { it.copy(records = it.records + seeded) }
        currentCoroutineContext().ensureActive()
        val records = cache.state.value.records
        return withContext(Dispatchers.Default) { items.map { item ->
            ensureActive()
            item.copy(genre = item.genre?.takeIf { GenreUtils.extractNormalizedGenres(it).isNotEmpty() } ?: records[item.id]?.genre)
        } }
    }

    suspend fun rememberGenre(credentials: ServerCredentials, kind: CatalogKind, id: Int, genre: String?) {
        val key = liveCategoryScope(credentials) ?: return
        val valid = genre?.take(4096)?.takeIf { GenreUtils.extractNormalizedGenres(it).isNotEmpty() }
        val cache = store(key, kind)
        cache.update { old ->
            if (valid == null && old.records[id]?.genre != null) old
            else old.copy(records = old.records + (id to DesktopGenreRecord(id, valid,
                if (valid == null) "NO_DATA" else "SUCCESS", System.currentTimeMillis())))
        }
        currentCoroutineContext().ensureActive()
        if (account == credentials) publish(kind, cache.state.value.records)
    }

    fun source(credentials: ServerCredentials, kind: CatalogKind, items: List<CatalogItem>) {
        if (account != null && account != credentials) reset()
        account = credentials
        sources[kind] = items
        restart()
    }
    fun setActive(kind: CatalogKind?) { if (active != kind) { active = kind; restart() } }
    fun setForeground(value: Boolean) { if (foreground != value) { foreground = value; restart() } }
    fun setPaused(value: Boolean) { paused = value; restart() }
    fun setSpeed(value: PreparationSpeed) { speed = value; mutable.value = mutable.value.copy(speed = value) }
    fun retryFailures() {
        val kind = active ?: return
        val credentials = account ?: return
        val old = job
        old?.cancel()
        val token = ++revision
        scope.launch {
            old?.join()
            if (token != revision || account != credentials || active != kind) return@launch
            val key = liveCategoryScope(credentials) ?: return@launch
            val cache = store(key, kind)
            cache.update { it.copy(records = it.records.mapValues { (_, record) ->
                if (record.status == "FAILED") record.copy(attempts = 0, nextEligibleAt = 0) else record
            }) }
            if (token == revision && account == credentials && active == kind) { paused = false; restart() }
        }
    }
    fun reset() {
        val old = job
        val oldBarrier = resetBarrier
        ++revision; old?.cancel(); job = null; sources.clear(); account = null; active = null; paused = false
        resetBarrier = scope.launch { old?.join(); oldBarrier?.join(); stores.clear() }
        mutable.value = DesktopPreparationState(speed = speed)
    }

    suspend fun stopAndFlush() {
        active = null; ++revision
        job?.cancelAndJoin()
        resetBarrier?.join()
        job = null
        mutable.value = mutable.value.copy(running = false)
    }

    private fun restart() {
        val old = job
        old?.cancel()
        val token = ++revision
        val kind = active
        val previous = mutable.value.takeIf { it.kind == kind } ?: DesktopPreparationState()
        mutable.value = previous.copy(kind = kind, running = false, paused = paused || !foreground, speed = speed,
            message = when { paused -> "Pausado"; !foreground -> "Pausado em segundo plano"; else -> null })
        job = scope.launch {
            old?.join()
            if (token != revision || kind == null || !foreground || paused) return@launch
            val credentials = account ?: return@launch
            val items = sources[kind] ?: return@launch
            prepare(credentials, kind, items, token)
        }
    }

    private suspend fun prepare(credentials: ServerCredentials, kind: CatalogKind, items: List<CatalogItem>, token: Long) {
        val cache = store(requireNotNull(liveCategoryScope(credentials)), kind)
        cache.load()
        if (cache.error.value != null) { mutable.value = mutable.value.copy(error = cache.error.value); return }
        val ordered = withContext(Dispatchers.Default) { items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
        val pending = linkedMapOf<Int, DesktopGenreRecord>()
        suspend fun flush(): Boolean {
            if (pending.isEmpty()) return true
            val batch = pending.toMap()
            val ok = cache.update { old ->
                old.copy(records = old.records + batch.filter { (id, record) ->
                    (old.records[id]?.updatedAt ?: 0) <= record.updatedAt
                })
            }
            if (ok) pending.clear()
            return ok
        }
        suspend fun progress(message: String? = null, publishResults: Boolean = false) {
            if (token != revision) return
            val records = cache.state.value.records + pending
            val actual = withContext(Dispatchers.Default) { items.mapNotNull { records[it.id] } }
            mutable.value = DesktopPreparationState(kind, items.size,
                actual.count { it.status != "FAILED" }, actual.count { it.genre != null },
                actual.count { it.status == "NO_DATA" }, actual.count { it.status == "FAILED" },
                running = true, speed = speed, message = message)
            if (publishResults) publish(kind, records)
        }
        var batchSize = 0
        var batchStarted = System.nanoTime()
        try {
            while (currentCoroutineContext().isActive && token == revision) {
                val now = System.currentTimeMillis()
                val records = cache.state.value.records + pending
                val candidates = withContext(Dispatchers.Default) { ordered.filter { item ->
                    val record = records[item.id]
                    record == null || record.status == "NO_DATA" && now - record.updatedAt >= 7L * 24 * 60 * 60 * 1000 ||
                        record.status == "FAILED" && record.attempts < 5
                }
                }
                val next = candidates.firstOrNull { (records[it.id]?.nextEligibleAt ?: 0) <= now }
                if (next == null) {
                    if (!flush()) { mutable.value = mutable.value.copy(error = cache.error.value); break }
                    progress(if (candidates.isEmpty()) {
                        if (records.values.any { it.status == "FAILED" }) "Há falhas pendentes; use Tentar falhas novamente" else "Preparação concluída"
                    } else "Aguardando nova tentativa", publishResults = true)
                    if (candidates.isEmpty()) break
                    delay((candidates.minOf { records[it.id]?.nextEligibleAt ?: now } - now).coerceIn(1000, 60_000))
                    continue
                }
                progress("Preparando ${next.name}")
                val record = try {
                    val raw = withContext(Dispatchers.IO) {
                        if (kind == CatalogKind.MOVIES) client.movieInfo(credentials, next.id).info?.genre
                        else client.seriesInfo(credentials, next.id).info?.genre
                    }
                    currentCoroutineContext().ensureActive()
                    val genre = raw?.take(4096)?.takeIf { GenreUtils.extractNormalizedGenres(it).isNotEmpty() }
                    DesktopGenreRecord(next.id, genre, if (genre == null) "NO_DATA" else "SUCCESS", System.currentTimeMillis())
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    val attempts = (records[next.id]?.attempts ?: 0) + 1
                    val wait = min(300_000L, 5000L * (1L shl min(attempts, 6))) + Random.nextLong(1000)
                    DesktopGenreRecord(next.id, status = "FAILED", updatedAt = System.currentTimeMillis(), attempts = attempts,
                        nextEligibleAt = System.currentTimeMillis() + wait)
                }
                pending[next.id] = record
                progress()
                batchSize++
                if (batchSize >= 10 || System.nanoTime() - batchStarted >= 30_000_000_000L) {
                    if (!flush()) { mutable.value = mutable.value.copy(error = cache.error.value); break }
                    progress("Pausa entre lotes", publishResults = true)
                    delay(speed.batchCooldownMs)
                    batchSize = 0; batchStarted = System.nanoTime()
                } else delay(speed.itemDelayMs)
            }
        } finally {
            withContext(NonCancellable) { flush() }
            if (token == revision) mutable.value = mutable.value.copy(running = false,
                error = cache.error.value ?: mutable.value.error)
        }
    }
}
