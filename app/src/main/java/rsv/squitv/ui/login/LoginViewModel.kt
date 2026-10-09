package rsv.squitv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rsv.squitv.BuildConfig
import rsv.squitv.core.domain.interactor.AuthManager
import rsv.squitv.core.domain.interactor.DnsManager
import rsv.squitv.data.repository.FirebaseRepository
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.model.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

import rsv.squitv.data.repository.UpdateRepository
import rsv.squitv.domain.model.AppUpdateInfo
import rsv.squitv.domain.model.UpdateCheckResult

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val firebaseRepository: FirebaseRepository,
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
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo

    val availableDns = dnsManager.getDnsOptions()

    init {
        testAllDns()
        autoRestoreIfNeeded()
        checkForUpdates()
        
        // DEBUG ONLY: Pre-fill test credentials for local development testing
        if (BuildConfig.DEBUG) {
            if (_username.value.isEmpty() && _password.value.isEmpty()) {
                _username.value = "565185685"
                _password.value = "069600716"
            }
        }
    }

    private fun autoRestoreIfNeeded() {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.credentials == null) {
                restoreBackup(isAuto = true)
            }
        }
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

            // Auto-select fastest working DNS if none selected yet
            if (_selectedDns.value == null) {
                val best = currentMap.filter { it.value != -1L }.minByOrNull { it.value }
                if (best != null) {
                    _selectedDns.value = best.key
                }
            }

            _isTestingDns.value = false
        }
    }

    fun login() {
        val currentUser = _username.value.trim()
        val currentPass = _password.value.trim()
        val customUrl = _url.value.trim()

        if (currentUser.isEmpty() || currentPass.isEmpty()) {
            _errorMessage.value = "Preencha usuário e senha"
            _state.value = LoginState.ERROR
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _state.value = LoginState.CONNECTING
            _errorMessage.value = null
            
            try {
                val dnsList = if (customUrl.isNotEmpty()) {
                    listOf(customUrl)
                } else if (_selectedDns.value != null) {
                    listOf(_selectedDns.value!!) + (availableDns - _selectedDns.value!!)
                } else {
                    availableDns
                }

                var lastError: String? = null
                var success = false

                for (dns in dnsList) {
                    try {
                        _errorMessage.value = "Conectando ao servidor..."
                        dnsManager.updateBaseUrl(dns)
                        val credentials = XtreamCredentials(currentUser, currentPass, dns)
                        val response = authManager.login(credentials)

                        if (response.userInfo?.auth == 1) {
                            settingsRepository.saveSessionCredentials(credentials)
                            _state.value = LoginState.SUCCESS

                            val isAlreadySaved = settingsRepository.isAccountSaved(credentials)
                            if (!isAlreadySaved) {
                                _promptSaveAccount.value = credentials
                            } else {
                                settingsRepository.saveAccountToSavedList(credentials)
                                _loginSuccess.emit(Unit)
                            }
                            success = true
                            break
                        } else {
                            val msg = response.userInfo?.message ?: "Credenciais inválidas"
                            lastError = msg
                            if (msg.contains("invalid", ignoreCase = true) || msg.contains("expired", ignoreCase = true)) {
                                break 
                            }
                        }
                    } catch (e: Exception) {
                        lastError = "Erro no servidor: ${e.message}"
                    }
                }

                if (!success) {
                    _errorMessage.value = lastError ?: "Falha ao conectar"
                    _state.value = LoginState.ERROR
                }

            } catch (e: Exception) {
                _errorMessage.value = "Erro inesperado: ${e.message}"
                _state.value = LoginState.ERROR
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreBackup(isAuto: Boolean = false) {
        viewModelScope.launch {
            if (!isAuto) _isLoading.value = true
            if (isAuto) Timber.d("Tentando recuperação automática de login...")
            else _errorMessage.value = "Buscando backup..."
            
            try {
                val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                val credentials = firebaseRepository.getBackupCredentials(deviceId)
                if (credentials != null) {
                    _username.value = credentials.username
                    _password.value = credentials.password
                    _url.value = credentials.baseUrl
                    if (!isAuto) _errorMessage.value = "Dados restaurados!"
                    else Timber.i("Login recuperado automaticamente do Firebase")
                } else {
                    if (!isAuto) _errorMessage.value = "Nenhum backup encontrado para este dispositivo."
                }
            } catch (e: Exception) {
                if (!isAuto) _errorMessage.value = "Erro ao restaurar: ${e.message}"
            } finally {
                if (!isAuto) _isLoading.value = false
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
            } catch (e: Exception) {
                Timber.e(e, "Error checking update on login screen")
                _updateInfo.value = null
            }
        }
    }
}
