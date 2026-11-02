package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rsv.squitv.desktop.data.*
import rsv.squitv.shared.xtream.*

data class DesktopState(
    val restoring: Boolean = true, val busy: Boolean = false,
    val credentials: ServerCredentials? = null, val savedCredentials: ServerCredentials? = null,
    val account: ServerAccount? = null, val categories: List<LiveCategory> = emptyList(),
    val selectedCategory: LiveCategory? = null, val channels: List<LiveChannel> = emptyList(),
    val loadingChannels: Boolean = false, val error: String? = null, val channelError: String? = null,
    val notice: String? = null
)

class DesktopController(
    private val client: XtreamClient, private val store: CredentialStore,
    private val scope: CoroutineScope
) : AutoCloseable {
    private val mutable = MutableStateFlow(DesktopState())
    val state = mutable.asStateFlow()
    private var connectionJob: Job? = null
    private var channelsJob: Job? = null

    init {
        scope.launch {
            val saved = try { withContext(Dispatchers.IO) { store.load() } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    mutable.value = mutable.value.copy(notice = "Não foi possível ler a conta salva. Entre novamente.")
                    null
                }
            mutable.value = mutable.value.copy(restoring = false, savedCredentials = saved)
        }
    }

    fun connect(address: String, username: String, password: String, remember: Boolean) {
        if (mutable.value.busy || mutable.value.restoring) return
        val baseUrl = try { ServerAddress.normalize(address) }
            catch (e: IllegalArgumentException) { mutable.value = mutable.value.copy(error = e.message); return }
        if (username.isBlank() || password.isEmpty()) {
            mutable.value = mutable.value.copy(error = "Preencha usuário e senha.")
            return
        }
        if (username.length > 1024 || password.length > 4096) {
            mutable.value = mutable.value.copy(error = "Usuário ou senha excedem o tamanho permitido.")
            return
        }
        val credentials = ServerCredentials(baseUrl, username.trim(), password)
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        connectionJob = scope.launch {
            try {
                val account = withContext(Dispatchers.IO) { client.login(credentials) }
                var notice: String? = null
                withContext(Dispatchers.IO) {
                    try { if (remember) store.save(credentials) else store.clear() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { notice = "Conectado, mas não foi possível atualizar a conta salva." }
                }
                mutable.value = DesktopState(restoring = false, credentials = credentials, account = account,
                    savedCredentials = if (remember) credentials else null, notice = notice, busy = true)
                try {
                    val categories = withContext(Dispatchers.IO) { client.categories(credentials) }
                    mutable.value = mutable.value.copy(categories = categories, busy = false)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, channelError = friendly(e)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, error = friendly(e)) }
        }
    }

    fun loadChannels(category: LiveCategory) {
        val credentials = mutable.value.credentials ?: return
        channelsJob?.cancel()
        mutable.value = mutable.value.copy(selectedCategory = category, channels = emptyList(),
            loadingChannels = true, channelError = null)
        channelsJob = scope.launch {
            try {
                val channels = withContext(Dispatchers.IO) { client.channels(credentials, category.id) }
                ensureActive()
                mutable.value = mutable.value.copy(channels = channels, loadingChannels = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(loadingChannels = false, channelError = friendly(e)) }
        }
    }

    fun cancelConnection() {
        connectionJob?.cancel()
        mutable.value = mutable.value.copy(busy = false)
    }

    fun disconnect() {
        connectionJob?.cancel()
        channelsJob?.cancel()
        val saved = mutable.value.savedCredentials
        mutable.value = DesktopState(restoring = false, savedCredentials = saved)
    }

    private fun friendly(error: Exception) = when (error) {
        is ServerProblem, is ServerAccountException -> error.message ?: "Não foi possível concluir a consulta."
        else -> "O servidor enviou dados inválidos ou indisponíveis. Tente novamente."
    }

    override fun close() { connectionJob?.cancel(); channelsJob?.cancel(); client.close() }
}
