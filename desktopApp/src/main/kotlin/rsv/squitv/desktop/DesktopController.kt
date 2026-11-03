package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rsv.squitv.desktop.data.*
import rsv.squitv.shared.xtream.*
import rsv.squitv.domain.model.ChannelQuality

enum class DesktopPage { DASHBOARD, LIVE, MOVIES, SERIES, SETTINGS, UPDATES, SEARCH }
data class LiveSession(val sourceName: String, val channels: List<LiveChannel>, val index: Int,
    val sourceScope: String = "", val selectedStreamId: Int? = null) {
    val channel: LiveChannel get() = LiveChannelGroups.select(channels[index], selectedStreamId) ?: channels[index]
    val versions: List<LiveChannel> get() = LiveChannelGroups.visible(channels, sourceScope, true)
        .firstOrNull { it.containsStream(channel.id) }?.versions.orEmpty()
}

data class DesktopState(
    val restoring: Boolean = true, val busy: Boolean = false,
    val refreshingCatalog: Boolean = false, val catalogSyncError: String? = null, val automaticLogin: Boolean = false,
    val credentials: ServerCredentials? = null, val savedCredentials: ServerCredentials? = null,
    val debugLoginDefaults: ServerCredentials? = null,
    val account: ServerAccount? = null, val categories: List<LiveCategory> = emptyList(),
    val selectedCategory: LiveCategory? = null, val channels: List<LiveChannel> = emptyList(),
    val loadingChannels: Boolean = false, val channelsLoaded: Boolean = false,
    val error: String? = null, val channelError: String? = null,
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
    private var catalogSyncJob: Job? = null
    private var catalogSyncRevision = 0L
    private var groupLiveChannels = true
    private var preferredChannelQuality = ChannelQuality.AUTO
    private val backupJson = kotlinx.serialization.json.Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
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
                    selectedCategory = null, channelsLoaded = false, channelError = null, automaticLogin = false)
                try {
                    val categories = withContext(Dispatchers.IO) { client.categories(credentials) }
                    mutable.value = mutable.value.copy(categories = categories, busy = false)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    ensureActive()
                    mutable.value = mutable.value.copy(busy = false, channelError = friendly(e))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                mutable.value = mutable.value.copy(busy = false, automaticLogin = false,
                    connectingServer = null, error = friendly(e))
            }
        }
    }

    fun loadChannels(category: LiveCategory) = loadChannels(category, force = false)

    private fun loadChannels(category: LiveCategory, force: Boolean) {
        val credentials = mutable.value.credentials ?: return
        val current = mutable.value
        if (!force && current.selectedCategory == category && (current.loadingChannels || current.channelsLoaded && current.channelError == null)) return
        channelsJob?.cancel()
        mutable.value = mutable.value.copy(selectedCategory = category, channels = emptyList(),
            loadingChannels = true, channelsLoaded = false, channelError = null)
        channelsJob = scope.launch {
            try {
                val channels = withContext(Dispatchers.IO) { client.channels(credentials, category.id) }
                ensureActive()
                mutable.value = mutable.value.copy(channels = channels, loadingChannels = false, channelsLoaded = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                mutable.value = mutable.value.copy(loadingChannels = false, channelError = friendly(e))
            }
        }
    }

    internal fun clearLiveSelection() {
        channelsJob?.cancel()
        mutable.value = mutable.value.copy(selectedCategory = null, channels = emptyList(),
            loadingChannels = false, channelsLoaded = false, channelError = null)
    }

    fun openLive() {
        search.leave()
        catalog.leave()
        mutable.value = mutable.value.copy(page = DesktopPage.LIVE)
        // The live screen selects only after account preferences (hidden/order) are ready.
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
        val sourceScope = liveCategoryScope(current.credentials).orEmpty()
        val allowed = current.channels.associateBy { it.id }
        if (LiveChannelGroups.expand(visibleChannels).any { allowed[it.id] != it }) return
        val source = visibleChannels.distinctBy { it.id }.toList()
        val index = source.indexOfFirst { it.id == channel.id }
        if (index < 0) return
        mutable.value = current.copy(playback = LiveSession(category.name, source, index, sourceScope))
        refreshEpg()
    }

    fun stepChannel(offset: Int) {
        val session = mutable.value.playback ?: return
        val next = LivePlayback.stepIndex(session.channels.size, session.index, offset)
        if (next == session.index) return
        mutable.value = mutable.value.copy(playback = session.copy(
            index = next, selectedStreamId = null))
        refreshEpg()
    }

    fun selectPlaybackChannel(id: Int) {
        val session = mutable.value.playback ?: return
        val index = session.channels.indexOfFirst { it.id == id }
        if (index < 0 || index == session.index) return
        mutable.value = mutable.value.copy(playback = session.copy(index = index, selectedStreamId = null))
        refreshEpg()
    }

    internal fun configureLiveChannels(grouped: Boolean, preferred: ChannelQuality) {
        if (groupLiveChannels == grouped && preferredChannelQuality == preferred) return
        groupLiveChannels = grouped
        preferredChannelQuality = preferred
        search.configureLiveChannels(grouped, preferred)
        if (mutable.value.page == DesktopPage.SEARCH) search.resume()
        val session = mutable.value.playback ?: return
        val channels = LiveChannelGroups.visible(session.channels, session.sourceScope, grouped, preferred)
        val index = channels.indexOfFirst { it.containsStream(session.channel.id) }
        if (index < 0) return
        // Changing grouping/preferences must not interrupt the stream already playing.
        mutable.value = mutable.value.copy(playback = session.copy(channels = channels, index = index,
            selectedStreamId = session.channel.id))
    }

    internal fun selectChannelVersion(id: Int) {
        val session = mutable.value.playback ?: return
        if (session.channel.id == id || session.versions.none { it.id == id }) return
        val index = session.channels.indexOfFirst { it.containsStream(id) }
        if (index < 0) return
        mutable.value = mutable.value.copy(playback = session.copy(index = index, selectedStreamId = id))
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
        ++catalogSyncRevision
        catalogSyncJob?.cancel()
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
            refreshingCatalog = false, catalogSyncError = null,
            loadingChannels = false, channelsLoaded = false, error = null, channelError = null, notice = null, connectingServer = null,
            savedCredentials = saved, page = DesktopPage.DASHBOARD, automaticLogin = false)
    }

    fun syncCatalog() {
        val account = mutable.value.credentials ?: return
        if (mutable.value.refreshingCatalog) return
        val revision = ++catalogSyncRevision
        mutable.value = mutable.value.copy(refreshingCatalog = true, catalogSyncError = null, notice = null)
        catalogSyncJob = scope.launch {
            try {
                val categories = withContext(Dispatchers.IO) { client.categories(account) }
                ensureActive()
                if (mutable.value.credentials != account || catalogSyncRevision != revision) return@launch
                mutable.value = mutable.value.copy(categories = categories, notice = "Categorias ao vivo atualizadas.")
                mutable.value.selectedCategory?.let { old ->
                    val selected = categories.firstOrNull { it.id == old.id }
                    if (selected == null) clearLiveSelection()
                    else if (mutable.value.page == DesktopPage.LIVE) loadChannels(selected, force = true)
                    else {
                        channelsJob?.cancel()
                        mutable.value = mutable.value.copy(selectedCategory = selected, channels = emptyList(),
                            channelsLoaded = false, loadingChannels = false, channelError = null)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (mutable.value.credentials == account && catalogSyncRevision == revision)
                    mutable.value = mutable.value.copy(catalogSyncError = friendly(e))
            } finally {
                if (mutable.value.credentials == account && catalogSyncRevision == revision) mutable.value = mutable.value.copy(refreshingCatalog = false)
            }
        }
    }

    private fun backupFile(): java.nio.file.Path = java.nio.file.Path.of(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), DesktopBuildInfo.DATA_FOLDER, "squitv-backup.json")

    internal suspend fun exportBackup(preferences: DesktopPreferences): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDesktopPreferences(preferences)
            val file = backupFile()
            java.nio.file.Files.createDirectories(file.parent)
            val payload = backupJson.encodeToString(DesktopPreferences.serializer(), preferences)
            require(payload.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
            val temp = java.nio.file.Files.createTempFile(file.parent, "backup-", ".tmp")
            try {
                java.nio.file.Files.writeString(temp, payload)
                try { java.nio.file.Files.move(temp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
                catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    java.nio.file.Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            } finally { java.nio.file.Files.deleteIfExists(temp) }
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }

    internal suspend fun importBackup(): DesktopPreferences? = withContext(Dispatchers.IO) {
        try {
            val file = backupFile()
            require(java.nio.file.Files.size(file) in 1..64L * 1024)
            val restored = backupJson.decodeFromString(DesktopPreferences.serializer(), java.nio.file.Files.readString(file))
            validateDesktopPreferences(restored)
            restored
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    private fun friendly(error: Exception) = when (error) {
        is ServerProblem, is ServerAccountException -> error.message ?: "Não foi possível concluir a consulta."
        else -> "O servidor enviou dados inválidos ou indisponíveis. Tente novamente."
    }

    override fun close() {
        search.reset()
        catalog.reset()
        connectionJob?.cancel(); channelsJob?.cancel(); dnsJob?.cancel(); epgJob?.cancel(); catalogSyncJob?.cancel()
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
            val sourceScope = liveCategoryScope(account).orEmpty()
            val source = LiveChannelGroups.visible(results.mapNotNull { it.channel }, sourceScope,
                groupLiveChannels, preferredChannelQuality)
            val index = source.indexOfFirst { it.containsStream(item.id) }
            if (index < 0) return
            // Only the visible live search results, never a category/global fallback.
            mutable.value = current.copy(playback = LiveSession("Busca · ${search.state.value.query.trim()}", source, index, sourceScope))
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

    fun clearCache() {
        catalog.clearCache()
        search.reset()
        mutable.value = mutable.value.copy(notice = "Cache em memória dos catálogos e da busca limpo.")
    }


}
