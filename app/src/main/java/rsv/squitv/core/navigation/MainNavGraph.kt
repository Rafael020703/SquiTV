package rsv.squitv.core.navigation

import android.app.Activity
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    var showExitDialog by remember { mutableStateOf(false) }

    // Execute actions from AppController
    LaunchedEffect(appController, navController) {
        appController.actions.collect { action ->
            when (action) {
                is AppAction.Back -> {
                    // Safety check for UI lock
                    if (appState is AppState.Locked) return@collect

                    val result = navController.popBackStack()
                    if (!result) {
                        showExitDialog = true
                    }
                }
                is AppAction.Navigate -> {
                    navController.navigate(action.route) {
                        action.popUpToRoute?.let { popRoute ->
                            popUpTo(popRoute) { inclusive = action.inclusive }
                        }
                        launchSingleTop = action.launchSingleTop
                    }
                }
                is AppAction.ExitApp -> {
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
                        qualitiesJson = null
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
                if (currentRoute.contains("Login") == false) {
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

    // Full Screen Overlays
    if (appState is AppState.Locked) {
        AppLockScreen(onUnlock = { pin -> mainViewModel.unlockApp(pin) })
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(R.string.exit_confirm_title).uppercase(),
                style = tokens.typography.headline,
                fontWeight = FontWeight.Black,
                color = tokens.colors.primary
            ) 
        },
        text = { 
            Text(
                text = stringResource(R.string.exit_confirm_msg),
                style = tokens.typography.body,
                color = tokens.colors.textSecondary
            ) 
        },
        confirmButton = {
            Button(
                onClick = onExit, 
                colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.error, contentColor = Color.White),
                shape = tokens.shapes.medium
            ) {
                Text(stringResource(R.string.exit_button).uppercase(), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text(stringResource(R.string.stay_button).uppercase(), color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold) 
            }
        },
        containerColor = tokens.colors.backgroundSecondary,
        shape = tokens.shapes.extraLarge
    )
}
