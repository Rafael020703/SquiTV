@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
package rsv.squitv.ui.content

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import rsv.squitv.MainActivity
import rsv.squitv.R
import timber.log.Timber
import rsv.squitv.core.ui.components.buttons.*
import rsv.squitv.core.ui.components.common.DigitalClock
import rsv.squitv.core.ui.components.states.BrandedLoadingScreen
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.model.EpgProgramme
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.domain.model.ContentType
import rsv.squitv.ui.content.player.*
import rsv.squitv.ui.viewmodel.PlayerUiState
import rsv.squitv.ui.viewmodel.PlayerViewModel
import rsv.squitv.util.DeviceType
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    streamName: String,
    categoryId: String? = null,
    onBack: () -> Unit,
    onNavigateToPlayer: (Int, String, String, String?) -> Unit = { _, _, _, _ -> }
) {
    val windowInfo = rememberWindowInfo()
    val isExpanded = windowInfo.isExpanded
    val isTv = windowInfo.deviceType == DeviceType.TV
    val isTablet = windowInfo.deviceType == DeviceType.TABLET

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.currentPlayer.collectAsStateWithLifecycle()
    val currentProgram by viewModel.currentProgram.collectAsStateWithLifecycle()
    val nextPrograms by viewModel.nextPrograms.collectAsStateWithLifecycle()
    val quickSwitchStreams by viewModel.quickSwitchStreams.collectAsStateWithLifecycle()
    val resizeMode by viewModel.resizeMode.collectAsStateWithLifecycle()
    val countdown by viewModel.nextEpisodeCountdown.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val subtitleSize by viewModel.subtitleSize.collectAsStateWithLifecycle()
    val zappingSessionId by viewModel.zappingSessionId.collectAsStateWithLifecycle()
    val zappingChannel by viewModel.zappingChannel.collectAsStateWithLifecycle()
    
    var isControlsVisible by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableStateOf(0L) }
    
    var showSettings by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableStateOf(0) }
    var isLocked by remember { mutableStateOf(false) }
    var digitBuffer by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Split view state
    var isSplitView by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var overlayTimerJob by remember { mutableStateOf<Job?>(null) }

    val hideOverlayImmediately: () -> Unit = {
        overlayTimerJob?.cancel()
        overlayTimerJob = null
        isControlsVisible = false
        viewModel.dismissZappingBanner()
    }

    val showOverlayWith5sTimer: () -> Unit = {
        overlayTimerJob?.cancel()
        isControlsVisible = true
        overlayTimerJob = coroutineScope.launch {
            delay(5000L)
            isControlsVisible = false
        }
    }

    val onZapping = { isNext: Boolean ->
        if (isNext) {
            viewModel.playNextChannel()
        } else {
            viewModel.playPreviousChannel()
        }
    }

    LaunchedEffect(digitBuffer) {
        if (digitBuffer.isNotEmpty()) {
            delay(2.seconds)
            viewModel.playByNumber(digitBuffer)
            digitBuffer = ""
        }
    }

    val mainActivity = context as? MainActivity

    LaunchedEffect(uiState) {
        val isPlaying = uiState is PlayerUiState.Playing
        mainActivity?.setCanEnterPip(isPlaying)
    }

    val isBgPlaybackEnabled = appSettings.backgroundPlaybackEnabled

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val isInPip = mainActivity?.isInPictureInPictureMode == true
                if (!isInPip && !isBgPlaybackEnabled) {
                    viewModel.stopPlayback()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val isInPip = mainActivity?.isInPictureInPictureMode == true
            if (!isInPip) {
                viewModel.stopPlayback()
            }
            mainActivity?.setCanEnterPip(false)
        }
    }

    MeusCanaisTheme(
        useOledTheme = appSettings.useOledTheme
    ) {
        PlayerContent(
            streamName = streamName,
            uiState = uiState,
            currentProgram = currentProgram,
            nextPrograms = nextPrograms,
            quickSwitchStreams = quickSwitchStreams,
            zappingEpg = viewModel.zappingEpg.collectAsState().value,
            isControlsVisible = isControlsVisible,
            onToggleControls = { 
                if (isControlsVisible || zappingChannel != null) {
                    hideOverlayImmediately()
                } else {
                    showOverlayWith5sTimer()
                }
            },
            onBack = { if (isSplitView) isSplitView = false else onBack() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeek = { viewModel.seekTo(it) },
            onSeekForward = { viewModel.seekForward() },
            onSeekBack = { viewModel.seekBack() },
            onNextEpisode = { viewModel.playNextEpisode() },
            onCancelCountdown = { viewModel.cancelCountdown() },
            onToggleFavorite = { viewModel.toggleFavorite() },
            onReload = { viewModel.reloadStream() },
            onToggleResizeMode = { viewModel.toggleResizeMode() },
            onSelectTrack = { g, t, ty -> viewModel.selectTrack(g, t, ty) },
            onClearTrackOverride = { viewModel.clearTrackOverride(it) },
            onSetSpeed = { viewModel.setPlaybackSpeed(it) },
            onSwitchStream = { stream ->
                val currentState = uiState as? PlayerUiState.Playing
                viewModel.playStream(
                    streamId = stream.streamId ?: 0, 
                    type = ContentType.LIVE, 
                    container = null, 
                    epgId = stream.epgChannelId,
                    qualities = currentState?.availableQualities ?: emptyMap(),
                    categoryId = categoryId,
                    displayName = stream.name,
                    streamIcon = stream.streamIcon
                )
            },
            onSetSubtitleSize = { viewModel.setSubtitleSize(it) },
            onDismissZapping = { viewModel.dismissZappingBanner() },
            onZapping = onZapping,
            onSetSleepTimer = { viewModel.setSleepTimer(it) },
            recentChannels = recentChannels,
            sleepTimer = sleepTimer,
            subtitleSize = subtitleSize,
            player = player,
            resizeMode = resizeMode,
            isLocked = isLocked,
            onToggleLock = { isLocked = !it },
            showSettings = showSettings,
            setShowSettings = { showSettings = it },
            showQualityMenu = showQualityMenu,
            setShowQualityMenu = { showQualityMenu = it },
            settingsInitialTab = settingsInitialTab,
            setSettingsInitialTab = { settingsInitialTab = it },
            focusRequester = focusRequester,
            countdown = countdown,
            isTablet = isTablet,
            isExpanded = isExpanded,
            isTv = isTv,
            updateInteraction = { lastInteraction = System.currentTimeMillis() },
            digitBuffer = digitBuffer,
            onDigitEntry = { digitBuffer += it },
            zappingSessionId = zappingSessionId,
            isSplitView = isSplitView,
            setSplitView = { isSplitView = it },
            playerEngine = appSettings.playerEngine,
            zappingChannel = zappingChannel,
            isLiveContent = viewModel.isLiveContent,
            showOverlayWith5sTimer = showOverlayWith5sTimer
        )
    }
}

@Composable
fun PlayerContent(
    streamName: String,
    uiState: PlayerUiState,
    currentProgram: EpgProgramme?,
    nextPrograms: List<EpgProgramme>,
    quickSwitchStreams: List<XtreamStream>,
    zappingEpg: Map<Int, EpgProgramme>,
    isControlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBack: () -> Unit,
    onNextEpisode: () -> Unit,
    onCancelCountdown: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReload: () -> Unit,
    onToggleResizeMode: () -> Unit,
    onSelectTrack: (Int, Int, Int) -> Unit,
    onClearTrackOverride: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSwitchStream: (XtreamStream) -> Unit,
    onZapping: (Boolean) -> Unit,
    onDismissZapping: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onSetSubtitleSize: (Float) -> Unit,
    recentChannels: List<XtreamStream>,
    sleepTimer: Int?,
    subtitleSize: Float,
    player: Player?,
    resizeMode: Int,
    isLocked: Boolean,
    onToggleLock: (Boolean) -> Unit,
    showSettings: Boolean,
    setShowSettings: (Boolean) -> Unit,
    showQualityMenu: Boolean,
    setShowQualityMenu: (Boolean) -> Unit,
    settingsInitialTab: Int,
    setSettingsInitialTab: (Int) -> Unit,
    focusRequester: FocusRequester,
    countdown: Int?,
    isTablet: Boolean,
    isExpanded: Boolean,
    isTv: Boolean,
    updateInteraction: () -> Unit,
    digitBuffer: String,
    onDigitEntry: (String) -> Unit,
    zappingSessionId: Int,
    isSplitView: Boolean,
    setSplitView: (Boolean) -> Unit,
    playerEngine: String,
    zappingChannel: XtreamStream?,
    isLiveContent: Boolean = true,
    showOverlayWith5sTimer: () -> Unit = {}
) {
    val context = LocalContext.current
    val tokens = AppDesignSystem

    val activity = context as? Activity
    val isInPip = activity?.isInPictureInPictureMode == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { 
                        updateInteraction()
                        onToggleControls() 
                    }
                )
            }
            .focusRequester(focusRequester)
            .onKeyEvent { keyEvent ->
                updateInteraction()
                val isLive = isLiveContent || (uiState as? PlayerUiState.Playing)?.isLive == true
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when {
                        // Dialogs absorb navigation
                        showSettings || showQualityMenu || isSplitView -> {
                            if (keyEvent.key == Key.Back) {
                                when {
                                    showSettings || showQualityMenu -> {
                                        setShowSettings(false)
                                        setShowQualityMenu(false)
                                    }
                                    isSplitView -> setSplitView(false)
                                }
                                true
                            } else false
                        }

                        // Live Zapping Navigation when controls are NOT visible
                        isLive && !isControlsVisible && (keyEvent.key == Key.DirectionDown || keyCode in listOf(166, 87, 92)) -> {
                            updateInteraction()
                            onZapping(true)
                            true
                        }

                        isLive && !isControlsVisible && (keyEvent.key == Key.DirectionUp || keyCode in listOf(167, 88, 93)) -> {
                            updateInteraction()
                            onZapping(false)
                            true
                        }

                        // Controls Overlay Toggle when controls are NOT visible
                        !isControlsVisible && keyEvent.key in listOf(Key.DirectionCenter, Key.Enter, Key.Spacebar) -> {
                            showOverlayWith5sTimer()
                            true
                        }

                        // VOD Seek Controls when controls are NOT visible
                        !isLive && !isControlsVisible && keyEvent.key == Key.DirectionLeft -> {
                            onSeekBack()
                            showOverlayWith5sTimer()
                            true
                        }
                        !isLive && !isControlsVisible && keyEvent.key == Key.DirectionRight -> {
                            onSeekForward()
                            showOverlayWith5sTimer()
                            true
                        }

                        // Back Button handling
                        keyEvent.key == Key.Back -> {
                            when {
                                zappingChannel != null -> { onDismissZapping(); updateInteraction(); true }
                                isControlsVisible -> { onToggleControls(); true }
                                else -> { onBack(); true }
                            }
                        }

                        // Numpad Digit Entry
                        keyCode in 7..16 -> {
                            val digit = (keyCode - 7).toString()
                            onDigitEntry(digit)
                            true
                        }

                        // Special Remote Color Keys
                        keyCode == 183 -> { onToggleFavorite(); true } // Red
                        keyCode == 184 -> { onToggleResizeMode(); true } // Green
                        keyCode == 185 -> { setSettingsInitialTab(0); setShowSettings(true); true } // Yellow
                        keyCode == 186 -> { setSettingsInitialTab(0); setShowSettings(true); true } // Blue
                        keyCode == 172 -> { setSplitView(true); true } // Guide

                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        // Video Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (player != null) {
                AndroidView(
                    factory = { 
                        PlayerView(context).apply { 
                            this.player = player
                            useController = false
                            this.resizeMode = resizeMode
                            this.keepScreenOn = true 
                            setBackgroundColor(android.graphics.Color.BLACK)
                        } 
                    },
                    update = { 
                        if (it.player != player) it.player = player
                        it.resizeMode = resizeMode
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Hide all controls, overlays, clocks and dialogs when in Picture-in-Picture mode
        if (!isInPip) {
            // Split View UI
            if (isSplitView) {
                val state = uiState as? PlayerUiState.Playing
                LiveSplitView(
                    categoryName = stringResource(R.string.tv_catalog_label), 
                    channels = quickSwitchStreams,
                    selectedChannelId = state?.streamId ?: 0,
                    currentProgram = currentProgram,
                    onChannelSelect = onSwitchStream
                )
            }

            // Full Screen Overlay
            if (!isSplitView && uiState is PlayerUiState.Playing) {
                val state = uiState as PlayerUiState.Playing
                if (state.isLive) {
                    LiveBottomOverlay(
                        streamName = state.name ?: streamName,
                        streamIcon = state.streamIcon,
                        currentProgram = currentProgram,
                        isControlsVisible = isControlsVisible,
                        isReconnecting = state.isReconnecting,
                        onShowSettings = { setSettingsInitialTab(0); setShowSettings(true) },
                        onToggleResize = onToggleResizeMode,
                        onShowAudio = { setSettingsInitialTab(1); setShowSettings(true) },
                        onShowSubtitles = { setSettingsInitialTab(2); setShowSettings(true) },
                        onShowQuality = { setSettingsInitialTab(0); setShowSettings(true) }
                    )

                    AnimatedVisibility(
                        visible = isControlsVisible,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        ZappingEdgeControl(
                            onNext = { onZapping(true) },
                            onPrev = { onZapping(false) }
                        )
                    }
                } else {
                    AnimatedVisibility(visible = isControlsVisible, enter = fadeIn(), exit = fadeOut()) {
                        PlayerControlOverlay(
                            streamName = state.name ?: streamName,
                            streamIcon = state.streamIcon,
                            uiState = state,
                            currentProgram = currentProgram,
                            nextPrograms = nextPrograms,
                            recentChannels = recentChannels,
                            focusRequester = focusRequester,
                            isExpanded = isExpanded,
                            isTv = isTv,
                            onBack = onBack,
                            onTogglePlayPause = onTogglePlayPause,
                            onSeek = onSeek,
                            onSeekForward = onSeekForward,
                            onSeekBack = onSeekBack,
                            onNextEpisode = onNextEpisode,
                            onShowSettings = { setSettingsInitialTab(0); setShowSettings(true) },
                            onShowChannels = { setSplitView(true) },
                            onShowQuality = { setSettingsInitialTab(0); setShowSettings(true) },
                            onShowSpeed = { },
                            onToggleFavorite = onToggleFavorite,
                            onToggleResizeMode = onToggleResizeMode,
                            onToggleLock = { onToggleLock(true) },
                            onReload = onReload,
                            onEnterPip = { },
                            onSwitchStream = onSwitchStream
                        )
                    }
                }
            }

            // Zapping Banner
            ZappingBanner(
                isVisible = zappingChannel != null,
                channel = zappingChannel,
                program = zappingEpg.get(zappingChannel?.streamId),
                isExpanded = isExpanded
            )

            // Reconnection Discrete Badge
            AnimatedVisibility(
                visible = (uiState as? PlayerUiState.Playing)?.isReconnecting == true && !isControlsVisible,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
            ) {
                ReconnectingBadge()
            }

            // Loading and Buffering indicators
            if (uiState is PlayerUiState.Loading) {
                BrandedLoadingScreen(message = stringResource(R.string.starting_playback))
            }
            
            // Countdown for Next Episode
            if (countdown != null && !isControlsVisible) {
                Box(modifier = Modifier.fillMaxSize().padding(tokens.spacing.extraLarge), contentAlignment = Alignment.BottomEnd) {
                     NextEpisodeCountdown(
                         seconds = countdown, 
                         onWatchNow = onNextEpisode, 
                         onCancel = onCancelCountdown
                     )
                }
            }

            // Settings Dialog (TV-First)
            if ((showSettings || showQualityMenu) && uiState is PlayerUiState.Playing) { 
                val state = uiState as PlayerUiState.Playing
                PlayerSettingsDialog(
                    tracks = state.tracks, 
                    sleepTimer = sleepTimer,
                    subtitleSize = subtitleSize,
                    initialTab = settingsInitialTab,
                    onDismiss = { 
                        setShowSettings(false)
                        setShowQualityMenu(false)
                    }, 
                    onSelectTrack = onSelectTrack, 
                    onClearOverride = onClearTrackOverride,
                    onSetSleepTimer = onSetSleepTimer,
                    onSetSubtitleSize = onSetSubtitleSize
                ) 
            }

            // Digit Entry Overlay
            AnimatedVisibility(
                visible = digitBuffer.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 1.1f),
                modifier = Modifier.align(Alignment.TopEnd).padding(48.dp)
            ) {
                Surface(
                    color = tokens.colors.primary,
                    shape = tokens.shapes.medium,
                    tonalElevation = 8.dp
                ) {
                    Text(
                        text = digitBuffer,
                        style = tokens.typography.display,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                    )
                }
            }

            // Global Clock in Player
            if (isControlsVisible) {
                DigitalClock(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(tokens.spacing.extraLarge),
                    textStyle = tokens.typography.title.copy(color = tokens.colors.textPrimary.copy(alpha = 0.8f)),
                    format = "HH:mm"
                )
            }
        }
    }
}

@Composable
fun NextEpisodeCountdown(seconds: Int, onWatchNow: () -> Unit, onCancel: () -> Unit) {
    val tokens = AppDesignSystem
    Surface(
        color = tokens.colors.backgroundSecondary.copy(alpha = 0.9f),
        shape = tokens.shapes.large,
        border = BorderStroke(1.dp, tokens.colors.border.copy(alpha = 0.2f)),
        modifier = Modifier.width(320.dp)
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.large)) {
            Text(
                stringResource(R.string.next_episode_in_title).uppercase(), 
                style = tokens.typography.caption, 
                color = tokens.colors.textSecondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.seconds_label, seconds).uppercase(), 
                style = tokens.typography.headline, 
                fontWeight = FontWeight.Black, 
                color = tokens.colors.primary
            )
            Spacer(Modifier.height(tokens.spacing.medium))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AppButton(
                    text = "ASSISTIR AGORA", 
                    onClick = onWatchNow, 
                    modifier = Modifier.weight(1f)
                )
                AppIconButton(
                    icon = Icons.Rounded.Close, 
                    onClick = onCancel,
                    tint = tokens.colors.textSecondary
                )
            }
        }
    }
}
