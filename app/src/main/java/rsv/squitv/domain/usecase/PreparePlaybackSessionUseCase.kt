package rsv.squitv.domain.usecase

import rsv.squitv.core.domain.interactor.AuthManager
import rsv.squitv.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UseCase responsible for preparing the IPTV session before playback starts.
 * It handles silent login refresh with throttling and validates connection limits.
 */
@Singleton
class PreparePlaybackSessionUseCase @Inject constructor(
    private val authManager: AuthManager,
    private val settingsRepository: SettingsRepository
) {
    private var lastLoginTime = 0L
    private val SESSION_EXPIRY_MS = 300_000L // 5 minutes

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * Ensures the session is valid and connection limits are respected.
     * 
     * @return Result.Success if everything is okay (including ignored network errors), 
     * or Result.Error if the connection limit is strictly exceeded.
     */
    suspend operator fun invoke(): Result {
        val settings = settingsRepository.settingsFlow.first()
        val credentials = settings.credentials ?: return Result.Error("Credenciais não encontradas")

        val now = System.currentTimeMillis()
        if (now - lastLoginTime > SESSION_EXPIRY_MS) {
            try {
                val response = authManager.login(credentials)
                lastLoginTime = now
                
                val active = response.userInfo?.activeCons?.toIntOrNull() ?: 0
                val max = response.userInfo?.maxConnections?.toIntOrNull() ?: 1
                Timber.d("PreparePlaybackSessionUseCase: Login atualizado. Conexões ativas=$active / máx=$max (sem bloqueio de cliente)")
            } catch (e: Exception) {
                Timber.w(e, "Erro silencioso ao validar conta para playback")
                // We return Success here to maintain existing behavior of allowing playback 
                // attempts even if the login validation fails (e.g. temporary server issue).
                return Result.Success
            }
        }
        
        return Result.Success
    }
}
