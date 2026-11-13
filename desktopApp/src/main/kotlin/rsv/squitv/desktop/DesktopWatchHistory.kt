package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import rsv.squitv.desktop.player.PlayerStatus
import rsv.squitv.domain.usecase.WatchProgressUtils
import rsv.squitv.shared.account.CloudProgress
import java.nio.file.Path

@Serializable
internal data class DesktopWatchEntry(val account: String, val streamId: Int, val catalogId: Int,
    val series: Boolean, val title: String, val streamTitle: String, val cover: String?, val extension: String?,
    val positionMs: Long, val durationMs: Long, val lastWatched: Long, val season: Int? = null,
    val cloudManaged: Boolean = false) {
    val key: String get() = "$account/${if (series) "SERIES" else "MOVIES"}/$streamId"
    val progress: Float get() = (positionMs.toDouble() / durationMs.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    fun item() = CatalogItem(catalogId, title, cover, null, extension = if (series) null else extension)
}
@Serializable
internal data class DesktopWatchData(val schema: Int = 1, val entries: List<DesktopWatchEntry> = emptyList())

internal class DesktopWatchHistory(directory: Path, scope: CoroutineScope) {
    private val file = DesktopJsonFile(directory.resolve("watch-history.json"), DesktopWatchData(),
        DesktopWatchData.serializer(), 4 * 1024 * 1024, "o histórico") { data ->
        require(data.schema == 1 && data.entries.size <= 5000 && data.entries.distinctBy { it.key }.size == data.entries.size)
        require(data.entries.all {
            it.account.matches(Regex("[a-f0-9]{64}")) && it.streamId > 0 && it.catalogId > 0 &&
                it.title.length <= 1024 && it.streamTitle.length <= 2048 && (it.cover?.length ?: 0) <= 4096 &&
                (it.extension?.length ?: 0) <= 32 && it.positionMs >= 0 && it.durationMs > 0 && it.lastWatched > 0 &&
                (it.season == null || it.season >= 0)
        })
    }
    val state = file.state
    val error = file.error
    private val saves = Mutex()
    var onNewProgress: (suspend (String, CloudProgress) -> Unit)? = null
    private val lastSaved = mutableMapOf<String, Long>()
    init { scope.launch { file.load() } }

    suspend fun ready() { file.load() }

    fun continuing(account: String, series: Boolean? = null): List<DesktopWatchEntry> = state.value.entries
        .filter { it.account == account && (series == null || it.series == series) &&
            WatchProgressUtils.isPendingWatchProgress(it.positionMs, it.durationMs) }
        .sortedByDescending { it.lastWatched }.distinctBy { it.series to it.catalogId }

    fun latest(account: String, series: Boolean, catalogId: Int) = continuing(account, series)
        .firstOrNull { it.catalogId == catalogId }

    suspend fun resume(account: String, session: VodSession): DesktopWatchEntry? {
        ready()
        return state.value.entries.firstOrNull { it.account == account && it.series == session.series &&
            it.streamId == session.id && WatchProgressUtils.isPendingWatchProgress(it.positionMs, it.durationMs) }
    }

    suspend fun applyCloudProgress(account: String, boundAt: Long, rows: List<CloudProgress>) = saves.withLock {
        ready()
        if (!file.update { old ->
            val entries = old.entries.associateBy(DesktopWatchEntry::key).toMutableMap()
            for (row in rows) {
                val series = row.kind == "episode"
                val key = "$account/${if (series) "SERIES" else "MOVIES"}/${row.id}"
                val previous = entries[key]
                if (previous != null && previous.lastWatched > row.observedAt) continue
                val duration = row.durationMs
                if (row.deleted || row.completed) {
                    if (previous != null && (previous.cloudManaged || (boundAt > 0 && previous.lastWatched >= boundAt))) entries.remove(key)
                } else if (duration != null && duration > 0) {
                    val catalogId = row.catalogId ?: if (series) previous?.catalogId else row.id
                    if (catalogId == null) continue
                    val title = row.title?.takeIf(String::isNotBlank) ?: previous?.title ?: "#$catalogId"
                    entries[key] = DesktopWatchEntry(account, row.id, catalogId, series, title,
                        previous?.streamTitle ?: title, previous?.cover, previous?.extension,
                        row.positionMs, duration, row.observedAt, previous?.season, cloudManaged = true)
                }
            }
            old.copy(entries = entries.values.toList())
        }) throw java.io.IOException("Não foi possível aplicar o progresso no Windows.")
    }

    suspend fun record(account: String, session: VodSession, status: PlayerStatus, force: Boolean = false,
        observedAt: Long = System.currentTimeMillis()): Boolean = saves.withLock {
        if (status.sourceId != session.id || !account.matches(Regex("[a-f0-9]{64}"))) return@withLock true
        val complete = WatchProgressUtils.isVideoCompleted(status.timeMs, status.durationMs, status.ended)
        if (!complete && (status.timeMs < 5000 || status.durationMs <= 0)) return@withLock true
        val key = "$account/${if (session.series) "SERIES" else "MOVIES"}/${session.id}"
        val monotonic = System.nanoTime() / 1_000_000
        if (!force && !complete && monotonic - (lastSaved[key] ?: Long.MIN_VALUE / 2) < 10_000) return@withLock true
        ready()
        val previous = state.value.entries.firstOrNull { it.key == key }
        // Paused/ended player snapshots can repeat. Avoid revalidating the entire history
        // on each tick when no progress has changed, including forced pause/close saves.
        if (complete && previous == null || !complete && previous?.positionMs == status.timeMs &&
            previous.durationMs == status.durationMs) return@withLock true
        val entry = DesktopWatchEntry(account, session.id, session.catalogId, session.series,
            session.catalogTitle, session.title, session.catalogCover, session.extension,
            status.timeMs, status.durationMs, observedAt, session.episodes.getOrNull(session.episodeIndex)?.season)
        val saved = file.update { old ->
            val previous = old.entries.firstOrNull { it.key == key }
            if (previous != null && previous.lastWatched > observedAt) old
            else if (complete) old.copy(entries = old.entries.filterNot { it.key == key })
            else if (previous?.positionMs == entry.positionMs && previous.durationMs == entry.durationMs) old
            else old.copy(entries = old.entries.filterNot { it.key == key } + entry)
        }
        if (saved) {
            lastSaved[key] = monotonic
            val duration = status.durationMs
            if (duration in 1..172800000) {
                try {
                    onNewProgress?.invoke(account, CloudProgress(
                        kind = if (session.series) "episode" else "movie", id = session.id, catalogId = session.catalogId,
                        title = session.catalogTitle.filterNot(Char::isISOControl).take(180).trimEnd { Character.isHighSurrogate(it) },
                        positionMs = status.timeMs.coerceIn(0, duration), durationMs = duration,
                        completed = complete, observedAt = observedAt,
                    ))
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    // Playback and the existing local save remain available.
                }
            }
        }
        saved
    }
}
