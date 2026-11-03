package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import rsv.squitv.shared.xtream.OnDemandPlayback
import rsv.squitv.shared.xtream.ServerCredentials
import java.io.IOException
import java.nio.file.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
internal enum class DesktopDownloadStatus { QUEUED, DOWNLOADING, COMPLETED, INTERRUPTED, FAILED }

@Serializable
internal enum class DesktopDownloadKind { MOVIE, EPISODE }

/** No media URL, password or server credentials are persisted with downloaded content. */
@Serializable
internal data class DesktopDownload(val key: String, val account: String, val streamId: Int,
    val title: String, val extension: String, val status: DesktopDownloadStatus,
    val bytes: Long = 0, val total: Long = -1, val createdAt: Long = System.currentTimeMillis(),
    val kind: DesktopDownloadKind = DesktopDownloadKind.MOVIE, val seriesId: Int? = null,
    val seriesTitle: String? = null, val seasonNumber: Int? = null, val episodeNumber: String? = null)

@Serializable
internal data class DesktopDownloadData(val schema: Int = 1, val entries: List<DesktopDownload> = emptyList())
internal data class DesktopDownloadProgress(val key: String, val bytes: Long, val total: Long)

/** Direct media only; a user-requested season queues episodes, one transfer at a time. */
internal class DesktopDownloads(directory: Path, private val scope: CoroutineScope) : AutoCloseable {
    private val root = directory.resolve("downloads").toAbsolutePath().normalize()
    private val files = root.resolve("files")
    private val store = DesktopJsonFile(root.resolve("index.json"), DesktopDownloadData(),
        DesktopDownloadData.serializer(), 2 * 1024 * 1024, "a lista de downloads") { data ->
        require(data.schema == 1 && data.entries.size <= 1000 && data.entries.distinctBy { it.key }.size == data.entries.size)
        require(data.entries.distinctBy { Triple(it.account, it.kind, it.streamId) }.size == data.entries.size)
        require(data.entries.all {
            it.key.matches(Regex("[a-f0-9-]{36}")) && it.account.matches(Regex("[a-f0-9]{64}")) &&
                it.streamId > 0 && it.title.length in 1..1024 && it.extension.matches(Regex("[a-z0-9]{1,10}")) &&
                it.bytes >= 0 && it.total >= -1 && it.createdAt > 0 &&
                (it.status != DesktopDownloadStatus.COMPLETED || it.bytes > 0 && it.total == it.bytes) &&
                (if (it.kind == DesktopDownloadKind.MOVIE) it.seriesId == null && it.seriesTitle == null && it.seasonNumber == null && it.episodeNumber == null
                 else it.seriesId != null && it.seriesId > 0 && !it.seriesTitle.isNullOrBlank() && it.seriesTitle.length <= 1024 &&
                     it.seasonNumber != null && it.seasonNumber >= 0 && (it.episodeNumber == null || it.episodeNumber.length <= 64))
        })
    }
    val state = store.state
    val error = store.error
    private val mutableProgress = MutableStateFlow<DesktopDownloadProgress?>(null)
    val progress = mutableProgress.asStateFlow()
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice = mutableNotice.asStateFlow()
    private val mutableLoaded = MutableStateFlow(false)
    val loaded = mutableLoaded.asStateFlow()
    private val commands = Mutex()
    private val ready = scope.async {
        store.load()
        store.update { data -> data.copy(entries = data.entries.map {
            if (it.status in setOf(DesktopDownloadStatus.DOWNLOADING, DesktopDownloadStatus.QUEUED)) it.copy(status = DesktopDownloadStatus.INTERRUPTED) else it
        }) }
        mutableLoaded.value = true
    }
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    @Volatile private var boundAccount: String? = null
    @Volatile private var job: Job? = null
    @Volatile private var stopping = false

    fun bind(account: ServerCredentials?) {
        val key = liveCategoryScope(account)
        val previous = boundAccount
        if (previous != key) {
            job?.cancel(); boundAccount = key; mutableNotice.value = null
            if (previous != null) scope.launch { ready.await(); interruptQueued(previous, onlyIfUnbound = true) }
        }
    }

    fun start(account: ServerCredentials, item: CatalogItem) {
        val accountKey = liveCategoryScope(account) ?: return
        enqueue(account, listOf(DesktopDownload(UUID.randomUUID().toString(), accountKey, item.id,
            item.name.ifBlank { "Filme ${item.id}" }.take(1024), extension(item.extension), DesktopDownloadStatus.QUEUED)))
    }

    fun startEpisodes(account: ServerCredentials, series: CatalogItem, season: Int,
        episodes: List<rsv.squitv.data.model.XtreamEpisode>) {
        val accountKey = liveCategoryScope(account) ?: return
        if (season < 0 || series.id <= 0 || episodes.size > 1000) return
        val requests = episodes.mapNotNull { episode ->
            val id = episode.id?.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val number = episode.episodeNum?.take(64)
            val title = "${series.name} · T$season · ${episode.title ?: "Episódio ${number.orEmpty()}"}".take(1024)
            DesktopDownload(UUID.randomUUID().toString(), accountKey, id, title, extension(episode.container_extension),
                DesktopDownloadStatus.QUEUED, kind = DesktopDownloadKind.EPISODE, seriesId = series.id,
                seriesTitle = series.name.ifBlank { "Série ${series.id}" }.take(1024), seasonNumber = season, episodeNumber = number)
        }.distinctBy { it.streamId }
        enqueue(account, requests)
    }

    fun retry(account: ServerCredentials, entry: DesktopDownload) {
        if (entry.account != liveCategoryScope(account)) return
        enqueue(account, listOf(entry.copy(status = DesktopDownloadStatus.QUEUED, bytes = 0, total = -1)))
    }

    private fun extension(raw: String?): String = raw?.trim()?.trimStart('.')?.lowercase(java.util.Locale.ROOT)
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "mp4"

    private fun enqueue(account: ServerCredentials, requests: List<DesktopDownload>) {
        val accountKey = liveCategoryScope(account) ?: return
        if (requests.isEmpty()) return
        scope.launch {
            commands.withLock {
                ready.await()
                if (stopping || boundAccount != accountKey) return@withLock
                if (job?.isCancelled == true) job?.join()
                val entries = state.value.entries.toMutableList()
                var added = 0
                var unsupported = 0
                for (request in requests) {
                    if (request.extension in setOf("m3u8", "mpd")) { unsupported++; continue }
                    val previous = entries.firstOrNull { it.account == accountKey && it.kind == request.kind && it.streamId == request.streamId }
                    if (previous?.status in setOf(DesktopDownloadStatus.QUEUED, DesktopDownloadStatus.DOWNLOADING) && job?.isCompleted == false) continue
                    if (previous?.status == DesktopDownloadStatus.COMPLETED && withContext(Dispatchers.IO) { completedFile(previous) } != null) continue
                    entries.removeAll { it.key == previous?.key }
                    entries.add(request.copy(key = previous?.key ?: request.key))
                    added++
                }
                if (entries.size > 1000) { mutableNotice.value = "O limite de 1.000 downloads foi atingido. Exclua itens antes de adicionar esta seleção."; return@withLock }
                if (boundAccount != accountKey || stopping) return@withLock
                if (added > 0 && !store.update { it.copy(entries = entries) }) return@withLock
                mutableNotice.value = when {
                    unsupported > 0 -> "${if (added > 0) "$added adicionado(s). " else ""}Formatos HLS/DASH não são suportados para download no Windows."
                    added == 0 -> "O conteúdo já está salvo ou na fila de downloads."
                    added > 1 -> "$added episódios adicionados à fila."
                    else -> null
                }
                if (job?.isCompleted != false && boundAccount == accountKey && !stopping) startWorker(account, accountKey)
            }
        }
    }

    private fun startWorker(account: ServerCredentials, accountKey: String) {
        job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            var drained = false
            try {
                while (isActive && !stopping && boundAccount == accountKey) {
                    val entry = commands.withLock {
                        if (stopping || boundAccount != accountKey) return@withLock null
                        val next = state.value.entries.firstOrNull { it.account == accountKey && it.status == DesktopDownloadStatus.QUEUED }
                        if (next == null) { drained = true; job = null; return@withLock null }
                        if (!store.update { data -> data.copy(entries = data.entries.map {
                            if (it.key == next.key) it.copy(status = DesktopDownloadStatus.DOWNLOADING) else it
                        }) }) return@withLock null
                        mutableProgress.value = DesktopDownloadProgress(next.key, 0, -1)
                        next
                    } ?: break
                    download(account, entry)
                    if (state.value.entries.firstOrNull { it.key == entry.key }?.status != DesktopDownloadStatus.COMPLETED) break
                }
            } finally { if (!drained) withContext(NonCancellable) { interruptQueued(accountKey) } }
        }
        job?.start()
    }

    private suspend fun interruptQueued(account: String, onlyIfUnbound: Boolean = false) {
        store.update { data -> data.copy(entries = data.entries.map {
            if (it.account == account && it.status == DesktopDownloadStatus.QUEUED && (!onlyIfUnbound || boundAccount != account))
                it.copy(status = DesktopDownloadStatus.INTERRUPTED) else it
        }) }
    }

    fun interrupt() { job?.cancel() }

    fun remove(key: String) {
        scope.launch {
            commands.withLock {
                ready.await()
                val entry = state.value.entries.firstOrNull { it.key == key && it.account == boundAccount } ?: return@withLock
                if (mutableProgress.value?.key == key) { job?.cancelAndJoin() }
                try {
                    withContext(Dispatchers.IO) {
                        Files.deleteIfExists(path(entry, true)); Files.deleteIfExists(path(entry, false))
                    }
                    if (store.update { it.copy(entries = it.entries.filterNot { saved -> saved.key == key }) })
                        mutableNotice.value = "Download excluído."
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutableNotice.value = "Não foi possível excluir o arquivo. Feche sua reprodução e tente novamente." }
            }
        }
    }

    suspend fun localFile(key: String, account: String): Pair<DesktopDownload, Path>? = withContext(Dispatchers.IO) {
        ready.await()
        val entry = state.value.entries.firstOrNull { it.key == key && it.account == account } ?: return@withContext null
        if (entry.status != DesktopDownloadStatus.COMPLETED) return@withContext null
        val file = completedFile(entry)
        if (file == null) {
            store.update { data -> data.copy(entries = data.entries.map {
                if (it.key == entry.key) it.copy(status = DesktopDownloadStatus.FAILED) else it
            }) }
            mutableNotice.value = "O arquivo salvo não está disponível ou está incompleto. Reinicie o download."
        }
        file?.let { entry to it }
    }

    private fun path(entry: DesktopDownload, partial: Boolean): Path {
        val result = files.resolve("${entry.key}.media" + if (partial) ".part" else "").normalize()
        check(result.parent == files)
        return result
    }

    private fun completedFile(entry: DesktopDownload): Path? = try {
        path(entry, false).takeIf { entry.status == DesktopDownloadStatus.COMPLETED &&
            Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) && Files.size(it) == entry.bytes }
    } catch (_: IOException) { null }

    private suspend fun download(account: ServerCredentials, entry: DesktopDownload) {
        var outcome = DesktopDownloadStatus.INTERRUPTED
        try {
            Files.createDirectories(files)
            check(!Files.isSymbolicLink(files) && !Files.isSymbolicLink(root))
            var url = OnDemandPlayback.address(account, entry.kind == DesktopDownloadKind.EPISODE, entry.streamId, entry.extension).toHttpUrl()
            val visited = mutableSetOf<HttpUrl>()
            repeat(6) {
                currentCoroutineContext().ensureActive()
                check(url.username.isEmpty() && url.password.isEmpty() && visited.add(url))
                val result = transfer(url, entry)
                if (result.redirect != null) {
                    check(!(url.isHttps && !result.redirect.isHttps))
                    url = result.redirect
                } else {
                    currentCoroutineContext().ensureActive()
                    check(store.update { data -> data.copy(entries = data.entries.map {
                        if (it.key == entry.key) it.copy(status = DesktopDownloadStatus.COMPLETED,
                            bytes = result.bytes, total = result.bytes) else it
                    }) })
                    outcome = DesktopDownloadStatus.COMPLETED
                    return
                }
            }
            error("Too many redirects")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            outcome = DesktopDownloadStatus.FAILED
            mutableNotice.value = "Não foi possível baixar o conteúdo. A fila foi interrompida. Confira a conexão, o espaço livre e o acesso do servidor."
        } finally {
            withContext(NonCancellable) {
                if (outcome != DesktopDownloadStatus.COMPLETED &&
                    state.value.entries.firstOrNull { it.key == entry.key }?.status != DesktopDownloadStatus.COMPLETED)
                    finishInterrupted(entry, outcome)
            }
            if (mutableProgress.value?.key == entry.key) mutableProgress.value = null
        }
    }

    private suspend fun finishInterrupted(entry: DesktopDownload, status: DesktopDownloadStatus) {
        val progress = mutableProgress.value?.takeIf { it.key == entry.key }
        store.update { data -> data.copy(entries = data.entries.map {
            if (it.key == entry.key) it.copy(status = status, bytes = progress?.bytes ?: 0, total = progress?.total ?: -1) else it
        }) }
    }

    private data class TransferResult(val redirect: HttpUrl? = null, val bytes: Long = 0)

    private suspend fun transfer(url: HttpUrl, entry: DesktopDownload): TransferResult {
        val finished = CompletableDeferred<Unit>()
        try {
            return suspendCancellableCoroutine { continuation ->
                val call = client.newCall(Request.Builder().url(url).header("User-Agent", "SquiTV-Windows-Downloads").build())
                continuation.invokeOnCancellation { call.cancel() }
                try { call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        try { if (continuation.isActive) continuation.resumeWithException(e) } finally { finished.complete(Unit) }
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val result = response.use {
                                if (it.code in setOf(301, 302, 303, 307, 308)) return@use TransferResult(
                                    redirect = it.header("Location")?.let(url::resolve) ?: error("Missing redirect"))
                                check(it.code == 200)
                                val type = it.body.contentType()?.toString().orEmpty().lowercase()
                                check(!type.startsWith("text/") && !type.contains("json") && !type.contains("mpegurl") && !type.contains("dash+xml"))
                                val total = it.body.contentLength()
                                check(total != 0L)
                                if (total > 0) check(total <= Files.getFileStore(files).usableSpace - 64L * 1024 * 1024)
                                val partial = path(entry, true)
                                check(!Files.isSymbolicLink(partial))
                                var count = 0L; var reported = 0L
                                it.body.byteStream().use { input -> Files.newOutputStream(partial).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        if (call.isCanceled()) throw IOException()
                                        val n = input.read(buffer); if (n < 0) break
                                        if (count == 0L) {
                                            val prefix = String(buffer, 0, minOf(n, 128), Charsets.ISO_8859_1).trimStart().lowercase()
                                            check(!prefix.startsWith("<html") && !prefix.startsWith("<!doctype") &&
                                                !prefix.startsWith("#extm3u") && !prefix.startsWith("<?xml"))
                                        }
                                        count += n; check(total < 0 || count <= total)
                                        output.write(buffer, 0, n)
                                        val now = System.nanoTime()
                                        if (now - reported >= 250_000_000L) {
                                            mutableProgress.value = DesktopDownloadProgress(entry.key, count, total); reported = now
                                        }
                                    }
                                } }
                                check(!call.isCanceled() && count > 0 && (total < 0 || count == total))
                                val destination = path(entry, false)
                                try { Files.move(partial, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                                catch (_: AtomicMoveNotSupportedException) { Files.move(partial, destination, StandardCopyOption.REPLACE_EXISTING) }
                                TransferResult(bytes = count)
                            }
                            if (continuation.isActive) continuation.resume(result)
                        } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                        finally { finished.complete(Unit) }
                    }
                }) } catch (e: Exception) { finished.complete(Unit); if (continuation.isActive) continuation.resumeWithException(e) }
            }
        } finally {
            // Wait for the canceled callback to close its file before retry/delete can reuse it.
            withContext(NonCancellable) { finished.await() }
        }
    }

    suspend fun stopAndFlush() {
        stopping = true
        commands.withLock { ready.await(); job?.cancelAndJoin() }
    }
    override fun close() {
        stopping = true; job?.cancel(); client.dispatcher.cancelAll()
        client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
    }
}
