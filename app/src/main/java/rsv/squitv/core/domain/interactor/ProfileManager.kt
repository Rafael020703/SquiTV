package rsv.squitv.core.domain.interactor

import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileManager @Inject constructor(private val settingsRepository: SettingsRepository) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles = _profiles.asStateFlow()
    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile = _activeProfile.asStateFlow()
    private val _profileImageUrl = MutableStateFlow<String?>(null)
    val profileImageUrl = _profileImageUrl.asStateFlow()

    init {
        managerScope.launch {
            settingsRepository.settingsFlow.map { Triple(it.profiles, it.activeProfileId, it.profileImageUrl) }
                .distinctUntilChanged().collect { (profiles, activeId, picture) ->
                    _profiles.value = profiles
                    _activeProfile.value = profiles.find { it.id == activeId } ?: profiles.firstOrNull()
                    _profileImageUrl.value = picture
                }
        }
    }

    fun selectProfile(profile: UserProfile) { managerScope.launch { settingsRepository.updateActiveProfileId(profile.id) } }
    fun addProfile(name: String, iconUrl: String? = null, isChild: Boolean = false) {
        if (name.isBlank()) return
        managerScope.launch { settingsRepository.saveLocalProfile(UserProfile(UUID.randomUUID().toString(), name.trim(), iconUrl, isChild)) }
    }
    fun deleteProfile(profileId: String) { managerScope.launch { settingsRepository.deleteLocalProfile(profileId) } }
    fun updateProfilePicture(url: String) { managerScope.launch { settingsRepository.updateProfilePicture(url) } }
}
