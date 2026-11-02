package rsv.squitv.core.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.core.ui.theme.MeusCanaisTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import rsv.squitv.R
import rsv.squitv.appfunctions.AppFunctionActionBus
import rsv.squitv.core.ui.components.states.BrandedLoadingScreen
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.ui.viewmodel.settings.SettingsViewModel
import rsv.squitv.core.domain.state.AppState

import androidx.compose.animation.core.tween
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rsv.squitv.util.DeviceType
import rsv.squitv.util.rememberWindowInfo

@Composable
@UnstableApi
fun MainNavigation(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    appFunctionActionBus: AppFunctionActionBus,
    appController: AppController
) {
    val context = LocalContext.current
    val appState by mainViewModel.appState.collectAsStateWithLifecycle()
    val loadingMessage by mainViewModel.loadingMessage.collectAsStateWithLifecycle()
    val tokens = AppDesignSystem
    
    // A new save-state namespace avoids restoring retired EPG/player route schemas after an update.
    val navController = key("navigation-simplification-v1") { rememberNavController() }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    var showExitDialog by remember { mutableStateOf(false) }
    val externalRoute by appController.externalRoute.collectAsStateWithLifecycle()

    // Wait until bootstrap/login/sync routing has settled before consuming an external link.
    LaunchedEffect(externalRoute, appState, currentRoute) {
        val target = externalRoute ?: return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        if (route.contains("Initial") || appState is AppState.Initializing ||
            appState is AppState.SyncRequired) return@LaunchedEffect
        if (appState is AppState.Ready && (route.contains("Sync") || route.contains("Login"))) return@LaunchedEffect
        if (appState is AppState.LoginRequired && !route.contains("Login") && !route.contains("Updates")) return@LaunchedEffect
        if (appController.consumeExternalRoute(target)) {
            showExitDialog = false
            navController.navigate(target) { launchSingleTop = true }
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute?.contains("Updates") == true) mainViewModel.dismissUpdateModal()
    }

    BackHandler(enabled = showExitDialog) {
        showExitDialog = false
    }

    // Execute actions from AppController
    LaunchedEffect(appController, navController) {
        appController.actions.collect { action ->
            when (action) {
                is AppAction.Back -> {
                    if (showExitDialog) {
                        showExitDialog = false
                        return@collect
                    }

                    if (navController.previousBackStackEntry != null) {
                        showExitDialog = false
                        navController.popBackStack()
                    } else {
                        showExitDialog = true
                    }
                }
                is AppAction.Navigate -> {
                    showExitDialog = false
                    navController.navigate(action.route) {
                        action.popUpToRoute?.let { popRoute ->
                            popUpTo(popRoute) { inclusive = action.inclusive }
                        }
                        launchSingleTop = action.launchSingleTop
                    }
                }
                is AppAction.ExitApp -> {
                    showExitDialog = false
                    (context as? Activity)?.finish()
                }
            }
        }
    }


    // Sync AppFunctionActionBus with AppController
    LaunchedEffect(appFunctionActionBus) {
        appFunctionActionBus.actions.collect { action ->
            if (action is AppFunctionActionBus.Action.PlayMedia && appState is AppState.Ready) {
                appController.navigate(
                    Route.Player(
                        streamId = action.streamId,
                        streamName = action.name,
                        streamType = action.type,
                        epgId = action.epgId,
                        container = action.container,
                        sourceId = null
                    )
                )
            }
        }
    }

    // App State Orchestration - Redirects only when state changes mid-session
    LaunchedEffect(appState, currentRoute) {
        // Avoid auto-navigation while initializing or if we are at the bootstrap route
        if (appState is AppState.Initializing || currentRoute?.contains("Initial") == true || currentRoute == null) return@LaunchedEffect
        
        when (appState) {
            is AppState.LoginRequired -> {
                if (currentRoute.contains("Login") == false && currentRoute.contains("Updates") == false && currentRoute.contains("LocalLogin") == false) {
                    navController.navigate(Route.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is AppState.SyncRequired -> {
                if (currentRoute.contains("Sync") == false) {
                    navController.navigate(Route.Sync) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is AppState.Ready -> {
                if (currentRoute.contains("Sync") == true || currentRoute.contains("Login") == true) {
                    navController.navigate(Route.Dashboard) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    if (showExitDialog) {
        ExitConfirmDialog(onDismiss = { showExitDialog = false }, onExit = { (context as? Activity)?.finish() })
    }

    val windowInfo = rememberWindowInfo()
    val isTv = windowInfo.deviceType == DeviceType.TV

    // Global Immersive Fullscreen Hardening
    LaunchedEffect(currentRoute) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = tokens.colors.background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Initial,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            enterTransition = { 
                if (isTv) fadeIn(animationSpec = tween(500))
                else fadeIn(animationSpec = tween(400)) + slideInHorizontally(initialOffsetX = { 300 }) 
            },
            exitTransition = { 
                if (isTv) fadeOut(animationSpec = tween(500))
                else fadeOut(animationSpec = tween(400)) + slideOutHorizontally(targetOffsetX = { -300 }) 
            },
            popEnterTransition = { 
                if (isTv) fadeIn(animationSpec = tween(500))
                else fadeIn(animationSpec = tween(400)) + slideInHorizontally(initialOffsetX = { -300 }) 
            },
            popExitTransition = { 
                if (isTv) fadeOut(animationSpec = tween(500))
                else fadeOut(animationSpec = tween(400)) + slideOutHorizontally(targetOffsetX = { 300 }) 
            }
        ) {
            appNavGraph(
                mainViewModel = mainViewModel,
                settingsViewModel = settingsViewModel,
                libraryViewModel = libraryViewModel,
                appController = appController
            )
        }
    }
}

@Composable
fun ExitConfirmDialog(onDismiss: () -> Unit, onExit: () -> Unit) {
    val tokens = AppDesignSystem
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        rsv.squitv.core.ui.components.ExitConfirmationCard(
            title = stringResource(R.string.exit_confirm_title).uppercase(),
            message = stringResource(R.string.exit_confirm_msg),
            exitLabel = stringResource(R.string.exit_button).uppercase(),
            stayLabel = stringResource(R.string.stay_button).uppercase(),
            background = tokens.colors.backgroundSecondary, primary = tokens.colors.primary,
            secondary = tokens.colors.textSecondary, text = tokens.colors.textPrimary,
            error = tokens.colors.error, headingStyle = tokens.typography.headline,
            bodyStyle = tokens.typography.body, shape = tokens.shapes.extraLarge,
            buttonShape = tokens.shapes.medium, onDismiss = onDismiss, onExit = onExit
        )
    }
}
