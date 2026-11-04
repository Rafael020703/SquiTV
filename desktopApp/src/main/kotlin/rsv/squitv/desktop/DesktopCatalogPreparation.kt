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
import rsv.squitv.shared.catalog.PreparationProgress
import rsv.squitv.shared.catalog.PreparationBatchPolicy
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
    val message: String? = null, val error: String? = null,
    val inFlight: Int = 0, val estimatedRemainingMs: Long? = null)

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
    fun setSpeed(value: PreparationSpeed) { if (speed != value) { speed = value; restart() } }
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
        mutable.value = previous.copy(kind = kind, running = false, paused = paused || !foreground, speed = speed, inFlight = 0, estimatedRemainingMs = null,
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
        val ordered = withContext(Dispatchers.Default) { items.distinctBy { it.id }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
        val ready = java.util.ArrayDeque<CatalogItem>()
        val waiting = java.util.TreeMap<Long, MutableList<CatalogItem>>()
        val pending = linkedMapOf<Int, DesktopGenreRecord>()
        val estimator = PreparationProgress()
        val policy = PreparationBatchPolicy()
        val ttl = 7L * 24 * 60 * 60 * 1000
        fun resolved(record: DesktopGenreRecord?, now: Long) = record?.status == "SUCCESS" ||
            record?.status == "NO_DATA" && now - record.updatedAt < ttl
        val initial = withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val records = cache.state.value.records
            val eligible = ordered.filter { item ->
                val record = records[item.id]
                !resolved(record, now) && (record?.status != "FAILED" || record.attempts < 5)
            }
            Triple(eligible, ordered.count { resolved(records[it.id], now) },
                ordered.count { records[it.id]?.status == "FAILED" })
        }
        var done = initial.second
        var failed = initial.third
        var withGenre = withContext(Dispatchers.Default) { ordered.count { cache.state.value.records[it.id]?.genre != null } }
        var noData = done - withGenre
        initial.first.forEach { item ->
            val due = cache.state.value.records[item.id]?.nextEligibleAt ?: 0L
            if (due <= System.currentTimeMillis()) ready.addLast(item)
            else waiting.getOrPut(due) { mutableListOf() }.add(item)
        }
        var lastFlush = System.nanoTime()
        suspend fun flush(): Boolean {
            if (pending.isEmpty()) return true
            val batch = pending.toMap()
            val ok = cache.update { old -> old.copy(records = old.records + batch.filter { (id, record) ->
                (old.records[id]?.updatedAt ?: 0) <= record.updatedAt
            }) }
            if (ok) { pending.clear(); lastFlush = System.nanoTime() }
            return ok
        }
        fun progress(message: String? = null, inFlight: Int = 0) {
            if (token != revision) return
            mutable.value = DesktopPreparationState(kind, ordered.size, done, withGenre, noData, failed,
                running = true, speed = speed, message = message, inFlight = inFlight,
                estimatedRemainingMs = estimator.remainingMs(ordered.size, done, failed))
        }
        try {
            progress()
            while (currentCoroutineContext().isActive && token == revision) {
                val now = System.currentTimeMillis()
                while (waiting.isNotEmpty() && waiting.firstKey() <= now) ready.addAll(waiting.pollFirstEntry().value)
                if (ready.isEmpty()) {
                    if (!flush()) { mutable.value = mutable.value.copy(error = cache.error.value); break }
                    publish(kind, cache.state.value.records)
                    progress(if (waiting.isNotEmpty()) "Aguardando nova tentativa" else if (failed > 0)
                        "Há falhas pendentes; use Tentar falhas novamente" else "Preparação concluída")
                    if (waiting.isEmpty()) break
                    mutable.value = mutable.value.copy(estimatedRemainingMs = null)
                    delay((waiting.firstKey() - now).coerceIn(1000, 60_000))
                    continue
                }
                val batch = buildList {
                    repeat(minOf(policy.size(speed), ready.size)) { add(ready.removeFirst()) }
                }
                val records = batch.associate { it.id to (pending[it.id] ?: cache.state.value.records[it.id]) }
                val started = System.nanoTime()
                progress("Consultando ${batch.size} conteúdo(s)", batch.size)
                val results = coroutineScope {
                    batch.map { item -> async(Dispatchers.IO) {
                        try {
                            val response = withTimeoutOrNull(45_000L) {
                                listOf(if (kind == CatalogKind.MOVIES) client.movieInfo(credentials, item.id).info?.genre
                                else client.seriesInfo(credentials, item.id).info?.genre)
                            } ?: throw java.io.IOException("Tempo limite ao consultar metadados")
                            val raw = response.single()
                            currentCoroutineContext().ensureActive()
                            val genre = raw?.take(4096)?.takeIf { GenreUtils.extractNormalizedGenres(it).isNotEmpty() }
                            DesktopGenreRecord(item.id, genre, if (genre == null) "NO_DATA" else "SUCCESS", System.currentTimeMillis())
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            currentCoroutineContext().ensureActive()
                            val attempts = (records[item.id]?.attempts ?: 0) + 1
                            val wait = min(300_000L, 5000L * (1L shl min(attempts, 6))) + Random.nextLong(1000)
                            DesktopGenreRecord(item.id, status = "FAILED", updatedAt = System.currentTimeMillis(), attempts = attempts,
                                nextEligibleAt = System.currentTimeMillis() + wait)
                        }
                    } }.awaitAll()
                }
                currentCoroutineContext().ensureActive()
                if (token != revision || account != credentials) return
                results.forEach { record ->
                    val old = records[record.id]
                    if (resolved(old, now)) done--
                    if (old?.genre != null) withGenre--
                    if (old?.status == "NO_DATA" && resolved(old, now)) noData--
                    if (old?.status == "FAILED") failed--
                    if (resolved(record, System.currentTimeMillis())) done++
                    if (record.genre != null) withGenre++
                    if (record.status == "NO_DATA") noData++
                    if (record.status == "FAILED") {
                        failed++
                        if (record.attempts < 5) waiting.getOrPut(record.nextEligibleAt) { mutableListOf() }
                            .add(batch.first { it.id == record.id })
                    }
                    pending[record.id] = record
                }
                val interval = policy.complete(speed, results.any { it.status == "FAILED" })
                if (pending.size >= 25 || System.nanoTime() - lastFlush >= 10_000_000_000L) {
                    if (!flush()) { mutable.value = mutable.value.copy(error = cache.error.value); break }
                    publish(kind, cache.state.value.records)
                }
                estimator.record(results.count { it.status != "FAILED" }, (System.nanoTime() - started) / 1_000_000L, interval)
                progress(if (interval > speed.batchCooldownMs) "Ritmo reduzido após falha do servidor" else "Intervalo entre lotes")
                delay(interval)
            }
        } finally {
            withContext(NonCancellable) { flush() }
            if (token == revision) mutable.value = mutable.value.copy(running = false, inFlight = 0,
                error = cache.error.value ?: mutable.value.error)
        }
    }
}
