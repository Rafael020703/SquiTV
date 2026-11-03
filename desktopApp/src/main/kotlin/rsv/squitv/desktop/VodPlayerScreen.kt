package rsv.squitv.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import rsv.squitv.core.ui.components.player.*
import rsv.squitv.desktop.player.WindowsLivePlayer
import rsv.squitv.shared.xtream.OnDemandPlayback
import rsv.squitv.shared.xtream.ServerCredentials
import rsv.squitv.ui.content.player.PlayerOverlayController
import rsv.squitv.ui.content.player.PlayerSeekController

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun VodPlayerScreen(session: VodSession, account: ServerCredentials, controller: DesktopController,
    settings: DesktopPreferencesStore, fullscreen: Boolean, toggleFullscreen: () -> Unit,
    registerPlayer: (WindowsLivePlayer?) -> Unit, registerInput: (((KeyEvent) -> Boolean)?) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val engine = remember { WindowsLivePlayer(onDemand = true) }
    val overlay = remember { PlayerOverlayController() }
    val status by engine.state.collectAsState()
    val preferences by settings.state.collectAsState()
    val preferencesLoaded by settings.loaded.collectAsState()
    val preferencesError by settings.error.collectAsState()
    val accountScope = remember(account) { liveCategoryScope(account) }
    val catalogKind = if (session.series) CatalogKind.SERIES else CatalogKind.MOVIES
    val isFavorite = preferences.isCatalogFavorite(accountScope, catalogKind, session.catalogId)
    val historyError = controller.watchHistory?.error?.collectAsState()?.value
    var controls by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<VodMenu?>(null) }
    var holding by remember { mutableStateOf(false) }
    var keyboardSeeking by remember { mutableStateOf(false) }
    var dragging by remember(session.id) { mutableStateOf<Float?>(null) }
    var seekFocused by remember { mutableStateOf(false) }
    var seekRevision by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var resumeTarget by remember(session.id, session.series, retry) { mutableStateOf<Long?>(null) }
    var confirmingResume by remember(session.id, session.series, retry) { mutableStateOf<Long?>(null) }
    var resumeWarning by remember(session.id, session.series, retry) { mutableStateOf<String?>(null) }
    var expectedGeneration by remember(session.id, session.series, retry) { mutableLongStateOf(-1) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }
    val pauseFocus = remember { FocusRequester() }
    val showControls: () -> Unit = { overlay.showOverlayWithTimer(scope) { controls = it } }
    val interaction: () -> Unit = { overlay.updateInteraction(scope) { controls = it } }
    val manualSeek: () -> Unit = { resumeTarget = null; confirmingResume = null; resumeWarning = null }
    val seek = remember(session.id, session.series, status.durationMs) {
        PlayerSeekController(status.durationMs, { engine.seekTo(it); seekRevision++ },
            { interaction(); seekRevision++ }, { keyboardSeeking = it })
    }
    DisposableEffect(seek) { onDispose { seek.cancelSeek() } }
    val hideControls: () -> Unit = { seek.cancelSeek(); dragging = null; holding = false; overlay.hideOverlayImmediately { controls = it } }
    val closeMenu: () -> Unit = { menu = null; showControls() }
    val openMenu: (VodMenu) -> Unit = { hideControls(); menu = it; if (it == VodMenu.SETTINGS) engine.refreshTracks() }
    val input: (KeyEvent) -> Boolean = { event ->
        when {
            event.key == Key.F11 || event.key == Key.F -> {
                if (event.type == KeyEventType.KeyDown) toggleFullscreen()
                true
            }
            event.key == Key.Escape || event.key == Key.Back -> {
                if (event.type == KeyEventType.KeyUp) when {
                    menu != null -> closeMenu()
                    else -> {
                        seek.cancelSeek(); dragging = null; holding = false
                        controller.catalog.stopPlayback()
                    }
                }
                true
            }
            menu != null -> false
            event.key == Key.Spacebar -> {
                if (event.type == KeyEventType.KeyUp) { engine.togglePause(); showControls() }
                true
            }
            event.key in listOf(Key.DirectionLeft, Key.DirectionRight) && (seekFocused || !controls) -> {
                if (event.type == KeyEventType.KeyDown && status.seekable && status.durationMs > 0) {
                    manualSeek()
                    showControls()
                    seek.handleDpadSeek(status.timeMs, if (event.key == Key.DirectionLeft) -10_000 else 10_000, scope)
                }
                true
            }
            session.series && event.key in listOf(Key.PageUp, Key.PageDown) -> {
                if (event.type == KeyEventType.KeyUp) controller.catalog.nextEpisode(if (event.key == Key.PageUp) -1 else 1)
                true
            }
            !controls && event.key in listOf(Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown) -> {
                if (event.type == KeyEventType.KeyUp) showControls()
                true
            }
            else -> { interaction(); false }
        }
    }
    val currentInput by rememberUpdatedState(input)
    val currentSession by rememberUpdatedState(session)
    val currentAccount by rememberUpdatedState(account)
    val currentResumeTarget by rememberUpdatedState(resumeTarget)
    val currentConfirmingResume by rememberUpdatedState(confirmingResume)
    val currentSeek by rememberUpdatedState(seek)
    val currentExpectedGeneration by rememberUpdatedState(expectedGeneration)
    DisposableEffect(engine) {
        val save: () -> Unit = {
            if (currentExpectedGeneration >= 0 && engine.state.value.playbackGeneration == currentExpectedGeneration &&
                (engine.state.value.ended || currentResumeTarget == null && currentConfirmingResume == null &&
                    currentSeek.pendingConfirmedSeek == null && currentSeek.seekTarget == null))
                controller.saveWatchProgress(currentAccount, currentSession, engine.state.value, force = true)
        }
        controller.catalog.beforePlaybackChange = save
        registerPlayer(engine); registerInput { currentInput(it) }
        onDispose {
            save()
            if (controller.catalog.beforePlaybackChange === save) controller.catalog.beforePlaybackChange = null
            registerInput(null); registerPlayer(null); overlay.hideOverlayImmediately(); engine.close()
        }
    }
    LaunchedEffect(session.id, session.series, account, retry) {
        val saved = liveCategoryScope(account)?.let { controller.watchHistory?.resume(it, session) }
        menu = null; seek.cancelSeek(); dragging = null; showControls()
        engine.play(OnDemandPlayback.address(account, session.series, session.id, session.extension), session.id)
        expectedGeneration = engine.state.value.playbackGeneration
        resumeTarget = saved?.positionMs
    }
    LaunchedEffect(session.id, session.series, account, engine) {
        engine.state.collect { latest ->
            if (currentSession.id == session.id && currentSession.series == session.series && currentAccount == account &&
                currentExpectedGeneration >= 0 && latest.playbackGeneration == currentExpectedGeneration &&
                (latest.ended || currentResumeTarget == null && currentConfirmingResume == null &&
                    currentSeek.pendingConfirmedSeek == null && currentSeek.seekTarget == null))
                controller.saveWatchProgress(account, session, latest, force = latest.paused || latest.ended)
        }
    }
    LaunchedEffect(resumeTarget, status.sourceId, status.playbackGeneration, expectedGeneration, status.seekable, status.durationMs, status.phase) {
        val target = resumeTarget
        if (target != null && status.sourceId == session.id && status.playbackGeneration == expectedGeneration && status.seekable && status.durationMs > 0 &&
            status.phase in listOf("Reproduzindo", "Pausado")) {
            resumeTarget = null
            if (target in 5000 until (status.durationMs - 2000)) {
                seek.handleDpadSeek(status.timeMs, target - status.timeMs, scope)
                seek.commitSeek()
                confirmingResume = target
            }
        }
    }
    LaunchedEffect(status.timeMs, status.ended, status.playbackGeneration, confirmingResume) {
        val target = confirmingResume
        if (target != null && status.playbackGeneration == expectedGeneration &&
            (status.ended || status.timeMs >= target - 2000)) {
            confirmingResume = null; resumeWarning = null
        }
    }
    LaunchedEffect(confirmingResume) {
        if (confirmingResume != null) {
            delay(3000)
            if (confirmingResume != null) resumeWarning = "A retomada ainda não foi confirmada. Use a barra ou tente novamente; sua posição salva foi preservada."
        }
    }
    LaunchedEffect(preferences.volume, preferences.muted) {
        engine.setVolume(preferences.volume)
        if (engine.state.value.muted != preferences.muted) engine.toggleMute()
    }
    LaunchedEffect(menu, keyboardSeeking, dragging != null, holding, status.error, status.paused, status.ended) {
        overlay.onSeekingStateChanged(menu != null || keyboardSeeking || dragging != null || holding ||
            status.error != null || status.paused || status.ended, scope) { controls = it }
        if (menu == null && (status.error != null || status.ended)) showControls()
    }
    LaunchedEffect(sleepMinutes) { sleepMinutes?.let { delay(it * 60_000L); controller.catalog.stopPlayback() } }
    LaunchedEffect(status.timeMs, seek) {
        val before = seek.pendingConfirmedSeek
        seek.onPositionUpdate(status.timeMs)
        if (before != seek.pendingConfirmedSeek) seekRevision++
    }
    // Keep the target while waiting for the native position, with a bounded wait on failure.
    val pending = remember(seekRevision, seek) { seek.pendingConfirmedSeek }
    LaunchedEffect(pending) { if (pending != null) { delay(2500); seek.cancelSeek() } }
    LaunchedEffect(status.ended, status.sourceId, status.playbackGeneration, expectedGeneration, session.id, preferences.autoPlayEpisodes) {
        if (status.ended && status.sourceId == session.id && status.playbackGeneration == expectedGeneration && preferences.autoPlayEpisodes && session.series &&
            session.episodeIndex in 0 until session.episodes.lastIndex) controller.catalog.nextEpisode(1)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        NativeVideoSurface(engine, Modifier.fillMaxSize(),
            controlsVisible = controls, pointerRevealEnabled = menu == null,
            onPointerMove = { if (menu == null) showControls() },
            onClick = { if (menu != null) closeMenu() else if (controls) hideControls() else showControls() })
    }
    NativePlayerLayer(controls && menu == null, PlayerLayerPlacement.TOP, heightDp = 112,
        interactive = false) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = .6f), Color.Transparent))).padding(24.dp)) {
            PlayerTitle(session.title, MaterialTheme.typography.titleLarge, palette.onSurface)
        }
    }
    NativePlayerLayer(controls && menu == null, PlayerLayerPlacement.BOTTOM,
        heightDp = if (status.error != null || historyError != null || resumeWarning != null || preferencesError != null) 320 else 260, onKey = input,
        onInputCancelled = { holding = false; dragging = null; seek.cancelSeek() }) {
        LaunchedEffect(controls) { if (controls) { withFrameNanos { }; pauseFocus.requestFocus() } }
        VodPlayerCard(
            background = palette.background,
            modifier = Modifier
                .fillMaxSize()
                .onPointerEvent(PointerEventType.Move) { interaction() }
                .onPointerEvent(PointerEventType.Press) { holding = true; interaction() }
                .onPointerEvent(PointerEventType.Release) { holding = false; interaction() },
            timeline = {
                if (status.durationMs > 0) {
                    val position = dragging?.let { (it * status.durationMs).toLong() } ?: seek.seekTarget ?: pending ?: status.timeMs
                    PlayerTimeline("${mediaTime(position)} / ${mediaTime(status.durationMs)}",
                        keyboardSeeking || dragging != null, palette.primary, palette.onSurface,
                        MaterialTheme.typography.labelLarge, MaterialTheme.typography.labelSmall) {
                        Slider((position.toDouble() / status.durationMs).toFloat().coerceIn(0f, 1f),
                            onValueChange = { manualSeek(); seek.cancelSeek(); dragging = it; interaction() },
                            onValueChangeFinished = {
                                dragging?.let {
                                    seek.cancelSeek()
                                    seek.handleDpadSeek(status.timeMs, (it * status.durationMs).toLong() - status.timeMs, scope)
                                    seek.commitSeek()
                                }
                                dragging = null
                            }, enabled = status.seekable, modifier = Modifier.fillMaxWidth().height(24.dp)
                                .onFocusChanged { seekFocused = it.isFocused; if (!it.isFocused && seek.seekTarget != null) seek.commitSeek() },
                            colors = SliderDefaults.colors(thumbColor = palette.primary, activeTrackColor = palette.primary,
                                inactiveTrackColor = palette.surface.copy(alpha = .3f)))
                    }
                } else Text("Duração ainda não informada", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            },
            actions = {
                CenteredPlayerActions(16.dp, Modifier.fillMaxWidth().height(72.dp), left = {
                    val favoriteInteraction = rememberVisualInteraction()
                    PlayerRoundButton(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos", {
                            accountScope?.let { account -> settings.update { it.toggleCatalogFavorite(account, catalogKind, session.catalogId) } }
                            interaction()
                        }, if (favoriteInteraction.emphasized) palette.primary else palette.surfaceVariant,
                        if (favoriteInteraction.emphasized) Color.Black else if (isFavorite) palette.error else palette.onSurface,
                        if (favoriteInteraction.emphasized) palette.primary else palette.outline, 1.dp,
                        interactionSource = favoriteInteraction.source,
                        enabled = preferencesLoaded && accountScope != null && session.catalogId > 0)
                    if (session.series) PlayerActionButton(Icons.Rounded.SkipPrevious, { controller.catalog.nextEpisode(-1) },
                        "Episódio anterior", enabled = session.episodeIndex > 0)
                    PlayerActionButton(Icons.Rounded.Replay10,
                        { if (status.seekable) { manualSeek(); seek.handleDpadSeek(status.timeMs, -10_000, scope) } },
                        "Voltar 10 segundos", enabled = status.seekable)
                }, center = {
                    PlayerPauseButton(!status.paused, { engine.togglePause(); interaction() },
                        Modifier.focusRequester(pauseFocus))
                }, right = {
                    PlayerActionButton(Icons.Rounded.Forward10,
                        { if (status.seekable) { manualSeek(); seek.handleDpadSeek(status.timeMs, 10_000, scope) } },
                        "Avançar 10 segundos", enabled = status.seekable)
                    if (session.series) PlayerActionButton(Icons.Rounded.SkipNext, { controller.catalog.nextEpisode(1) },
                        "Próximo episódio", enabled = session.episodeIndex in 0 until session.episodes.lastIndex)
                    Spacer(Modifier.width(12.dp))
                    PlayerIcon(Icons.Rounded.Settings, "Configurações do player", { openMenu(VodMenu.SETTINGS) })
                })
            },
            extraControls = {
                status.error?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                historyError?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                preferencesError?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                resumeWarning?.let { Text(it, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PlayerIcon(if (status.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        "Silenciar/ativar som", { settings.update { it.copy(muted = !it.muted) }; interaction() })
                    var volume by remember(preferences.volume) { mutableFloatStateOf(preferences.volume.toFloat()) }
                    Slider(volume, { volume = it; engine.setVolume(it.toInt()); interaction() }, valueRange = 0f..100f,
                        onValueChangeFinished = { settings.update { it.copy(volume = volume.toInt()) } }, modifier = Modifier.width(120.dp))
                    Spacer(Modifier.weight(1f))
                    PlayerIcon(Icons.Rounded.Refresh, "Tentar novamente", {
                        scope.launch {
                            controller.catalog.beforePlaybackChange?.invoke()
                            controller.awaitWatchSave()
                            retry++; interaction()
                        }
                    })
                    if (session.series) PlayerIcon(Icons.Rounded.VideoLibrary, "Episódios", { openMenu(VodMenu.EPISODES) })
                    PlayerIcon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Alternar tela cheia (F11)", toggleFullscreen)
                    PlayerIcon(Icons.Rounded.Close, "Encerrar reprodução", controller.catalog::stopPlayback)
                }
            }
        )
    }
    NativePlayerLayer(menu != null, if (menu == VodMenu.EPISODES) PlayerLayerPlacement.START else PlayerLayerPlacement.CENTER,
        heightDp = if (menu == VodMenu.EPISODES) null else 450,
        widthDp = if (menu == VodMenu.EPISODES) 380 else 760, onKey = input) {
        when (menu) {
            VodMenu.SETTINGS -> LiveSettingsPanel(status, engine, sleepMinutes, { sleepMinutes = it }, closeMenu)
            VodMenu.EPISODES -> VodEpisodesPanel(session, controller, closeMenu)
            null -> Unit
        }
    }
}

@Composable
private fun VodEpisodesPanel(session: VodSession, controller: DesktopController, closeMenu: () -> Unit) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(session.episodeIndex.coerceAtLeast(0))
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; selectedFocus.requestFocus() }
    Column(Modifier.fillMaxSize().background(palette.surface.copy(alpha = .95f))) {
        Surface(Modifier.fillMaxWidth().height(80.dp), color = Color.White.copy(alpha = .05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .05f))) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("EPISÓDIOS", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    color = palette.onSurface, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(closeMenu) { Icon(Icons.Rounded.Close, "Fechar episódios", tint = palette.onSurfaceVariant) }
            }
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(session.episodes, key = { _, item -> requireNotNull(item.id) }) { index, episode ->
                    val selected = index == session.episodeIndex
                    val interaction = rememberVisualInteraction()
                    val text by animateColorAsState(if (interaction.emphasized) Color.Black else if (selected) palette.primary else palette.onSurface)
                    val background by animateColorAsState(if (interaction.emphasized) palette.primary else if (selected) palette.primary.copy(alpha = .12f) else Color.Transparent)
                    PlayerEpisodeRow(
                        episodeNum = episode.episodeNum ?: "—",
                        title = episode.title ?: "Episódio",
                        plot = episode.info?.plot,
                        selected = selected,
                        focused = interaction.emphasized,
                        primary = palette.primary,
                        text = text,
                        secondary = palette.onSurfaceVariant,
                        background = background,
                        titleStyle = MaterialTheme.typography.titleMedium,
                        bodyStyle = MaterialTheme.typography.bodySmall,
                        tagStyle = MaterialTheme.typography.labelSmall,
                        modifier = (if (selected) Modifier.focusRequester(selectedFocus) else Modifier)
                            .clickable(interactionSource = interaction.source, indication = null) {
                                controller.catalog.selectEpisode(requireNotNull(episode.id))
                                closeMenu()
                            }
                    )
                }
            }
            if (listState.canScrollBackward || listState.canScrollForward)
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd), style = desktopScrollbarStyle())
        }
    }
}

private enum class VodMenu { SETTINGS, EPISODES }
private fun mediaTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}
