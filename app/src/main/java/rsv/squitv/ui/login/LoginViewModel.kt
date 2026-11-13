package rsv.squitv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rsv.squitv.BuildConfig
import rsv.squitv.core.domain.interactor.AuthManager
import rsv.squitv.core.domain.interactor.DnsManager
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.model.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import rsv.squitv.shared.xtream.ProviderServers
import rsv.squitv.shared.xtream.ServerAccountException
import rsv.squitv.shared.xtream.XtreamCatalogParser
import timber.log.Timber

import rsv.squitv.data.repository.UpdateRepository
import rsv.squitv.domain.model.AppUpdateInfo
import rsv.squitv.domain.model.UpdateCheckResult

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dnsManager: DnsManager,
    private val authManager: AuthManager,
    private val updateRepository: UpdateRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    enum class LoginState {
        IDLE,
        CONNECTING,
        SUCCESS,
        SYNCING,
        ERROR
    }

    private val _state = MutableStateFlow(LoginState.IDLE)
    val state: StateFlow<LoginState> = _state

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _loginSuccess = MutableSharedFlow<Unit>()
    val loginSuccess = _loginSuccess.asSharedFlow()

    private val _promptSaveAccount = MutableStateFlow<XtreamCredentials?>(null)
    val promptSaveAccount: StateFlow<XtreamCredentials?> = _promptSaveAccount.asStateFlow()

    val appSettings: StateFlow<SettingsRepository.AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsRepository.AppSettings(null, lastSyncTimestamp = 0L, syncIntervalHours = 24)
    )

    val savedAccounts: StateFlow<List<XtreamCredentials>> = appSettings.map { it.accounts }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _dnsStatusMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val dnsStatusMap: StateFlow<Map<String, Long>> = _dnsStatusMap

    private val _selectedDns = MutableStateFlow<String?>(null)
    val selectedDns: StateFlow<String?> = _selectedDns

    private val _isTestingDns = MutableStateFlow(false)
    val isTestingDns: StateFlow<Boolean> = _isTestingDns

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = combine(
        _updateInfo, appSettings.map { it.ignoredVersion }.distinctUntilChanged()
    ) { info, ignored -> info?.takeUnless { it.versionName == ignored } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val availableDns = dnsManager.getDnsOptions()

    init {
        testAllDns()
        checkForUpdates()
        

    }

    fun onUrlChanged(value: String) { _url.value = value }
    fun onUsernameChanged(value: String) { _username.value = value }
    fun onPasswordChanged(value: String) { _password.value = value }
    fun onDnsSelected(url: String?) { _selectedDns.value = url }

    fun selectSavedAccount(account: XtreamCredentials) {
        _username.value = account.username
        _password.value = account.password
        _url.value = account.baseUrl
        _selectedDns.value = account.baseUrl
    }

    fun removeSavedAccount(index: Int) {
        viewModelScope.launch {
            settingsRepository.removeAccount(index)
        }
    }

    fun confirmSaveAccount(credentials: XtreamCredentials, save: Boolean) {
        viewModelScope.launch {
            if (save) {
                settingsRepository.saveAccountToSavedList(credentials)
                Timber.i("User confirmed saving account ${credentials.username}")
            } else {
                Timber.i("User declined saving account ${credentials.username}")
            }
            _promptSaveAccount.value = null
            _loginSuccess.emit(Unit)
        }
    }

    fun testAllDns() {
        viewModelScope.launch {
            _isTestingDns.value = true
            val currentMap = mutableMapOf<String, Long>()
            _dnsStatusMap.value = emptyMap()

            val deferreds = availableDns.map { dnsUrl ->
                async(Dispatchers.IO) {
                    val ping = dnsManager.testDns(dnsUrl)
                    dnsUrl to ping
                }
            }

            deferreds.forEach { deferred ->
                val (dnsUrl, ping) = deferred.await()
                currentMap[dnsUrl] = ping
                _dnsStatusMap.value = currentMap.toMap()
            }

            _isTestingDns.value = false
        }
    }

    private var loginJob: Job? = null

    fun login() = login(_username.value.trim(), _password.value, _url.value.trim())

    fun loginLinked(credentials: rsv.squitv.shared.account.AccountPilot.LinkedLogin) =
        login(credentials.username, credentials.password, "", linkedLogin = credentials)

    fun cancelLogin() {
        if (_state.value == LoginState.CONNECTING) loginJob?.cancel()
    }

    private fun login(
        currentUser: String,
        currentPass: String,
        customUrl: String,
        linkedLogin: rsv.squitv.shared.account.AccountPilot.LinkedLogin? = null,
    ) {
        if (_isLoading.value || _state.value == LoginState.SUCCESS) return

        if (currentUser.isEmpty() || currentPass.isEmpty()) {
            _errorMessage.value = "Preencha usuário e senha"
            _state.value = LoginState.ERROR
            return
        }

        _isLoading.value = true
        _state.value = LoginState.CONNECTING
        _errorMessage.value = null
        loginJob = viewModelScope.launch {
            
            try {
                val selected = _selectedDns.value.takeIf { linkedLogin == null }
                val dnsList = ProviderServers.loginCandidates(customUrl, selected, availableDns)
                for (dns in dnsList) {
                    val credentials = XtreamCredentials(currentUser, currentPass, dns)
                    try {
                        dnsManager.updateBaseUrl(dns)
                        val response = withTimeoutOrNull(15_000) { authManager.login(credentials) }
                            ?: throw IllegalStateException("O servidor não respondeu ao login no prazo esperado.")
                        val user = response.userInfo
                        XtreamCatalogParser.validateProviderLogin(user?.auth?.toString(), user?.status,
                            user?.expDate?.toLongOrNull(), System.currentTimeMillis() / 1000)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ServerAccountException) {
                        _errorMessage.value = e.message
                        _state.value = LoginState.ERROR
                        return@launch
                    } catch (_: Exception) {
                        continue
                    }
                    settingsRepository.saveCredentials(credentials, linkedLogin?.displayName,
                        linkedLogin?.favoriteClient?.encodeForProtectedStorage())
                    _state.value = LoginState.SUCCESS
                    _loginSuccess.emit(Unit)
                    return@launch
                }
                _errorMessage.value = if (linkedLogin == null) "Não foi possível conectar ao servidor IPTV."
                    else "O vínculo foi encontrado, mas o servidor IPTV não respondeu. Tente novamente."
                _state.value = LoginState.ERROR
            } catch (e: CancellationException) {
                _state.value = LoginState.IDLE
                throw e
            } catch (_: Exception) {
                _errorMessage.value = "Não foi possível concluir o login. Confira o endereço e tente novamente."
                _state.value = LoginState.ERROR
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = updateRepository.checkForUpdates(force = false)
                if (result is UpdateCheckResult.UpdateAvailable) {
                    _updateInfo.value = result.updateInfo
                } else {
                    _updateInfo.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error checking update on login screen")
                _updateInfo.value = null
            }
        }
    }
}
