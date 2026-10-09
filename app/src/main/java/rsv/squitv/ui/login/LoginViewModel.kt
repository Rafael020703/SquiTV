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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException

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

    private val _promptSaveCredentialEvent = MutableSharedFlow<Pair<String, String>>()
    val promptSaveCredentialEvent = _promptSaveCredentialEvent.asSharedFlow()

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
                            settingsRepository.saveCredentials(credentials)
                            _state.value = LoginState.SUCCESS
                            _promptSaveCredentialEvent.emit(Pair(currentUser, currentPass))
                            _loginSuccess.emit(Unit)
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

    fun fetchSavedCredentials(context: Context) {
        viewModelScope.launch {
            try {
                val targetContext = context.findActivity() ?: context
                val credentialManager = CredentialManager.create(targetContext)
                val getPasswordOption = GetPasswordOption()
                val request = GetCredentialRequest(listOf(getPasswordOption))
                val response = credentialManager.getCredential(targetContext, request)
                val credential = response.credential
                if (credential is PasswordCredential) {
                    val fetchedUser = credential.id
                    val fetchedPass = credential.password
                    _username.value = fetchedUser
                    _password.value = fetchedPass

                    // Lookup matching server URL from account history if available
                    val settings = settingsRepository.settingsFlow.first()
                    val cachedAccount = settings.credentials
                    if (cachedAccount != null && cachedAccount.username == fetchedUser) {
                        _url.value = cachedAccount.baseUrl
                    }
                    _errorMessage.value = null
                    Timber.i("Credential successfully retrieved from CredentialManager")
                }
            } catch (e: GetCredentialCancellationException) {
                Timber.d("User cancelled credential picker")
            } catch (e: GetCredentialException) {
                Timber.d("No saved credential found or provider unavailable: ${e.message}")
            } catch (e: Exception) {
                Timber.w("Error fetching credential: ${e.message}")
            }
        }
    }

    suspend fun saveCredentialToPasswordManager(context: Context, user: String, pass: String) {
        try {
            val targetContext = context.findActivity() ?: context
            val credentialManager = CredentialManager.create(targetContext)
            val request = CreatePasswordRequest(id = user, password = pass)
            credentialManager.createCredential(targetContext, request)
            Timber.i("Password save request completed for account")
        } catch (e: CreateCredentialCancellationException) {
            Timber.d("User cancelled saving password credential")
        } catch (e: CreateCredentialException) {
            Timber.w("CreateCredentialException: ${e.message}")
        } catch (e: Exception) {
            Timber.w("Could not save password credential: ${e.message}")
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
