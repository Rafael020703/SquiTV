@file:Suppress("DEPRECATION")
package rsv.squitv.ui.viewmodel.settings

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import rsv.squitv.R
import rsv.squitv.data.model.BackupOption
import rsv.squitv.data.model.SettingsBackupCodec
import rsv.squitv.data.repository.SettingsBackupRepository
import rsv.squitv.data.transfer.LocalTransferProtocol as Protocol
import timber.log.Timber

data class LocalPeer(val id: String, val name: String, val address: InetAddress, val port: Int, val fingerprint: String)
data class IncomingTransfer(val id: String, val name: String, val address: String, val options: Set<BackupOption>)
data class LocalTransferState(
    val receiving: Boolean = false, val starting: Boolean = false, val discovering: Boolean = false,
    val busy: Boolean = false, val completed: Boolean = false, val addresses: List<String> = emptyList(),
    val peers: List<LocalPeer> = emptyList(), val pending: IncomingTransfer? = null,
    val status: Int? = null, val notice: Int? = null
)

@HiltViewModel
class LocalTransferViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val backups: SettingsBackupRepository
) : ViewModel() {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _state = MutableStateFlow(LocalTransferState())
    val state = _state.asStateFlow()
    @Volatile private var generation = 0
    @Volatile private var options = BackupOption.entries.toSet()
    private var receiverJob: Job? = null
    private var discoveryJob: Job? = null
    private var senderJob: Job? = null
    private var expiryJob: Job? = null
    private var server: ServerSocket? = null
    private var accepted: Socket? = null
    private var outgoing: Socket? = null
    private var approval: Pair<String, CompletableDeferred<Boolean>>? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val deviceName = Build.MODEL.filterNot(Char::isISOControl).trim().take(64).ifBlank { "Android" }

    @Synchronized
    private fun inSession(session: Int, action: () -> Unit): Boolean {
        if (session != generation) return false
        action()
        return true
    }
    private fun update(session: Int, action: (LocalTransferState) -> LocalTransferState) {
        inSession(session) { _state.update(action) }
    }

    @Synchronized
    fun startReceiver(selected: Set<BackupOption>) {
        stop()
        options = selected.toSet()
        if (options.isEmpty()) return
        val session = generation
        _state.value = LocalTransferState(starting = true)
        receiverJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val identity = Protocol.identity()
                ensureActive()
                val listener = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(0), 1) }
                if (!inSession(session) { server = listener }) { listener.close(); return@launch }
                val addresses = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp }
                    .flatMap { it.inetAddresses.toList() }.filter { !it.isLoopbackAddress && it.address.size == 4 }
                    .map { "${it.hostAddress}:${listener.localPort}" }
                update(session) { it.copy(starting = false, receiving = true, addresses = addresses) }
                advertise(listener.localPort, session, identity)
                while (isActive && session == generation) {
                    val socket = listener.accept()
                    if (!inSession(session) { accepted = socket }) { socket.close(); return@launch }
                    socket.use {
                        it.soTimeout = 20_000
                        val input = DataInputStream(it.getInputStream())
                        val output = DataOutputStream(it.getOutputStream())
                        var channel: Protocol.Session? = null
                        var requestId: String? = null
                        try {
                            channel = Protocol.receive(input, output, identity)
                            val name = channel.read(input, 256).filterNot(Char::isISOControl).trim().take(64)
                            require(name.isNotBlank())
                            channel.write(output, "READY")
                            update(session) { state -> state.copy(busy = true, status = R.string.local_sync_receiving_data, notice = null) }
                            val backup = SettingsBackupCodec.decode(channel.read(input))
                            ensureActive()
                            val selectedOptions = options.intersect(SettingsBackupCodec.available(backup))
                            require(selectedOptions.isNotEmpty())
                            val id = UUID.randomUUID().toString()
                            requestId = id
                            val decision = CompletableDeferred<Boolean>(coroutineContext[Job])
                            val request = IncomingTransfer(id, name, it.inetAddress.hostAddress.orEmpty(), selectedOptions)
                            if (!inSession(session) {
                                approval = id to decision
                                _state.update { state -> state.copy(pending = request, status = null) }
                            }) return@launch
                            val answer = withTimeoutOrNull(60_000) { decision.await() }
                            ensureActive()
                            if (session != generation) return@launch
                            if (answer != true) {
                                channel.write(output, if (answer == null) "EXPIRED" else "DECLINED")
                                update(session) { state -> state.copy(busy = false, pending = null,
                                    notice = if (answer == null) R.string.local_sync_request_expired else R.string.local_sync_declined) }
                                return@use
                            }
                            update(session) { state -> state.copy(pending = null, status = R.string.local_sync_applying) }
                            channel.write(output, "APPLYING")
                            val result = backups.import(backup, selectedOptions)
                            val partial = result.skippedAccounts > 0 || result.skippedFavorites > 0
                            // The import has completed even when the acknowledgement cannot reach the sender.
                            runCatching { channel.write(output, if (partial) "PARTIAL" else "OK") }
                            inSession(session) {
                                _state.update { state -> state.copy(busy = false, receiving = false, completed = true,
                                    pending = null, status = null,
                                    notice = if (partial) R.string.local_sync_received_partial else R.string.local_sync_received) }
                                closeReceiver()
                            }
                            return@launch
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) {
                            Timber.w("Local receive failed: %s", e.javaClass.simpleName)
                            runCatching { channel?.write(output, "ERROR") }
                            update(session) { state -> state.copy(busy = false, pending = null, status = null,
                                notice = if (e is Protocol.IncompatibleVersion) R.string.local_sync_update_required else R.string.local_sync_receive_error) }
                        } finally {
                            synchronized(this@LocalTransferViewModel) {
                                if (accepted === socket) accepted = null
                                if (approval?.first == requestId) { approval?.second?.cancel(); approval = null }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                inSession(session) {
                    closeReceiver()
                    _state.update { it.copy(starting = false, busy = false, receiving = false,
                        pending = null, status = null, notice = R.string.local_sync_receive_error) }
                }
            }
        }
        expiryJob = viewModelScope.launch {
            delay(10 * 60 * 1000L)
            if (session == generation && _state.value.receiving) {
                stop()
                _state.update { it.copy(notice = R.string.local_sync_expired) }
            }
        }
    }

    @Synchronized
    fun respond(requestId: String, accept: Boolean) {
        val current = approval ?: return
        if (current.first == requestId) current.second.complete(accept)
    }

    private fun advertise(port: Int, session: Int, identity: KeyPair) {
        val callback = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                if (session != generation || !_state.value.receiving) runCatching { nsd.unregisterService(this) }
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
                update(session) { it.copy(notice = R.string.local_sync_discovery_error) }
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        val info = NsdServiceInfo().apply {
            serviceName = "SquiTV ${deviceName.filter { it in ' '..'~' }.take(24)} ${UUID.randomUUID().toString().take(8)}"
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("v", Protocol.VERSION)
            setAttribute("name", deviceName)
            setAttribute("key", Protocol.fingerprint(identity))
        }
        inSession(session) {
            registration = callback
            runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, callback) }
                .onFailure { update(session) { it.copy(notice = R.string.local_sync_discovery_error) } }
        }
    }

    @Synchronized
    fun startDiscovery(selected: Set<BackupOption>) {
        stop()
        options = selected.toSet()
        val session = generation
        val queue = Channel<NsdServiceInfo>(32)
        val found = ConcurrentHashMap.newKeySet<String>()
        _state.value = LocalTransferState(discovering = true)
        val callback = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) {
                update(session) { it.copy(discovering = false, notice = R.string.local_sync_discovery_error) }
            }
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                if (session == generation && info.serviceType.contains("squitv") && found.add(info.serviceName)) {
                    if (queue.trySend(info).isFailure) found.remove(info.serviceName)
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                update(session) { it.copy(peers = it.peers.filterNot { peer -> peer.id == info.serviceName }) }
            }
        }
        discovery = callback
        discoveryJob = viewModelScope.launch {
            try {
                for (service in queue) {
                    val resolved = withTimeoutOrNull(5000) {
                        suspendCancellableCoroutine<NsdServiceInfo?> { continuation ->
                            try {
                                nsd.resolveService(service, object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                                        if (continuation.isActive) continuation.resumeWith(Result.success(null))
                                    }
                                    override fun onServiceResolved(info: NsdServiceInfo) {
                                        if (continuation.isActive) continuation.resumeWith(Result.success(info))
                                    }
                                })
                            } catch (_: Exception) {
                                if (continuation.isActive) continuation.resumeWith(Result.success(null))
                            }
                        }
                    } ?: continue
                    if (session != generation || service.serviceName !in found) continue
                    val version = resolved.attributes["v"]?.toString(Charsets.UTF_8)
                    val fingerprint = resolved.attributes["key"]?.toString(Charsets.UTF_8)
                    if (version != Protocol.VERSION || fingerprint == null || !fingerprint.matches(Regex("[a-f0-9]{64}"))) {
                        update(session) { it.copy(notice = R.string.local_sync_update_required) }
                        continue
                    }
                    val address = resolved.host ?: continue
                    val name = resolved.attributes["name"]?.toString(Charsets.UTF_8)?.filterNot(Char::isISOControl)?.take(64)
                        ?.takeIf(String::isNotBlank) ?: service.serviceName
                    val peer = LocalPeer(service.serviceName, name, address, resolved.port, fingerprint)
                    update(session) { it.copy(peers = (it.peers.filterNot { current -> current.id == peer.id } + peer).sortedBy { peer -> peer.name }) }
                }
            } finally { queue.close() }
        }
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, callback) }
            .onFailure { update(session) { it.copy(discovering = false, notice = R.string.local_sync_discovery_error) } }
    }

    fun send(peer: LocalPeer) {
        val host = peer.address.hostAddress ?: return
        sendTo(host, peer.port, peer.fingerprint)
    }
    fun sendManual(address: String) {
        if (_state.value.busy) return
        try {
            val uri = java.net.URI("tcp://${address.trim()}")
            require(uri.host != null && uri.port in 1..65535 && uri.userInfo == null &&
                uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null)
            sendTo(uri.host, uri.port, null)
        } catch (_: Exception) { _state.update { it.copy(notice = R.string.local_sync_send_error) } }
    }

    @Synchronized
    private fun sendTo(host: String, port: Int, fingerprint: String?) {
        if (_state.value.busy || _state.value.completed || options.isEmpty()) return
        val session = generation
        val selected = options
        _state.update { it.copy(busy = true, status = R.string.local_sync_connecting, notice = null) }
        senderJob = viewModelScope.launch(Dispatchers.IO) {
            val socket = Socket()
            try {
                val payload = backups.export(selected)
                ensureActive()
                if (!inSession(session) { outgoing = socket }) return@launch
                socket.use {
                    val address = InetAddress.getByName(host)
                    ensureActive()
                    it.connect(InetSocketAddress(address, port), 5000)
                    it.soTimeout = 120_000
                    val input = DataInputStream(it.getInputStream())
                    val output = DataOutputStream(it.getOutputStream())
                    val channel = Protocol.connect(input, output, fingerprint)
                    channel.write(output, deviceName)
                    require(channel.read(input, 256) == "READY")
                    channel.write(output, payload)
                    update(session) { state -> state.copy(status = R.string.local_sync_waiting_approval) }
                    var result = channel.read(input, 256)
                    if (result == "APPLYING") {
                        update(session) { state -> state.copy(status = R.string.local_sync_applying) }
                        result = channel.read(input, 256)
                    }
                    when (result) {
                        "OK", "PARTIAL" -> inSession(session) {
                            closeDiscovery()
                            _state.update { state -> state.copy(busy = false, completed = true, discovering = false, status = null,
                                notice = if (result == "PARTIAL") R.string.local_sync_sent_partial else R.string.local_sync_sent) }
                        }
                        "DECLINED", "EXPIRED" -> update(session) { state -> state.copy(busy = false, status = null,
                            notice = if (result == "EXPIRED") R.string.local_sync_request_expired else R.string.local_sync_declined) }
                        else -> error("Receiver could not apply transfer")
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                update(session) { it.copy(busy = false, status = null,
                    notice = if (e is Protocol.IncompatibleVersion) R.string.local_sync_update_required else R.string.local_sync_send_error) }
            } finally {
                runCatching { socket.close() }
                synchronized(this@LocalTransferViewModel) { if (outgoing === socket) outgoing = null }
            }
        }
    }

    private fun closeReceiver() {
        runCatching { server?.close() }; server = null
        registration?.let { runCatching { nsd.unregisterService(it) } }; registration = null
        expiryJob?.cancel()
    }
    private fun closeDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }; discovery = null
        discoveryJob?.cancel()
    }
    @Synchronized
    fun stop() {
        generation++
        closeReceiver()
        closeDiscovery()
        approval?.second?.cancel(); approval = null
        runCatching { accepted?.close() }; accepted = null
        runCatching { outgoing?.close() }; outgoing = null
        receiverJob?.cancel(); senderJob?.cancel()
        _state.update { it.copy(receiving = false, starting = false, busy = false, discovering = false, pending = null, status = null) }
    }
    override fun onCleared() { stop(); super.onCleared() }
    companion object { private const val SERVICE_TYPE = "_squitv._tcp." }
}
