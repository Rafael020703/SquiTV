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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsRepository.AppSettings(null, lastSyncTimestamp = 0L, syncIntervalHours = 24)
    )

    fun updateGroupLiveChannels(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateGroupLiveChannels(enabled) }
    }

    fun updatePreferredChannelQuality(quality: String) {
        viewModelScope.launch { settingsRepository.updatePreferredChannelQuality(quality) }
    }

    fun updateAutoPlay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoPlay(enabled) }
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

    fun updateUiZoom(zoom: Float) {
        viewModelScope.launch { settingsRepository.updateUiZoom(zoom) }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch { settingsRepository.updateLanguage(language) }
    }

    fun updateBackgroundPlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateBackgroundPlaybackEnabled(enabled) }
    }

    fun updatePipAutoEnterEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updatePipAutoEnterEnabled(enabled) }
    }

    fun saveCustomDisplayName(username: String, baseUrl: String? = null, name: String?) {
        viewModelScope.launch { settingsRepository.updateCustomDisplayName(username, baseUrl, name) }
    }

    fun updateHiddenSections(hidden: Set<String>) {
        viewModelScope.launch { settingsRepository.updateHiddenSections(hidden) }
    }

    fun resetHiddenSections() {
        viewModelScope.launch { settingsRepository.resetHiddenSections() }
    }

    fun updateProfilePicture(url: String) {
        viewModelScope.launch {
            settingsRepository.updateProfilePicture(url)
        }
    }
}
