package rsv.squitv.ui.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.model.XtreamCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val firebaseRepository: rsv.squitv.data.repository.FirebaseRepository
) : ViewModel() {

    val settings = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsRepository.AppSettings(null, lastSyncTimestamp = 0L, syncIntervalHours = 24)
    )

    fun updatePin(pin: String?) {
        viewModelScope.launch { settingsRepository.updatePin(pin) }
    }

    fun updateAutoPlay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoPlay(enabled) }
    }

    fun updateShowDiagnostics(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowDiagnostics(enabled) }
    }

    fun updateHideBlockedCategories(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateHideBlockedCategories(enabled) }
    }

    fun addAccount(credentials: XtreamCredentials) {
        viewModelScope.launch { settingsRepository.addAccount(credentials) }
    }

    fun removeAccount(index: Int) {
        viewModelScope.launch { settingsRepository.removeAccount(index) }
    }

    fun switchAccount(index: Int) {
        viewModelScope.launch { settingsRepository.switchAccount(index) }
    }

    fun updatePlayerEngine(engine: String) {
        viewModelScope.launch { settingsRepository.updatePlayerEngine(engine) }
    }

    fun updateBufferStrategy(strategy: String) {
        viewModelScope.launch { settingsRepository.updateBufferStrategy(strategy) }
    }

    fun updateUseOledTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateUseOledTheme(enabled) }
    }

    fun updateUiZoom(zoom: Float) {
        viewModelScope.launch { settingsRepository.updateUiZoom(zoom) }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch { settingsRepository.updateLanguage(language) }
    }

    fun updateBackgroundPlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateBackgroundPlaybackEnabled(enabled) }
    }

    fun updateProfilePicture(url: String) {
        viewModelScope.launch {
            firebaseRepository.updateProfilePicture(url)
        }
    }
}
