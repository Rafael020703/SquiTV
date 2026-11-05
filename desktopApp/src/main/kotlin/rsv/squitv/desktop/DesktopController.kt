package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rsv.squitv.desktop.data.*
import rsv.squitv.shared.xtream.*
import rsv.squitv.domain.model.ChannelQuality
import rsv.squitv.core.domain.state.AppSyncProgress
import rsv.squitv.core.domain.state.AppSyncStatus

enum class DesktopPage { DASHBOARD, LIVE, MOVIES, SERIES, SETTINGS, UPDATES, SEARCH, FAVORITES, MULTIVIEW, DOWNLOADS, APP_DNS }
internal val ALL_LIVE_CATEGORY = LiveCategory("ALL", "Todos")
internal val RECENT_LIVE_CATEGORY = LiveCategory("RECENTS", "Adicionados recentemente")
data class LiveSession(val sourceName: String, val channels: List<LiveChannel>, val index: Int,
    val sourceScope: String = "", val selectedStreamId: Int? = null, val sourceCategoryId: String? = null) {
    val channel: LiveChannel get() = LiveChannelGroups.select(channels[index], selectedStreamId) ?: channels[index]
    val versions: List<LiveChannel> get() = LiveChannelGroups.visible(channels, sourceScope, true)
        .firstOrNull { it.containsStream(channel.id) }?.versions.orEmpty()
}

data class DesktopState(
    val restoring: Boolean = true, val busy: Boolean = false,
    val loginSync: AppSyncProgress? = null,
    val refreshingCatalog: Boolean = false, val catalogSyncError: String? = null, val automaticLogin: Boolean = false,
    val credentials: ServerCredentials? = null, val savedCredentials: ServerCredentials? = null,
    val savedAccounts: List<ServerCredentials> = emptyList(), val managingAccounts: Boolean = false,
    val accountError: String? = null,
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
    internal var watchHistory: DesktopWatchHistory? = null
    internal var downloads: DesktopDownloads? = null
    internal val catalog = OnDemandController(client, scope) { watchHistory }
    internal val search = DesktopSearchController(client, scope)
    internal val favorites = DesktopFavoritesController(client, scope)
    internal val liveIndex = DesktopLiveIndex(client, scope)
    internal val multiView = DesktopMultiViewController(liveIndex)
    private var watchSaveJob: Job? = null
    private var connectionJob: Job? = null
    private var accountManagementJob: Job? = null
    private var channelsJob: Job? = null
    private var dnsJob: Job? = null
    private var epgJob: Job? = null
    private var catalogSyncJob: Job? = null
    private var catalogSyncRevision = 0L
    private var groupLiveChannels = true
    private var preferredChannelQuality = ChannelQuality.AUTO
    private val credentialWrites = Mutex()
    private val dnsProbe = DnsProbe()
    val availableDns: List<String> = ProviderServers.addresses.map(ServerAddress::normalize)

    internal fun saveWatchProgress(account: ServerCredentials, session: VodSession,
        status: rsv.squitv.desktop.player.PlayerStatus, force: Boolean = false) {
        val key = liveCategoryScope(account) ?: return
        val observed = System.currentTimeMillis()
        watchSaveJob = scope.launch { watchHistory?.record(key, session, status, force, observed) }
    }

    internal suspend fun flushWatchProgress(status: rsv.squitv.desktop.player.PlayerStatus?) {
        catalog.beforePlaybackChange?.let { save -> save(); watchSaveJob?.join(); return }
        val account = liveCategoryScope(mutable.value.credentials) ?: return
        val session = catalog.state.value.playback ?: return
        if (status != null) watchHistory?.record(account, session, status, force = true)
    }

    internal suspend fun awaitWatchSave() { watchSaveJob?.join() }

    internal fun openContinueEntry(entry: DesktopWatchEntry) {
        val account = mutable.value.credentials ?: return
        val key = liveCategoryScope(account) ?: return
        val actual = watchHistory?.continuing(key)?.firstOrNull { it.key == entry.key } ?: return
        catalog.openSearchDetail(if (actual.series) CatalogKind.SERIES else CatalogKind.MOVIES, actual.item(), account)
    }

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
            mutable.value = mutable.value.copy(savedCredentials = saved, debugLoginDefaults = debugDefaults,
                selectedDns = saved?.baseUrl?.let { url -> availableDns.firstOrNull { it == url } })
            reloadSavedAccounts()
            mutable.value = mutable.value.copy(restoring = false)
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

    private suspend fun reloadSavedAccounts() {
        try {
            val accounts = withContext(Dispatchers.IO) { store.savedAccounts() }
            mutable.value = mutable.value.copy(savedAccounts = accounts, accountError = null)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutable.value = mutable.value.copy(accountError = "Não foi possível ler as contas salvas. Os arquivos foram preservados.") }
    }

    internal fun switchSavedAccount(id: String) {
        if (mutable.value.busy || mutable.value.managingAccounts) return
        val target = mutable.value.savedAccounts.firstOrNull { liveCategoryScope(it) == id } ?: return
        if (mutable.value.credentials == target) return
        disconnect()
        mutable.value = mutable.value.copy(savedCredentials = target, selectedDns = null)
        connect(target.baseUrl, target.username, target.password, remember = true)
    }

    internal fun removeSavedAccount(id: String) {
        if (mutable.value.busy || mutable.value.managingAccounts || mutable.value.savedAccounts.none { liveCategoryScope(it) == id }) return
        mutable.value = mutable.value.copy(managingAccounts = true, accountError = null)
        accountManagementJob = scope.launch {
            try {
                credentialWrites.withLock { withContext(Dispatchers.IO) { store.removeAccount(id) } }
                if (liveCategoryScope(mutable.value.savedCredentials) == id)
                    mutable.value = mutable.value.copy(savedCredentials = null)
                reloadSavedAccounts()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(accountError = "Não foi possível remover a conta salva.") }
            finally { mutable.value = mutable.value.copy(managingAccounts = false) }
        }
    }

    internal fun refreshAccountInfo() {
        val account = mutable.value.credentials ?: return
        if (mutable.value.managingAccounts || mutable.value.busy) return
        mutable.value = mutable.value.copy(managingAccounts = true, accountError = null)
        accountManagementJob = scope.launch {
            try {
                val info = withContext(Dispatchers.IO) { client.login(account) }
                if (mutable.value.credentials == account) mutable.value = mutable.value.copy(account = info)
                reloadSavedAccounts()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (mutable.value.credentials == account) mutable.value = mutable.value.copy(accountError = "Não foi possível atualizar os dados da conta. Tente novamente.")
            } finally { mutable.value = mutable.value.copy(managingAccounts = false) }
        }
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
                search.reset(); favorites.reset(); multiView.reset(); liveIndex.reset()
                var notice: String? = null
                credentialWrites.withLock { withContext(Dispatchers.IO) {
                    try { if (remember) store.save(credentials) else store.clear() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        runCatching { store.setAutomaticLogin(false) }
                        notice = "Conectado, mas não foi possível atualizar a conta salva. Confira as contas salvas nas Configurações."
                    }
                } }
                reloadSavedAccounts()
                val operation = java.util.UUID.randomUUID().toString()
                mutable.value = mutable.value.copy(restoring = false, credentials = credentials, account = account,
                    savedCredentials = if (remember) credentials else null, notice = notice, busy = true,
                    connectingServer = null, error = null, categories = emptyList(), channels = emptyList(),
                    selectedCategory = null, channelsLoaded = false, channelError = null, automaticLogin = false,
                    page = DesktopPage.DASHBOARD, loginSync = newLoginSync(operation))
                downloads?.bind(credentials)
                synchronizeLoginCategories(credentials, operation)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                mutable.value = mutable.value.copy(busy = false, automaticLogin = false,
                    connectingServer = null, error = friendly(e))
            }
        }
    }

    private fun newLoginSync(operation: String) = AppSyncProgress(accountStatus = AppSyncStatus.Success,
        operationId = operation, isRunning = true)

    fun retryLoginSync() {
        val account = mutable.value.credentials ?: return
        if (mutable.value.busy || connectionJob?.isActive == true) return
        val operation = java.util.UUID.randomUUID().toString()
        mutable.value = mutable.value.copy(busy = true, loginSync = newLoginSync(operation))
        connectionJob = scope.launch { synchronizeLoginCategories(account, operation) }
    }

    fun continueLoginSync() {
        val progress = mutable.value.loginSync ?: return
        if (progress.isRunning) return
        if (listOf(progress.liveStatus, progress.vodStatus, progress.seriesStatus).none { it is AppSyncStatus.Done }) return
        mutable.value = mutable.value.copy(loginSync = null, busy = false)
    }

    private suspend fun synchronizeLoginCategories(account: ServerCredentials, operation: String) {
        var progress = newLoginSync(operation)
        suspend fun ensureCurrent() {
            currentCoroutineContext().ensureActive()
            if (mutable.value.credentials != account || mutable.value.loginSync?.operationId != operation)
                throw CancellationException("Login synchronization replaced")
        }
        fun stage(type: String, status: AppSyncStatus) {
            progress = when (type) {
                "LIVE" -> progress.copy(liveStatus = status)
                "VOD" -> progress.copy(vodStatus = status)
                else -> progress.copy(seriesStatus = status)
            }
            mutable.value = mutable.value.copy(loginSync = progress)
        }
        val errors = mutableListOf<String>()
        for ((type, label) in listOf("LIVE" to "TV ao vivo", "VOD" to "Filmes", "SERIES" to "Séries")) {
            ensureCurrent()
            stage(type, AppSyncStatus.Syncing)
            try {
                val categories = withContext(Dispatchers.IO) {
                    if (type == "LIVE") client.categories(account) else client.catalogCategories(account, type == "SERIES")
                }
                ensureCurrent()
                if (type == "LIVE") mutable.value = mutable.value.copy(categories = categories)
                else catalog.acceptLoginCategories(account,
                    if (type == "VOD") CatalogKind.MOVIES else CatalogKind.SERIES, categories)
                stage(type, AppSyncStatus.Done(categories.size))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureCurrent()
                val message = friendly(e)
                errors += "$label: $message"
                stage(type, AppSyncStatus.Error(message))
            }
        }
        ensureCurrent()
        progress = progress.copy(isRunning = false, isComplete = true,
            error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"))
        mutable.value = mutable.value.copy(busy = false, loginSync = if (errors.isEmpty()) null else progress)
    }

    fun loadChannels(category: LiveCategory) = loadChannels(category, force = false)

    private fun loadChannels(category: LiveCategory, force: Boolean) {
        // Synthetic categories are supplied as explicit snapshots, never provider/global queries.
        if (category.id in setOf(RECENT_LIVE_CATEGORY.id, ALL_LIVE_CATEGORY.id)) return
        val credentials = mutable.value.credentials ?: return
        val current = mutable.value
        if (!force && current.selectedCategory == category && (current.loadingChannels || current.channelsLoaded && current.channelError == null)) return
        channelsJob?.cancel()
        mutable.value = mutable.value.copy(selectedCategory = category, channels = emptyList(),
            loadingChannels = true, channelsLoaded = false, channelError = null)
        channelsJob = scope.launch {
            try {
                val index = liveIndex.state.value
                val knownCategoryIds = current.categories.map { it.id }.toSet()
                val channels = if (index.loaded && index.accountScope == liveCategoryScope(credentials))
                    index.channels.filter { if (category.id == rsv.squitv.shared.catalog.LiveCatalogView.UNCLASSIFIED)
                        it.categoryId !in knownCategoryIds else it.categoryId == category.id }
                    else withContext(Dispatchers.IO) { client.channels(credentials, category.id) }
                ensureActive()
                mutable.value = mutable.value.copy(channels = channels, loadingChannels = false, channelsLoaded = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                mutable.value = mutable.value.copy(loadingChannels = false, channelError = friendly(e))
            }
        }
    }

    internal fun loadCatalogSnapshot(category: LiveCategory, channels: List<LiveChannel>) {
        if (category !in listOf(RECENT_LIVE_CATEGORY, ALL_LIVE_CATEGORY)) return
        val current = mutable.value
        val accountScope = liveCategoryScope(current.credentials) ?: return
        val index = liveIndex.state.value
        if (!index.loaded || index.accountScope != accountScope || channels.isEmpty() || category == RECENT_LIVE_CATEGORY && channels.size > 100) return
        val known = index.channels.associateBy { it.id }
        if (channels.distinctBy { it.id }.size != channels.size || channels.any { known[it.id] != it }) return
        if (current.selectedCategory == category && current.channels == channels && current.channelsLoaded) return
        channelsJob?.cancel()
        mutable.value = current.copy(selectedCategory = category, channels = channels.toList(),
            loadingChannels = false, channelsLoaded = true, channelError = null)
    }

    internal fun clearLiveSelection() {
        channelsJob?.cancel()
        mutable.value = mutable.value.copy(selectedCategory = null, channels = emptyList(),
            loadingChannels = false, channelsLoaded = false, channelError = null)
    }
    internal fun refreshLiveSelectionFromIndex() {
        val current = mutable.value
        val index = liveIndex.state.value
        if (current.page == DesktopPage.LIVE && index.loaded && index.accountScope == liveCategoryScope(current.credentials))
            current.selectedCategory?.takeUnless { it.id in setOf(RECENT_LIVE_CATEGORY.id, ALL_LIVE_CATEGORY.id) }?.let { loadChannels(it, force = true) }
    }

    fun openLive() {
        favorites.leave(); multiView.leave()
        search.leave()
        catalog.leave()
        mutable.value = mutable.value.copy(page = DesktopPage.LIVE)
        // The live screen selects only after account preferences (hidden/order) are ready.
    }

    fun home() {
        favorites.leave(); multiView.leave()
        search.leave()
        catalog.leave()
        stopPlayback()
        mutable.value = mutable.value.copy(page = DesktopPage.DASHBOARD)
    }

    fun openAppDns() { mutable.value = mutable.value.copy(page = DesktopPage.APP_DNS) }

    fun play(channel: LiveChannel, visibleChannels: List<LiveChannel>, favoriteSource: Boolean = false) {
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
        val sourceName = if (favoriteSource) "Favoritos · ${category.name}" else category.name
        mutable.value = current.copy(playback = LiveSession(sourceName, source, index, sourceScope, sourceCategoryId = category.id))
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
        mutable.value = mutable.value.copy(busy = false, automaticLogin = false, connectingServer = null,
            loginSync = mutable.value.loginSync?.copy(isRunning = false, isComplete = true, isCancelled = true))
    }

    fun disconnect() {
        accountManagementJob?.cancel()
        downloads?.bind(null)
        favorites.reset(); multiView.reset(); liveIndex.reset()
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
            managingAccounts = false, accountError = null,
            account = null, categories = emptyList(), channels = emptyList(), selectedCategory = null,
            refreshingCatalog = false, catalogSyncError = null, loginSync = null,
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
                // Counts must not continue using a previous catalog after an explicit refresh.
                liveIndex.reset()
                if (mutable.value.page == DesktopPage.LIVE) liveIndex.load(account)
                mutable.value.selectedCategory?.let { old ->
                    val selected = categories.firstOrNull { it.id == old.id }
                    if (old.id in setOf(RECENT_LIVE_CATEGORY.id, ALL_LIVE_CATEGORY.id)) {
                        // The live screen rebuilds this snapshot after the refreshed index is ready.
                        channelsJob?.cancel()
                        mutable.value = mutable.value.copy(channels = emptyList(), channelsLoaded = false,
                            loadingChannels = false, channelError = null)
                    } else if (selected == null) clearLiveSelection()
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

    internal suspend fun exportBackup(preferences: DesktopPreferences, destination: java.nio.file.Path = backupFile(),
        selected: Set<DesktopBackupGroup> = DesktopBackupGroup.entries.toSet()): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = DesktopBackupCodec.encode(preferences, selected)
            val file = destination.toAbsolutePath().normalize()
            java.nio.file.Files.createDirectories(file.parent)
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

    internal suspend fun importBackup(source: java.nio.file.Path = backupFile()): DesktopBackupContents? = withContext(Dispatchers.IO) {
        try {
            val file = source.toAbsolutePath().normalize()
            require(java.nio.file.Files.isRegularFile(file))
            require(java.nio.file.Files.size(file) in 1..DesktopBackupCodec.MAX_BYTES.toLong())
            val bytes = java.nio.file.Files.newInputStream(file).use { it.readNBytes(DesktopBackupCodec.MAX_BYTES + 1) }
            require(bytes.size in 1..DesktopBackupCodec.MAX_BYTES)
            DesktopBackupCodec.decode(bytes.toString(Charsets.UTF_8))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    private fun friendly(error: Exception) = when (error) {
        is ServerProblem, is ServerAccountException -> error.message ?: "Não foi possível concluir a consulta."
        else -> "O servidor enviou dados inválidos ou indisponíveis. Tente novamente."
    }

    override fun close() {
        downloads?.close()
        favorites.reset(); multiView.reset(); liveIndex.reset()
        search.reset()
        catalog.reset()
        connectionJob?.cancel(); channelsJob?.cancel(); dnsJob?.cancel(); epgJob?.cancel(); catalogSyncJob?.cancel()
        dnsProbe.close(); client.close()
    }

    fun openDownloads() {
        val account = mutable.value.credentials ?: mutable.value.savedCredentials ?: return
        search.leave(); favorites.leave(); multiView.leave(); catalog.leave(); stopPlayback()
        downloads?.bind(account)
        mutable.value = mutable.value.copy(page = DesktopPage.DOWNLOADS)
    }

    internal fun downloadMovie() {
        val account = mutable.value.credentials ?: return
        val detail = catalog.state.value.detail?.takeIf { it.kind == CatalogKind.MOVIES && !it.loading } ?: return
        downloads?.start(account, detail.item.copy(extension = detail.movie?.movieData?.container_extension ?: detail.item.extension))
    }

    internal fun downloadEpisodes(season: Int, episodeId: String? = null) {
        val account = mutable.value.credentials ?: return
        val detail = catalog.state.value.detail?.takeIf { it.kind == CatalogKind.SERIES && !it.loading && it.error == null } ?: return
        val info = detail.series ?: return
        val episodes = OnDemandPlayback.episodes(info, season).filter { episodeId == null || it.id == episodeId }
        downloads?.startEpisodes(account, detail.item, season, episodes)
    }

    internal fun retryDownload(key: String) {
        val account = mutable.value.credentials ?: mutable.value.savedCredentials ?: return
        val entry = downloads?.state?.value?.entries?.firstOrNull { it.key == key && it.account == liveCategoryScope(account) } ?: return
        downloads?.retry(account, entry)
    }

    internal fun playDownload(key: String) {
        val account = mutable.value.credentials ?: mutable.value.savedCredentials ?: return
        val accountKey = liveCategoryScope(account) ?: return
        scope.launch {
            val (entry, file) = downloads?.localFile(key, accountKey) ?: return@launch
            val current = mutable.value
            if (current.page != DesktopPage.DOWNLOADS || liveCategoryScope(current.credentials ?: current.savedCredentials) != accountKey) return@launch
            if (entry.kind == DesktopDownloadKind.EPISODE) {
                val available = downloads?.state?.value?.entries.orEmpty().filter { it.account == accountKey &&
                    it.kind == DesktopDownloadKind.EPISODE && it.seriesId == entry.seriesId && it.status == DesktopDownloadStatus.COMPLETED }
                val local = available.mapNotNull { downloads?.localFile(it.key, accountKey) }
                if (mutable.value.page != DesktopPage.DOWNLOADS || liveCategoryScope(mutable.value.credentials ?: mutable.value.savedCredentials) != accountKey) return@launch
                catalog.playDownloadedEpisodes(account, entry, local)
            } else catalog.playDownloadedMovie(account, CatalogItem(entry.streamId, entry.title, null, null,
                extension = entry.extension), file.toUri().toString())
        }
    }

    internal fun openCatalog(kind: CatalogKind) {
        favorites.leave(); multiView.leave()
        val account = mutable.value.credentials ?: return
        search.leave()
        stopPlayback()
        mutable.value = mutable.value.copy(page = if (kind == CatalogKind.MOVIES) DesktopPage.MOVIES else DesktopPage.SERIES)
        catalog.open(kind, account)
    }

    fun openUpdates() {
        favorites.leave(); multiView.leave()
        search.leave()
        catalog.leave(); stopPlayback()
        mutable.value = mutable.value.copy(page = DesktopPage.UPDATES)
    }

    fun openSettings() {
        favorites.leave(); multiView.leave()
        search.leave()
        catalog.leave(); stopPlayback()
        mutable.value = mutable.value.copy(page = DesktopPage.SETTINGS)
    }

    internal fun openSearch() {
        favorites.leave(); multiView.leave()
        val account = mutable.value.credentials ?: return
        catalog.leave(); stopPlayback()
        search.open(account)
        mutable.value = mutable.value.copy(page = DesktopPage.SEARCH)
    }

    fun openFavorites() {
        if (mutable.value.credentials == null) return
        search.leave(); catalog.leave(); stopPlayback(); multiView.leave()
        mutable.value = mutable.value.copy(page = DesktopPage.FAVORITES)
    }

    fun openMultiView() {
        if (mutable.value.credentials == null) return
        search.leave(); catalog.leave(); stopPlayback(); favorites.leave()
        mutable.value = mutable.value.copy(page = DesktopPage.MULTIVIEW)
    }

    internal fun openFavoriteResult(item: DesktopSearchItem, visible: List<DesktopSearchItem>) {
        val current = mutable.value
        val credentials = current.credentials ?: return
        val snapshot = favorites.state.value
        if (snapshot.accountScope != liveCategoryScope(credentials)) return
        val originType = when (current.page) {
            DesktopPage.FAVORITES -> null
            DesktopPage.LIVE -> DesktopSearchType.LIVE
            DesktopPage.MOVIES -> DesktopSearchType.MOVIES
            DesktopPage.SERIES -> DesktopSearchType.SERIES
            else -> return
        }
        if (snapshot.requestedType != originType || originType != null && item.type != originType) return
        val allowed = snapshot.results.associateBy { it.key }
        if (item !in visible || visible.any { allowed[it.key] != it || originType != null && it.type != originType }) return
        favorites.leave()
        if (item.channel != null) {
            val source = visible.mapNotNull { it.channel }.toList()
            val index = source.indexOfFirst { it.containsStream(item.id) }
            if (index < 0) return
            mutable.value = current.copy(playback = LiveSession("Favoritos", source, index, liveCategoryScope(credentials).orEmpty(), sourceCategoryId = "FAVORITES"))
            refreshEpg()
        } else {
            val catalogItem = item.catalogItem ?: return
            val kind = when (item.type) {
                DesktopSearchType.MOVIES -> CatalogKind.MOVIES
                DesktopSearchType.SERIES -> CatalogKind.SERIES
                else -> return
            }
            catalog.openSearchDetail(kind, catalogItem, credentials)
        }
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
        favorites.reset(); multiView.reset(); liveIndex.reset()
        catalog.clearCache()
        search.reset()
        mutable.value = mutable.value.copy(notice = "Cache em memória dos catálogos e da busca limpo.")
    }


}
