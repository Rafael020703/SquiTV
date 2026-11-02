package rsv.squitv.desktop

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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import rsv.squitv.core.ui.components.player.CenteredPlayerActions
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
    var controls by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<VodMenu?>(null) }
    var holding by remember { mutableStateOf(false) }
    var keyboardSeeking by remember { mutableStateOf(false) }
    var dragging by remember(session.id) { mutableStateOf<Float?>(null) }
    var seekFocused by remember { mutableStateOf(false) }
    var seekRevision by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }
    val pauseFocus = remember { FocusRequester() }
    val showControls: () -> Unit = { overlay.showOverlayWithTimer(scope) { controls = it } }
    val interaction: () -> Unit = { overlay.updateInteraction(scope) { controls = it } }
    val seek = remember(session.id, status.durationMs) {
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
                    seek.seekTarget != null || dragging != null -> { seek.cancelSeek(); dragging = null }
                    menu != null -> closeMenu()
                    controls -> hideControls()
                    else -> controller.catalog.stopPlayback()
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
    DisposableEffect(engine) {
        registerPlayer(engine); registerInput { currentInput(it) }
        onDispose { registerInput(null); registerPlayer(null); overlay.hideOverlayImmediately(); engine.close() }
    }
    LaunchedEffect(session.id, session.series, account, retry) {
        menu = null; seek.cancelSeek(); dragging = null; showControls()
        engine.play(OnDemandPlayback.address(account, session.series, session.id, session.extension), session.id)
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
    LaunchedEffect(status.ended, status.sourceId, session.id, preferences.autoPlayEpisodes) {
        if (status.ended && status.sourceId == session.id && preferences.autoPlayEpisodes && session.series &&
            session.episodeIndex in 0 until session.episodes.lastIndex) controller.catalog.nextEpisode(1)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        NativeVideoSurface(engine, Modifier.fillMaxSize(),
            onPointerMove = { if (menu == null) showControls() },
            onClick = { if (menu != null) closeMenu() else if (controls) hideControls() else showControls() })
    }
    NativePlayerLayer(controls && menu == null, PlayerLayerPlacement.BOTTOM, heightDp = 256, onKey = input) {
        LaunchedEffect(controls) { if (controls) { withFrameNanos { }; pauseFocus.requestFocus() } }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)
            .onPointerEvent(PointerEventType.Move) { interaction() }
            .onPointerEvent(PointerEventType.Press) { holding = true; interaction() }
            .onPointerEvent(PointerEventType.Release) { holding = false; interaction() },
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status.phase, style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                }
                PlayerIcon(Icons.Rounded.Refresh, "Tentar novamente", { retry++; interaction() })
                PlayerIcon(Icons.Rounded.Close, "Encerrar reprodução", controller.catalog::stopPlayback)
            }
            if (status.durationMs > 0) {
                val position = dragging?.let { (it * status.durationMs).toLong() } ?: seek.seekTarget ?: pending ?: status.timeMs
                Text("${mediaTime(position)} / ${mediaTime(status.durationMs)}", style = MaterialTheme.typography.labelMedium,
                    color = if (keyboardSeeking || dragging != null) palette.primary else palette.onSurface)
                Slider((position.toDouble() / status.durationMs).toFloat().coerceIn(0f, 1f),
                    onValueChange = { seek.cancelSeek(); dragging = it; interaction() },
                    onValueChangeFinished = {
                        dragging?.let {
                            seek.cancelSeek()
                            seek.handleDpadSeek(status.timeMs, (it * status.durationMs).toLong() - status.timeMs, scope)
                            seek.commitSeek()
                        }
                        dragging = null
                    }, enabled = status.seekable, modifier = Modifier.fillMaxWidth().height(24.dp)
                        .onFocusChanged { seekFocused = it.isFocused; if (!it.isFocused && seek.seekTarget != null) seek.commitSeek() })
            } else Text("Duração ainda não informada", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            CenteredPlayerActions(12.dp, Modifier.weight(1f), left = {
                if (session.series) IconButton({ controller.catalog.nextEpisode(-1) }, enabled = session.episodeIndex > 0) {
                    Icon(Icons.Rounded.SkipPrevious, "Episódio anterior")
                }
                IconButton({ if (status.seekable) seek.handleDpadSeek(status.timeMs, -10_000, scope) }, enabled = status.seekable) {
                    Icon(Icons.Rounded.Replay10, "Voltar 10 segundos")
                }
            }, center = {
                FilledIconButton({ engine.togglePause(); interaction() }, Modifier.size(64.dp).focusRequester(pauseFocus), shape = CircleShape) {
                    Icon(if (status.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        if (status.paused) "Reproduzir" else "Pausar", Modifier.size(38.dp))
                }
            }, right = {
                IconButton({ if (status.seekable) seek.handleDpadSeek(status.timeMs, 10_000, scope) }, enabled = status.seekable) {
                    Icon(Icons.Rounded.Forward10, "Avançar 10 segundos")
                }
                if (session.series) IconButton({ controller.catalog.nextEpisode(1) }, enabled = session.episodeIndex in 0 until session.episodes.lastIndex) {
                    Icon(Icons.Rounded.SkipNext, "Próximo episódio")
                }
            })
            status.error?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PlayerIcon(if (status.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    "Silenciar/ativar som", { settings.update { it.copy(muted = !it.muted) }; interaction() })
                var volume by remember(preferences.volume) { mutableFloatStateOf(preferences.volume.toFloat()) }
                Slider(volume, { volume = it; engine.setVolume(it.toInt()); interaction() }, valueRange = 0f..100f,
                    onValueChangeFinished = { settings.update { it.copy(volume = volume.toInt()) } }, modifier = Modifier.width(120.dp))
                Spacer(Modifier.weight(1f))
                if (session.series) PlayerIcon(Icons.Rounded.VideoLibrary, "Episódios", { openMenu(VodMenu.EPISODES) })
                PlayerIcon(Icons.Rounded.Settings, "Configurações do player", { openMenu(VodMenu.SETTINGS) })
                PlayerIcon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Alternar tela cheia (F11)", toggleFullscreen)
            }
        }
    }
    NativePlayerLayer(menu != null, PlayerLayerPlacement.CENTER, heightDp = 450, widthDp = 760, onKey = input) {
        when (menu) {
            VodMenu.SETTINGS -> LiveSettingsPanel(status, engine, sleepMinutes, { sleepMinutes = it }, closeMenu)
            VodMenu.EPISODES -> Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Episódios", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(closeMenu) { Icon(Icons.Rounded.Close, "Fechar episódios") }
                }
                val listState = androidx.compose.foundation.lazy.rememberLazyListState(session.episodeIndex.coerceAtLeast(0))
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(session.episodes, key = { _, item -> requireNotNull(item.id) }) { index, episode ->
                        OutlinedButton({ controller.catalog.selectEpisode(requireNotNull(episode.id)); closeMenu() }, Modifier.fillMaxWidth()) {
                            Text(listOfNotNull(episode.season?.let { "T$it" }, episode.episodeNum?.let { "E$it" }, episode.title)
                                .joinToString(" · "), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (index == session.episodeIndex) Icon(Icons.Rounded.Check, null)
                        }
                    }
                }
            }
            null -> Unit
        }
    }
}

private enum class VodMenu { SETTINGS, EPISODES }
private fun mediaTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}
