package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rsv.squitv.desktop.data.XtreamClient
import rsv.squitv.shared.xtream.*

internal data class DesktopLiveIndexState(val accountScope: String? = null, val channels: List<LiveChannel> = emptyList(),
    val loaded: Boolean = false, val loading: Boolean = false, val error: String? = null)

/** One session index for category counts and Multi-view. Loaded only on explicit catalog access. */
internal class DesktopLiveIndex(private val client: XtreamClient, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(DesktopLiveIndexState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var revision = 0L
    fun load(account: ServerCredentials, force: Boolean = false) {
        val key = liveCategoryScope(account) ?: return
        if (mutable.value.accountScope != key) reset()
        if (!force && (mutable.value.loading || mutable.value.loaded)) return
        job?.cancel()
        val token = ++revision
        mutable.value = mutable.value.copy(accountScope = key, loaded = if (force) false else mutable.value.loaded, loading = true, error = null)
        job = scope.launch {
            try {
                val channels = withContext(Dispatchers.IO) { client.channels(account, null).distinctBy { it.id } }
                ensureActive()
                if (token == revision) mutable.value = DesktopLiveIndexState(key, channels, loaded = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                ensureActive()
                if (token == revision) mutable.value = mutable.value.copy(loading = false,
                    error = "Não foi possível carregar o índice de canais. Contagens indisponíveis; tente novamente.")
            }
        }
    }
    fun cancel() { ++revision; job?.cancel(); mutable.value = mutable.value.copy(loading = false) }
    fun reset() { cancel(); mutable.value = DesktopLiveIndexState() }
}
