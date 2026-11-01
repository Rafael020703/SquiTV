package rsv.squitv.core.domain.interactor

import rsv.squitv.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    val uiZoom: Flow<Float> = settingsRepository.settingsFlow.map { it.uiZoom }
    val language: Flow<String> = settingsRepository.settingsFlow.map { it.language }
}
