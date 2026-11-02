package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rsv.squitv.desktop.data.*
import rsv.squitv.shared.xtream.*

enum class DesktopPage { DASHBOARD, LIVE, MOVIES, SERIES, SETTINGS, UPDATES, SEARCH }
data class LiveSession(val sourceName: String, val channels: List<LiveChannel>, val index: Int) {
    val channel: LiveChannel get() = channels[index]
}

data class DesktopState(
    val restoring: Boolean = true, val busy: Boolean = false, val automaticLogin: Boolean = false,
    val credentials: ServerCredentials? = null, val savedCredentials: ServerCredentials? = null,
    val debugLoginDefaults: ServerCredentials? = null,
    val account: ServerAccount? = null, val categories: List<LiveCategory> = emptyList(),
    val selectedCategory: LiveCategory? = null, val channels: List<LiveChannel> = emptyList(),
    val loadingChannels: Boolean = false, val error: String? = null, val channelError: String? = null,
    val notice: String? = null,
    val selectedDns: String? = null, val dnsStatus: Map<String, Long> = emptyMap(),
    val testingDns: Boolean = false, val connectingServer: String? = null,
    val page: DesktopPage = DesktopPage.DASHBOARD, val playback: LiveSession? = null,
    val programmes: List<LiveProgramme> = emptyList(), val epgLoading: Boolean = false,
    val epgUnavailable: Boolean = false
)

class DesktopController(
    private val client: XtreamClient, private val store: CredentialStore,
    private val scope: CoroutineScope,
    autoProbe: Boolean = true
) : AutoCloseable {
    private val mutable = MutableStateFlow(DesktopState())
    val state = mutable.asStateFlow()
    internal val catalog = OnDemandController(client, scope)
    internal val search = DesktopSearchController(client, scope)
    private var connectionJob: Job? = null
    private var channelsJob: Job? = null
    private var dnsJob: Job? = null
    private var epgJob: Job? = null
    private val credentialWrites = Mutex()
    private val dnsProbe = DnsProbe()
    val availableDns: List<String> = ProviderServers.addresses.map(ServerAddress::normalize)

    init {
        scope.launch {
            val (saved, reconnect, debugDefaults) = try { withContext(Dispatchers.IO) {
                Triple(store.load(), store.allowsAutomaticLogin(), store.loadDebugDefaults())
            } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    mutable.value = mutable.value.copy(notice = "Não foi possível ler a conta salva. Entre novamente.")
                    Triple(null, false, null)
                }
            mutable.value = mutable.value.copy(restoring = false, savedCredentials = saved, debugLoginDefaults = debugDefaults,
                selectedDns = saved?.baseUrl?.let { url -> availableDns.firstOrNull { it == url } })
            if (saved != null && reconnect) connect(
                saved.baseUrl.takeUnless { it in availableDns }.orEmpty(), saved.username, saved.password,
                remember = true, automatic = true)
        }
        if (autoProbe) testDns()
    }

    fun selectDns(address: String?) {
        if (mutable.value.busy) return
        if (address == null || address in availableDns) mutable.value = mutable.value.copy(selectedDns = address)
    }

    fun testDns() {
        if (mutable.value.testingDns) return
        mutable.value = mutable.value.copy(testingDns = true, dnsStatus = emptyMap())
        dnsJob = scope.launch {
            try {
                availableDns.map { address -> async {
                    val latency = dnsProbe.latency(address)
                    mutable.value = mutable.value.copy(dnsStatus = mutable.value.dnsStatus + (address to latency))
                } }.awaitAll()
            } finally { mutable.value = mutable.value.copy(testingDns = false) }
        }
    }

    fun connect(address: String, username: String, password: String, remember: Boolean, automatic: Boolean = false) {
        if (mutable.value.busy || mutable.value.restoring) return
        val preferred = mutable.value.selectedDns ?: mutable.value.dnsStatus.filterValues { it >= 0 }.minByOrNull { it.value }?.key
        val candidates = try { ProviderServers.loginCandidates(address, preferred) }
            catch (e: IllegalArgumentException) { mutable.value = mutable.value.copy(error = e.message); return }
        if (username.isBlank() || password.isEmpty()) {
            mutable.value = mutable.value.copy(error = "Preencha usuário e senha.")
            return
        }
        if (username.length > 1024 || password.length > 4096) {
            mutable.value = mutable.value.copy(error = "Usuário ou senha excedem o tamanho permitido.")
            return
        }
        mutable.value = mutable.value.copy(busy = true, automaticLogin = automatic, error = null, notice = null)
        connectionJob = scope.launch {
            try {
                var connected: Pair<ServerCredentials, ServerAccount>? = null
                var lastFailure: Exception? = null
                for (baseUrl in candidates) {
                    ensureActive()
                    mutable.value = mutable.value.copy(connectingServer = baseUrl)
                    val credentials = ServerCredentials(baseUrl, username.trim(), password)
                    try {
                        val account = withTimeoutOrNull(15_000L) {
                            withContext(Dispatchers.IO) { client.login(credentials) }
                        } ?: throw ServerProblem("O servidor não respondeu ao login no prazo esperado.")
                        connected = credentials to account
                        break
                    } catch (e: CancellationException) { throw e }
                    catch (e: ServerAccountException) { throw e }
                    catch (e: Exception) { lastFailure = e }
                }
                val (credentials, account) = connected ?: throw (lastFailure ?: ServerProblem("Nenhum DNS respondeu ao login."))
                search.reset()
                var notice: String? = null
                credentialWrites.withLock { withContext(Dispatchers.IO) {
                    try { if (remember) store.save(credentials) else store.clear() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { notice = "Conectado, mas não foi possível atualizar a conta salva." }
                } }
                mutable.value = mutable.value.copy(restoring = false, credentials = credentials, account = account,
                    savedCredentials = if (remember) credentials else null, notice = notice, busy = true,
                    connectingServer = null, error = null, categories = emptyList(), channels = emptyList(),
                    selectedCategory = null, channelError = null, automaticLogin = false)
                try {
                    val categories = withContext(Dispatchers.IO) { client.categories(credentials) }
                    mutable.value = mutable.value.copy(categories = categories, busy = false)
                    if (mutable.value.page == DesktopPage.LIVE && mutable.value.selectedCategory == null)
                        categories.firstOrNull()?.let(::loadChannels)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, channelError = friendly(e)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, automaticLogin = false,
                connectingServer = null, error = friendly(e)) }
        }
    }

    fun loadChannels(category: LiveCategory) {
        val credentials = mutable.value.credentials ?: return
        if (mutable.value.selectedCategory == category && mutable.value.loadingChannels) return
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

    fun openLive() {
        search.leave()
        catalog.leave()
        mutable.value = mutable.value.copy(page = DesktopPage.LIVE)
        if (mutable.value.selectedCategory == null) mutable.value.categories.firstOrNull()?.let(::loadChannels)
    }

    fun home() {
        search.leave()
        catalog.leave()
        stopPlayback()
        mutable.value = mutable.value.copy(page = DesktopPage.DASHBOARD)
    }

    fun play(channel: LiveChannel, visibleChannels: List<LiveChannel>) {
        val current = mutable.value
        val category = current.selectedCategory ?: return
        if (current.credentials == null || current.loadingChannels || visibleChannels.isEmpty()) return
        // Freeze the exact visible source; never silently replace it with a global/provider list.
        val allowed = current.channels.associateBy { it.id }
        if (visibleChannels.any { allowed[it.id] != it }) return
        val source = visibleChannels.distinctBy { it.id }.toList()
        val index = source.indexOfFirst { it.id == channel.id }
        if (index < 0) return
        mutable.value = current.copy(playback = LiveSession(category.name, source, index))
        refreshEpg()
    }

    fun stepChannel(offset: Int) {
        val session = mutable.value.playback ?: return
        mutable.value = mutable.value.copy(playback = session.copy(
            index = LivePlayback.stepIndex(session.channels.size, session.index, offset)))
        refreshEpg()
    }

    fun selectPlaybackChannel(id: Int) {
        val session = mutable.value.playback ?: return
        val index = session.channels.indexOfFirst { it.id == id }
        if (index < 0 || index == session.index) return
        mutable.value = mutable.value.copy(playback = session.copy(index = index))
        refreshEpg()
    }

    fun stopPlayback() {
        epgJob?.cancel()
        mutable.value = mutable.value.copy(playback = null, programmes = emptyList(), epgLoading = false,
            epgUnavailable = false)
    }

    private fun refreshEpg() {
        epgJob?.cancel()
        val credentials = mutable.value.credentials ?: return
        val channelId = mutable.value.playback?.channel?.id ?: return
        mutable.value = mutable.value.copy(programmes = emptyList(), epgLoading = true, epgUnavailable = false)
        epgJob = scope.launch {
            while (isActive) {
                try {
                    val epg = withContext(Dispatchers.IO) { client.epg(credentials, channelId) }
                    ensureActive()
                    mutable.value = mutable.value.copy(programmes = epg, epgLoading = false, epgUnavailable = false)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    mutable.value = mutable.value.copy(epgLoading = false, epgUnavailable = true)
                }
                delay(60_000)
            }
        }
    }

    fun cancelConnection() {
        connectionJob?.cancel()
        mutable.value = mutable.value.copy(busy = false, automaticLogin = false, connectingServer = null)
    }

    fun disconnect() {
        search.reset()
        catalog.reset()
        stopPlayback()
        connectionJob?.cancel()
        channelsJob?.cancel()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            credentialWrites.withLock {
                try { withContext(Dispatchers.IO) { store.setAutomaticLogin(false) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutable.value = mutable.value.copy(notice = "Não foi possível desativar a entrada automática.") }
            }
        }
        val saved = mutable.value.savedCredentials
        mutable.value = mutable.value.copy(restoring = false, busy = false, credentials = null,
            account = null, categories = emptyList(), channels = emptyList(), selectedCategory = null,
            loadingChannels = false, error = null, channelError = null, notice = null, connectingServer = null,
            savedCredentials = saved, page = DesktopPage.DASHBOARD, automaticLogin = false)
    }

    private fun friendly(error: Exception) = when (error) {
        is ServerProblem, is ServerAccountException -> error.message ?: "Não foi possível concluir a consulta."
        else -> "O servidor enviou dados inválidos ou indisponíveis. Tente novamente."
    }

    override fun close() {
        search.reset()
        catalog.reset()
        connectionJob?.cancel(); channelsJob?.cancel(); dnsJob?.cancel(); epgJob?.cancel()
        dnsProbe.close(); client.close()
    }

    internal fun openCatalog(kind: CatalogKind) {
        val account = mutable.value.credentials ?: return
        search.leave()
        stopPlayback()
        mutable.value = mutable.value.copy(page = if (kind == CatalogKind.MOVIES) DesktopPage.MOVIES else DesktopPage.SERIES)
        catalog.open(kind, account)
    }

    fun openUpdates() {
        search.leave()
        catalog.leave(); stopPlayback()
        mutable.value = mutable.value.copy(page = DesktopPage.UPDATES)
    }

    fun openSettings() {
        search.leave()
        catalog.leave(); stopPlayback()
        mutable.value = mutable.value.copy(page = DesktopPage.SETTINGS)
    }

    internal fun openSearch() {
        val account = mutable.value.credentials ?: return
        catalog.leave(); stopPlayback()
        search.open(account)
        mutable.value = mutable.value.copy(page = DesktopPage.SEARCH)
    }

    internal fun openSearchResult(item: DesktopSearchItem) {
        val current = mutable.value
        val account = current.credentials ?: return
        val results = search.state.value.results
        if (current.page != DesktopPage.SEARCH || item !in results) return
        search.leave()
        if (item.type == DesktopSearchType.LIVE) {
            val source = results.mapNotNull { it.channel }.distinctBy { it.id }.toList()
            val index = source.indexOfFirst { it == item.channel }
            if (index < 0) return
            // Only the visible live search results, never a category/global fallback.
            mutable.value = current.copy(playback = LiveSession("Busca · ${search.state.value.query.trim()}", source, index))
            refreshEpg()
        } else {
            val catalogItem = item.catalogItem ?: return
            val kind = when (item.type) {
                DesktopSearchType.MOVIES -> CatalogKind.MOVIES
                DesktopSearchType.SERIES -> CatalogKind.SERIES
                else -> return
            }
            catalog.openSearchDetail(kind, catalogItem, account)
        }
    }
}
