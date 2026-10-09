@file:OptIn(UnstableApi::class)
package rsv.squitv

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import rsv.squitv.appfunctions.AppFunctionActionBus
import rsv.squitv.core.domain.interactor.PlaybackManager
import rsv.squitv.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import rsv.squitv.core.navigation.AppController
import rsv.squitv.core.navigation.MainNavigation
import rsv.squitv.core.navigation.Route
import android.view.KeyEvent
import rsv.squitv.core.debug.DebugInputTracker
import rsv.squitv.core.debug.DebugLogger
import rsv.squitv.core.ui.theme.MeusCanaisTheme
import rsv.squitv.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var canEnterPip = false
    private var isPipActive = false

    @Inject
    lateinit var appFunctionActionBus: AppFunctionActionBus

    @Inject
    lateinit var appController: AppController

    // Removed dispatchKeyEvent to use OnBackPressedDispatcher for standard consumption

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        DebugInputTracker.trackKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        DebugInputTracker.trackFocusChange("MainActivity_Window", hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }


    fun setCanEnterPip(value: Boolean) {
        canEnterPip = value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(value)
                    .build()
                setPictureInPictureParams(params)
            } catch (e: Exception) {
                Log.e("PIP_DEBUG", "Error setting PiP params: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        requestNotificationPermission()
        handleIntent(intent)

        // Modern Back Handling
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val eventId = java.util.UUID.randomUUID().toString().take(8)
                android.util.Log.i("BACK_NAV_DEBUG", "BACK_DISPATCHED | eventId=$eventId | source=OnBackPressedCallback")
                appController.goBack(eventId)
            }
        })

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        setContent {
            val dispatcherOwner = remember { 
                object : androidx.navigationevent.NavigationEventDispatcherOwner {
                    override val navigationEventDispatcher = NavigationEventDispatcher()
                }
            }
            
            DisposableEffect(dispatcherOwner) {
                onDispose {
                    dispatcherOwner.navigationEventDispatcher.dispose()
                }
            }

            val mainViewModel: MainViewModel = hiltViewModel()
            val useOledTheme by mainViewModel.useOledTheme.collectAsState()
            val uiZoom by mainViewModel.uiZoom.collectAsState()
            val language by mainViewModel.language.collectAsState()

            LaunchedEffect(language) {
                val appLocales = if (language.isBlank() || language == "auto") {
                    LocaleListCompat.forLanguageTags("")
                } else {
                    LocaleListCompat.forLanguageTags(language)
                }
                AppCompatDelegate.setApplicationLocales(appLocales)
            }
            
            MeusCanaisTheme(useOledTheme = useOledTheme, uiZoom = uiZoom) {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
                    MainNavigation(
                        mainViewModel = mainViewModel,
                        appFunctionActionBus = appFunctionActionBus,
                        appController = appController
                    )
                }
            }
        }
    }

    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        val isBgEnabled = runCatching {
            runBlocking { settingsRepository.settingsFlow.first().backgroundPlaybackEnabled }
        }.getOrDefault(false)

        if (!inPip && !isPipActive && !isBgEnabled) {
            playbackManager.pause()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            playbackManager.stopAndDisconnect()
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra("navigate_to") == "updates") {
            appController.navigate(Route.Updates)
        }
        val uri = intent?.data
        if (uri != null && uri.scheme == "squitv" && uri.host == "pair") {
            val token = uri.getQueryParameter("token")
            if (!token.isNullOrBlank()) {
                appController.navigate(Route.LocalLogin)
            }
        }
    }

    override fun finish() {
        val stackTrace = Thread.currentThread().stackTrace
        android.util.Log.e("BACK_NAV_DEBUG", "FINISH_STACK_TRACE:")
        stackTrace.forEach { frame ->
            android.util.Log.e("BACK_NAV_DEBUG", "  at $frame")
        }
        android.util.Log.e("BACK_NAV_DEBUG", "ACTIVITY_FINISH_CALLED")
        super.finish()
    }





    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (canEnterPip && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                // Support seamless transition
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipActive = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            // Hide UI elements if needed, though most is handled by Compose
        } else {
            // Restore UI if needed
        }
    }
}
