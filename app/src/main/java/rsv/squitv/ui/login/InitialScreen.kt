package rsv.squitv.ui.login

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import rsv.squitv.core.domain.state.AppState
import rsv.squitv.core.navigation.AppController
import rsv.squitv.core.navigation.Route
import rsv.squitv.core.ui.components.states.BrandedLoadingScreen
import rsv.squitv.ui.viewmodel.MainViewModel

@OptIn(UnstableApi::class)
@Composable
fun InitialScreen(
    viewModel: MainViewModel,
    appController: AppController
) {
    val appState by viewModel.appState.collectAsStateWithLifecycle()
    val loadingMessage by viewModel.loadingMessage.collectAsStateWithLifecycle()

    LaunchedEffect(appState) {
        when (appState) {
            is AppState.LoginRequired -> {
                appController.navigate(Route.Login, popUpToRoute = Route.Initial, inclusive = true)
            }
            is AppState.SyncRequired -> {
                appController.navigate(Route.Sync, popUpToRoute = Route.Initial, inclusive = true)
            }
            is AppState.Ready -> {
                appController.navigate(Route.Dashboard, popUpToRoute = Route.Initial, inclusive = true)
            }
            is AppState.Locked -> {
                // If locked, we stay here until unlocked, 
                // but AppLockScreen is usually an overlay in MainNavigation.
                // We'll keep InitialScreen active with the loading/branded look
                // while the overlay is shown.
            }
            else -> {}
        }
    }

    BrandedLoadingScreen(message = loadingMessage)
}
