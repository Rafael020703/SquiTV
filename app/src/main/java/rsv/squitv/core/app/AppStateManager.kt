package rsv.squitv.core.app

import rsv.squitv.core.domain.interactor.AuthManager
import rsv.squitv.core.domain.interactor.SyncManager
import rsv.squitv.core.domain.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStateManager @Inject constructor(
    private val authManager: AuthManager,
    private val syncManager: SyncManager
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val appState: StateFlow<AppState> = combine(
        authManager.isLoggedIn,
        syncManager.isContentReady
    ) { isLoggedIn, isContentReady ->
        val state = when {
            isLoggedIn == null -> AppState.Initializing
            isLoggedIn == false -> AppState.LoginRequired
            !isContentReady -> AppState.SyncRequired
            else -> AppState.Ready
        }
        Timber.i("AppStateManager: Calculated State -> $state (isLoggedIn=$isLoggedIn, isContentReady=$isContentReady)")
        state
    }.stateIn(appScope, SharingStarted.Eagerly, AppState.Initializing)
}
