package rsv.squitv

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

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
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import rsv.squitv.appfunctions.AppFunctionActionBus
import rsv.squitv.core.domain.interactor.PlaybackManager
import rsv.squitv.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import rsv.squitv.util.isMediaPipAllowed
import rsv.squitv.util.supportsMediaPip
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

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var canEnterPip = false
    var isPipActive by mutableStateOf(false)
        private set
    private var backgroundPlaybackEnabled = false
    private var pipAutoEnterEnabled = true
    private var settingsLoaded = false
    private var isStopped = false
    private var resumeOnForeground = false
    private var pipExitJob: Job? = null

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
        canEnterPip = value && supportsMediaPip()
        updatePipParams()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsMediaPip()) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(settingsLoaded && canEnterPip && pipAutoEnterEnabled &&
                        playbackManager.player?.playWhenReady == true && isMediaPipAllowed())
                    .build()
                setPictureInPictureParams(params)
            } catch (e: Exception) {
                Log.e("PIP_DEBUG", "Error setting PiP params: ${e.message}")
            }
        }
    }

    fun enterMediaPip(): Boolean {
        if (!canEnterPip || !isMediaPipAllowed() || isFinishing) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9)).build())
            } else false
        } catch (failure: RuntimeException) {
            Log.w("PIP_DEBUG", "PiP unavailable", failure)
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                backgroundPlaybackEnabled = settings.backgroundPlaybackEnabled
                pipAutoEnterEnabled = settings.pipAutoEnterEnabled
                settingsLoaded = true
                updatePipParams()
                if (isStopped) pauseIfNeeded()
            }
        }
        
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
        isStopped = false
        pipExitJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        updatePipParams()
        if (resumeOnForeground) {
            resumeOnForeground = false
            playbackManager.play()
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        isStopped = true
        pauseIfNeeded()
    }

    private fun pauseIfNeeded() {
        if (settingsLoaded && !isPipActive && !isChangingConfigurations && !backgroundPlaybackEnabled) {
            resumeOnForeground = resumeOnForeground || playbackManager.player?.playWhenReady == true
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
        if (settingsLoaded && pipAutoEnterEnabled && playbackManager.player?.playWhenReady == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterMediaPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipActive = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            pipExitJob?.cancel()
        } else {
            // Expansion resumes the activity. Closing the system window leaves it stopped.
            pipExitJob = lifecycleScope.launch {
                delay(250)
                if (!isPipActive && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !isChangingConfigurations) {
                    resumeOnForeground = false
                    playbackManager.stopAndDisconnect()
                    finish()
                }
            }
        }
    }
}
