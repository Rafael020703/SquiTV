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
import java.nio.file.attribute.BasicFileAttributes
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
    val seriesTitle: String? = null, val seasonNumber: Int? = null, val episodeNumber: String? = null,
    val failure: String? = null)

@Serializable
internal data class DesktopDownloadData(val schema: Int = 1, val entries: List<DesktopDownload> = emptyList())
internal data class DesktopDownloadProgress(val key: String, val bytes: Long, val total: Long)

/** Direct media only; a user-requested season queues episodes, one transfer at a time. */
internal class DesktopDownloads(directory: Path, private val scope: CoroutineScope) : AutoCloseable {
    private companion object {
        val fileKeyPattern = Regex("[a-f0-9-]{36}")
        val accountPattern = Regex("[a-f0-9]{64}")
        val extensionPattern = Regex("[a-z0-9]{1,10}")
    }

    private val root = directory.resolve("downloads").toAbsolutePath().normalize()
    private val files = root.resolve("files")
    private val store = DesktopJsonFile(root.resolve("index.json"), DesktopDownloadData(),
        DesktopDownloadData.serializer(), 2 * 1024 * 1024, "a lista de downloads") { data ->
        require(data.schema == 1 && data.entries.size <= 1000 && data.entries.distinctBy { it.key }.size == data.entries.size)
        require(data.entries.distinctBy { Triple(it.account, it.kind, it.streamId) }.size == data.entries.size)
        require(data.entries.all {
            it.key.matches(fileKeyPattern) && it.account.matches(accountPattern) &&
                it.streamId > 0 && it.title.length in 1..1024 && it.extension.matches(extensionPattern) &&
                it.bytes >= 0 && it.total >= -1 && it.createdAt > 0 && (it.failure?.length ?: 0) <= 160 &&
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
            if (it.status == DesktopDownloadStatus.DOWNLOADING || it.status == DesktopDownloadStatus.QUEUED)
                it.copy(status = DesktopDownloadStatus.INTERRUPTED) else it
        }) }
        mutableLoaded.value = true
    }
    private val client = OkHttpClient.Builder().dns(rsv.squitv.shared.network.AppDns).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
        .also(rsv.squitv.shared.network.AppDns::register)
    @Volatile private var boundAccount: String? = null
    @Volatile private var job: Job? = null
    @Volatile private var stopping = false
    private var activeTransfer: Pair<String, Job>? = null

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
        ?.takeIf { it.matches(extensionPattern) } ?: "mp4"

    private fun enqueue(account: ServerCredentials, requests: List<DesktopDownload>) {
        val accountKey = liveCategoryScope(account) ?: return
        if (requests.isEmpty()) return
        scope.launch {
            commands.withLock {
                ready.await()
                if (stopping || boundAccount != accountKey) return@withLock
                if (job?.isCancelled == true) job?.join()
                var added = 0
                var unsupported = 0
                var limitReached = false
                val saved = withContext(Dispatchers.IO) {
                    store.update { data ->
                        if (boundAccount != accountKey || stopping) return@update data
                        val entries = data.entries.associateByTo(linkedMapOf()) { Triple(it.account, it.kind, it.streamId) }
                        for (request in requests) {
                            if (request.extension == "m3u8" || request.extension == "mpd") { unsupported++; continue }
                            val identity = Triple(accountKey, request.kind, request.streamId)
                            val previous = entries[identity]
                            if ((previous?.status == DesktopDownloadStatus.QUEUED || previous?.status == DesktopDownloadStatus.DOWNLOADING) &&
                                job?.isCompleted == false) continue
                            if (previous != null && completedFile(previous) != null) continue
                            entries.remove(identity)
                            entries[identity] = request.copy(key = previous?.key ?: request.key, failure = null)
                            added++
                        }
                        limitReached = entries.size > 1000
                        if (limitReached) data else data.copy(entries = entries.values.toList())
                    }
                }
                if (!saved || boundAccount != accountKey || stopping) return@withLock
                if (limitReached) {
                    mutableNotice.value = "O limite de 1.000 downloads foi atingido. Exclua itens antes de adicionar esta seleção."
                    return@withLock
                }
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
            val worker = this
            try {
                while (isActive && !stopping && boundAccount == accountKey) {
                    val (entry, transfer) = commands.withLock {
                        if (stopping || boundAccount != accountKey) return@withLock null
                        val next = state.value.entries.firstOrNull { it.account == accountKey && it.status == DesktopDownloadStatus.QUEUED }
                        if (next == null) {
                            activeTransfer = null
                            drained = true
                            job = null
                            return@withLock null
                        }
                        if (!store.update { data -> data.copy(entries = data.entries.map {
                            if (it.key == next.key) it.copy(status = DesktopDownloadStatus.DOWNLOADING) else it
                        }) }) return@withLock null
                        // Start cleanup ownership before cancellation can leave this item marked DOWNLOADING.
                        val transfer = worker.async(start = CoroutineStart.UNDISPATCHED) { download(account, next) }
                        activeTransfer = next.key to transfer
                        next to transfer
                    } ?: break
                    try { transfer.await() }
                    catch (_: CancellationException) { ensureActive() } // Removing one item cancels only its transfer.
                    if (state.value.entries.any { it.key == entry.key && it.status == DesktopDownloadStatus.DOWNLOADING }) break
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
                activeTransfer?.takeIf { it.first == key }?.second?.cancelAndJoin()
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

    suspend fun localFile(key: String, account: String): Path? = withContext(Dispatchers.IO) {
        ready.await()
        commands.withLock {
            val entry = state.value.entries.firstOrNull { it.key == key && it.account == account &&
                it.status == DesktopDownloadStatus.COMPLETED } ?: return@withLock null
            val file = completedFile(entry)
            if (file == null) {
                store.update { data -> data.copy(entries = data.entries.map {
                    if (it == entry) it.copy(status = DesktopDownloadStatus.FAILED,
                        bytes = 0, failure = "Arquivo ausente ou incompleto. Reinicie o download.") else it
                }) }
            }
            file
        }
    }

    private fun path(entry: DesktopDownload, partial: Boolean): Path {
        val result = files.resolve("${entry.key}.media" + if (partial) ".part" else "").normalize()
        check(result.parent == files)
        return result
    }

    private fun completedFile(entry: DesktopDownload): Path? {
        if (entry.status != DesktopDownloadStatus.COMPLETED) return null
        val file = path(entry, false)
        return try {
            val attributes = Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            file.takeIf { attributes.isRegularFile && attributes.size() == entry.bytes }
        } catch (_: IOException) { null }
    }

    private suspend fun download(account: ServerCredentials, entry: DesktopDownload) {
        var outcome = DesktopDownloadStatus.INTERRUPTED
        var failure: String? = null
        mutableProgress.value = DesktopDownloadProgress(entry.key, 0, -1)
        try {
            currentCoroutineContext().ensureActive()
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
                    withContext(NonCancellable) {
                        val partial = path(entry, true)
                        val destination = path(entry, false)
                        try { Files.move(partial, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                        catch (_: AtomicMoveNotSupportedException) { Files.move(partial, destination, StandardCopyOption.REPLACE_EXISTING) }
                        check(store.update { data -> data.copy(entries = data.entries.map {
                            if (it.key == entry.key) it.copy(status = DesktopDownloadStatus.COMPLETED,
                                bytes = result.bytes, total = result.bytes, failure = null) else it
                        }) })
                        outcome = DesktopDownloadStatus.COMPLETED
                    }
                    return
                }
            }
            throw TransferFailure("O servidor excedeu o limite de redirecionamentos.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            outcome = DesktopDownloadStatus.FAILED
            failure = if (e is TransferFailure) e.message
                else "Não foi possível baixar. Confira a conexão, o acesso do servidor e o espaço livre."
        } finally {
            withContext(NonCancellable) {
                if (outcome != DesktopDownloadStatus.COMPLETED) {
                    if (!Files.isSymbolicLink(files) && !Files.isSymbolicLink(root))
                        runCatching { Files.deleteIfExists(path(entry, true)) }
                    store.update { data -> data.copy(entries = data.entries.map {
                        if (it.key == entry.key && it.status == DesktopDownloadStatus.DOWNLOADING)
                            it.copy(status = outcome, bytes = 0, total = -1, failure = failure) else it
                    }) }
                }
            }
            if (mutableProgress.value?.key == entry.key) mutableProgress.value = null
        }
    }

    private class TransferFailure(message: String) : IOException(message)
    private data class TransferResult(val redirect: HttpUrl? = null, val bytes: Long = 0)

    private suspend fun transfer(url: HttpUrl, entry: DesktopDownload): TransferResult {
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "SquiTV-Windows-Downloads").build())
        val finished = CompletableDeferred<Unit>()
        try {
            return suspendCancellableCoroutine { continuation ->
                try {
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            try { if (continuation.isActive) continuation.resumeWithException(e) } finally { finished.complete(Unit) }
                        }
                        override fun onResponse(call: Call, response: Response) {
                            try {
                                val result = response.use {
                                    if (it.code in setOf(301, 302, 303, 307, 308)) return@use TransferResult(
                                        redirect = it.header("Location")?.let(url::resolve) ?: error("Missing redirect"))
                                    if (it.code != 200) throw TransferFailure("O servidor respondeu com HTTP ${it.code}.")
                                    val type = it.body.contentType()?.toString().orEmpty().lowercase()
                                    if (type.startsWith("text/") || type.contains("json") || type.contains("mpegurl") || type.contains("dash+xml"))
                                        throw TransferFailure("O servidor retornou uma resposta que não é um arquivo de vídeo.")
                                    val total = it.body.contentLength()
                                    check(total != 0L)
                                    if (total > 0 && total > Files.getFileStore(files).usableSpace - 64L * 1024 * 1024)
                                        throw TransferFailure("Espaço insuficiente para salvar este vídeo.")
                                    val partial = path(entry, true)
                                    check(!Files.isSymbolicLink(partial))
                                    val count = it.body.byteStream().use { input ->
                                        val prefix = input.readNBytes(128)
                                        val text = prefix.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t').lowercase()
                                        if (listOf("<html", "<!doctype", "<?xml", "<mpd", "#extm3u", "{\"", "[{", "[\"").any(text::startsWith))
                                            throw TransferFailure("O servidor retornou uma página de erro ou playlist em vez do vídeo.")
                                        var count = prefix.size.toLong()
                                        var reported = 0L
                                        Files.newOutputStream(partial).use { output ->
                                            output.write(prefix)
                                            val buffer = ByteArray(64 * 1024)
                                            while (true) {
                                                if (call.isCanceled()) throw IOException()
                                                val n = input.read(buffer)
                                                if (n < 0) break
                                                count += n
                                                check(total < 0 || count <= total)
                                                output.write(buffer, 0, n)
                                                val now = System.nanoTime()
                                                if (now - reported >= 250_000_000L) {
                                                    mutableProgress.value = DesktopDownloadProgress(entry.key, count, total)
                                                    reported = now
                                                }
                                            }
                                        }
                                        count
                                    }
                                    if (call.isCanceled()) throw IOException()
                                    if (count == 0L || total >= 0 && count != total)
                                        throw TransferFailure("O servidor enviou um arquivo vazio ou incompleto.")
                                    TransferResult(bytes = count)
                                }
                                if (continuation.isActive) continuation.resume(result)
                            } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                            finally { finished.complete(Unit) }
                        }
                    })
                } catch (e: Exception) {
                    finished.complete(Unit)
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
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
