package rsv.squitv.data.pairing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import rsv.squitv.core.domain.interactor.AuthManager
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.repository.SettingsRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val settingsRepository: SettingsRepository
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currentSession = MutableStateFlow<PairingSession?>(null)
    val currentSession: StateFlow<PairingSession?> = _currentSession.asStateFlow()

    private val _serverStatus = MutableStateFlow<PairingState>(PairingState.WAITING)
    val serverStatus: StateFlow<PairingState> = _serverStatus.asStateFlow()

    private val _serverPort = MutableStateFlow(0)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _localIp = MutableStateFlow("127.0.0.1")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private var pairingServer: LocalPairingServer? = null
    private var serverStartupJob: Job? = null
    private var expiryJob: Job? = null
    private val authenticationMutex = Mutex()

    fun startPairingSession() {
        stopPairingSession()

        val session = PairingSession.generate()
        _currentSession.value = session
        _serverStatus.value = PairingState.WAITING
        _localIp.value = LocalPairingServer.getLocalIpAddress()

        val server = LocalPairingServer(
            port = LocalPairingServer.DEFAULT_PAIRING_PORT,
            onLoginSubmitted = { username, password, token ->
                handleLoginSubmission(username, password, token)
            },
            getSessionStatus = {
                _currentSession.value
            },
            getPairingState = { _serverStatus.value }
        )
        pairingServer = server

        serverStartupJob = repositoryScope.launch {
            try {
                val port = server.start()
                if (port == LocalPairingServer.DEFAULT_PAIRING_PORT && pairingServer == server) {
                    _serverPort.value = port
                    Timber.i("Sessão de pareamento iniciada. IP: %s, Porta: %d", _localIp.value, _serverPort.value)
                } else if (pairingServer == server) {
                    _serverStatus.value = PairingState.ERROR
                    server.stop()
                    Timber.e("Falha ao iniciar servidor de pareamento: porta incorreta ($port)")
                }
            } catch (e: Exception) {
                if (pairingServer == server) {
                    _serverStatus.value = PairingState.ERROR
                    server.stop()
                    Timber.e(e, "Erro ao iniciar servidor de pareamento")
                }
            }
        }

        // Start expiration monitor
        expiryJob = repositoryScope.launch {
            val delayMillis = session.expirationTime - System.currentTimeMillis()
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            if (_currentSession.value?.sessionId == session.sessionId && !_currentSession.value!!.isUsed.get()) {
                _serverStatus.value = PairingState.EXPIRED
                stopPairingSession()
                Timber.i("Sessão de pareamento expirada.")
            }
        }
    }

    fun stopPairingSession() {
        serverStartupJob?.cancel()
        serverStartupJob = null
        expiryJob?.cancel()
        expiryJob = null
        pairingServer?.stop()
        pairingServer = null
        _currentSession.value = null
        _serverPort.value = 0
        if (_serverStatus.value !in setOf(PairingState.SUCCESS, PairingState.ERROR, PairingState.EXPIRED)) {
            _serverStatus.value = PairingState.CANCELLED
        }
        Timber.i("Sessão de pareamento encerrada.")
    }

    fun cancelPairing() {
        _serverStatus.value = PairingState.CANCELLED
        stopPairingSession()
    }

    fun handleDeepLinkToken(token: String): Boolean {
        val session = _currentSession.value
        if (session == null || session.isExpired() || session.token != token) {
            Timber.w("Deep link com token inválido ou expirado recebido.")
            return false
        }
        _serverStatus.value = PairingState.CONNECTED
        return true
    }

    private suspend fun handleLoginSubmission(username: String, password: String, token: String): LocalPairingServer.LoginResult {
        val session = _currentSession.value
        if (session == null || session.isExpired()) {
            _serverStatus.value = PairingState.EXPIRED
            return LocalPairingServer.LoginResult.Error("Sessão expirada.")
        }

        if (session.token != token) {
            return LocalPairingServer.LoginResult.Error("Token de pareamento inválido.")
        }

        if (session.isUsed.get()) return LocalPairingServer.LoginResult.Error("Este aparelho já foi conectado.")
        if (!authenticationMutex.tryLock()) return LocalPairingServer.LoginResult.Error("Uma verificação já está em andamento. Aguarde.")
        _serverStatus.value = PairingState.AUTHENTICATING

        try {
            val settings = settingsRepository.settingsFlow.first()
            val baseUrl = settings.credentials?.baseUrl ?: "http://ded30.com/"

            val credentials = XtreamCredentials(username.trim(), password, baseUrl)
            val response = authManager.login(credentials)

            if (_currentSession.value?.sessionId != session.sessionId || session.isExpired()) {
                return LocalPairingServer.LoginResult.Error("Sessão encerrada. Abra um novo login no aparelho.")
            }
            if (response.userInfo?.auth == 1) {
                settingsRepository.saveCredentials(credentials)
                session.isUsed.set(true)
                _serverStatus.value = PairingState.SUCCESS
                Timber.i("Login via rede local realizado com sucesso para usuário: %s", username)
                
                repositoryScope.launch {
                    delay(1000)
                    if (_currentSession.value?.sessionId == session.sessionId) stopPairingSession()
                }

                return LocalPairingServer.LoginResult.Success
            } else {
                _serverStatus.value = PairingState.ERROR
                val msg = response.userInfo?.message ?: "Credenciais inválidas"
                return LocalPairingServer.LoginResult.Error(msg)
            }
        } catch (e: CancellationException) {
            if (_currentSession.value?.sessionId == session.sessionId) _serverStatus.value = PairingState.WAITING
            throw e
        } catch (e: Exception) {
            if (_currentSession.value?.sessionId == session.sessionId) _serverStatus.value = PairingState.ERROR
            Timber.e(e, "Erro no login via rede local")
            return LocalPairingServer.LoginResult.Error("Falha ao autenticar no servidor. Você pode tentar novamente.")
        } finally {
            authenticationMutex.unlock()
        }
    }
}
