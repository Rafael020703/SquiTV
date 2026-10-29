package rsv.squitv.ui.viewmodel.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rsv.squitv.core.domain.interactor.AuthManager
import rsv.squitv.core.domain.interactor.DnsManager
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.usecase.SyncDataUseCase
import rsv.squitv.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class DnsTesterUiState(
    val isTesting: Boolean = false,
    val isInternetConnected: Boolean = true,
    val dnsStatusMap: Map<String, Long> = emptyMap(),
    val currentDns: String? = null,
    val availableDns: List<String> = emptyList(),
    val showSwitchDialog: Boolean = false,
    val confirmDnsToSwitch: String? = null,
    val isSwitching: Boolean = false,
    val isSyncing: Boolean = false,
    val switchStatusMessage: String? = null,
    val syncSuccess: Boolean? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class DnsTesterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dnsManager: DnsManager,
    private val settingsRepository: SettingsRepository,
    private val authManager: AuthManager,
    private val syncDataUseCase: SyncDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DnsTesterUiState())
    val uiState: StateFlow<DnsTesterUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val available = dnsManager.getDnsOptions()
            val settings = settingsRepository.settingsFlow.first()
            val activeDns = settings.credentials?.baseUrl

            _uiState.update {
                it.copy(
                    availableDns = available,
                    currentDns = activeDns
                )
            }

            testAllDns()
        }
    }

    fun normalizeUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url.trim()
            .lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
    }

    fun isCurrentDns(dnsUrl: String): Boolean {
        val current = _uiState.value.currentDns ?: return false
        return normalizeUrl(dnsUrl) == normalizeUrl(current)
    }

    fun testAllDns() {
        if (_uiState.value.isTesting) return

        viewModelScope.launch {
            val hasInternet = NetworkUtils.isNetworkConnected(context)

            if (!hasInternet) {
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        isInternetConnected = false,
                        dnsStatusMap = emptyMap(),
                        errorMessage = null
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isTesting = true,
                    isInternetConnected = true,
                    dnsStatusMap = emptyMap(),
                    errorMessage = null
                )
            }

            val available = _uiState.value.availableDns.ifEmpty { dnsManager.getDnsOptions() }
            val currentMap = mutableMapOf<String, Long>()

            try {
                val deferreds = available.map { dnsUrl ->
                    async(Dispatchers.IO) {
                        val ping = dnsManager.testDns(dnsUrl)
                        dnsUrl to ping
                    }
                }

                deferreds.forEach { deferred ->
                    val (dnsUrl, ping) = deferred.await()
                    currentMap[dnsUrl] = ping
                    _uiState.update { state ->
                        state.copy(dnsStatusMap = currentMap.toMap())
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error testing DNS servers")
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isTesting = false) }
            }
        }
    }

    fun openSwitchDialog() {
        _uiState.update { it.copy(showSwitchDialog = true) }
    }

    fun dismissSwitchDialog() {
        _uiState.update { it.copy(showSwitchDialog = false, confirmDnsToSwitch = null) }
    }

    fun selectDnsToConfirm(dnsUrl: String) {
        _uiState.update { it.copy(confirmDnsToSwitch = dnsUrl) }
    }

    fun cancelConfirmSwitch() {
        _uiState.update { it.copy(confirmDnsToSwitch = null) }
    }

    fun performSwitchDns(newDnsUrl: String) {
        if (_uiState.value.isSwitching || _uiState.value.isSyncing) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSwitching = true,
                    showSwitchDialog = false,
                    confirmDnsToSwitch = null,
                    switchStatusMessage = "Alterando DNS...",
                    syncSuccess = null
                )
            }

            val settings = settingsRepository.settingsFlow.first()
            val oldCreds = settings.credentials

            if (oldCreds == null) {
                _uiState.update {
                    it.copy(
                        isSwitching = false,
                        errorMessage = "Credenciais não encontradas"
                    )
                }
                return@launch
            }

            try {
                // 1. Update Persistent Credentials with new DNS
                val updatedCreds = oldCreds.copy(baseUrl = newDnsUrl)
                settingsRepository.saveCredentials(updatedCreds)

                // 2. Propagate to Retrofit client
                dnsManager.updateBaseUrl(newDnsUrl)

                // 3. Re-authenticate on new DNS
                var authSuccess = false
                try {
                    val loginRes = authManager.login(updatedCreds)
                    authSuccess = loginRes.userInfo?.auth == 1
                } catch (e: Exception) {
                    Timber.w(e, "Login check failed after switching DNS")
                    authSuccess = false
                }

                // 4. ROLLBACK IF AUTH/CONNECTION FAILED
                if (!authSuccess) {
                    Timber.w("Auth failed on $newDnsUrl. Rolling back to ${oldCreds.baseUrl}")
                    settingsRepository.saveCredentials(oldCreds)
                    dnsManager.updateBaseUrl(oldCreds.baseUrl)

                    _uiState.update {
                        it.copy(
                            isSwitching = false,
                            isSyncing = false,
                            syncSuccess = false,
                            switchStatusMessage = "Falha na conexão/autenticação no novo DNS. A alteração foi desfeita por segurança."
                        )
                    }
                    return@launch
                }

                settingsRepository.preserveCustomizationForDnsChange(oldCreds, updatedCreds)

                // 5. IF AUTH SUCCEEDED, UPDATE ACTIVE STATE & START SYNC
                _uiState.update {
                    it.copy(
                        currentDns = newDnsUrl,
                        isSwitching = false,
                        isSyncing = true,
                        switchStatusMessage = "Sincronizando lista..."
                    )
                }

                var syncError: String? = null

                syncDataUseCase(force = true).collect { syncResult ->
                    if (syncResult.isComplete) {
                        syncError = syncResult.error
                    }
                }

                if (syncError == null) {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncSuccess = true,
                            switchStatusMessage = "DNS alterado e lista atualizada com sucesso."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncSuccess = false,
                            switchStatusMessage = "DNS alterado, mas não foi possível sincronizar a lista."
                        )
                    }
                }

                // Re-test DNS statuses to reflect new active state
                testAllDns()

            } catch (e: Exception) {
                Timber.e(e, "Error switching DNS")
                // Rollback on unexpected exception
                settingsRepository.saveCredentials(oldCreds)
                dnsManager.updateBaseUrl(oldCreds.baseUrl)

                _uiState.update {
                    it.copy(
                        isSwitching = false,
                        isSyncing = false,
                        syncSuccess = false,
                        errorMessage = e.message ?: "Erro ao alterar DNS"
                    )
                }
            }
        }
    }
}
