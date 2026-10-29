package rsv.squitv.core.domain.interactor

import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _unlockedCategories = MutableStateFlow<Set<String>>(emptySet())
    val unlockedCategories: StateFlow<Set<String>> = _unlockedCategories.asStateFlow()

    private val _blockedCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedCategoryIds: StateFlow<Set<String>> = _blockedCategoryIds.asStateFlow()

    private var appPin: String? = null

    init {
        managerScope.launch {
            settingsRepository.settingsFlow.map { settings ->
                Triple(settings.appLockEnabled, settings.appPin,
                    settings.credentials?.let { rsv.squitv.data.model.SettingsBackupCodec.accountKey(it) })
            }.distinctUntilChanged().collect { (enabled, pin, _) ->
                appPin = pin
                _isAppLocked.value = enabled && pin != null
                _unlockedCategories.value = emptySet()
                updateBlockedCategories()
            }
        }
        managerScope.launch {
            settingsRepository.settingsFlow.map { Triple(it.lastSyncLive, it.lastSyncVod, it.lastSyncSeries) }
                .distinctUntilChanged().collect { updateBlockedCategories() }
        }
    }

    fun unlockApp(pin: String): Boolean {
        if (pin == appPin) {
            _isAppLocked.value = false
            return true
        }
        return false
    }

    fun unlockCategory(catId: String) {
        _unlockedCategories.value = _unlockedCategories.value + catId
        updateBlockedCategories()
    }

    private fun updateBlockedCategories() {
        managerScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val creds = settings.credentials ?: return@launch
            val unlocked = _unlockedCategories.value
            val categories = catalogRepository.getLiveCategories(creds) + 
                            catalogRepository.getVodCategories(creds) + 
                            catalogRepository.getSeriesCategories(creds)
            
            _blockedCategoryIds.value = categories
                .filter { it.isLocked && it.id !in unlocked }
                .map { it.id }
                .toSet()
        }
    }
}
