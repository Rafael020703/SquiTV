package rsv.squitv.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import rsv.squitv.core.ui.components.player.CenteredPlayerActions
import rsv.squitv.core.ui.components.player.CompactChannelBanner
import rsv.squitv.desktop.player.WindowsLivePlayer
import rsv.squitv.shared.xtream.LiveStreamAddress
import rsv.squitv.ui.content.player.PlayerOverlayController
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun LivePlayerScreen(state: DesktopState, controller: DesktopController, settings: DesktopPreferencesStore,
    fullscreen: Boolean, toggleFullscreen: () -> Unit, registerPlayer: (WindowsLivePlayer?) -> Unit,
    registerInput: (((KeyEvent) -> Boolean)?) -> Unit) {
    val session = state.playback ?: return
    val credentials = state.credentials ?: return
    val scope = rememberCoroutineScope()
    val overlay = remember { PlayerOverlayController() }
    var controls by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf<LivePlayerMenu?>(null) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }
    var holdingControls by remember { mutableStateOf(false) }
    val pauseFocus = remember { FocusRequester() }
    val engine = remember { WindowsLivePlayer() }
    val status by engine.state.collectAsState()
    val preferences by settings.state.collectAsState()
    val logos = remember { ChannelLogos() }
    var retry by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    val showControls: () -> Unit = { banner = false; overlay.showOverlayWithTimer(scope) { controls = it } }
    val hideControls: () -> Unit = { holdingControls = false; overlay.hideOverlayImmediately { controls = it } }
    val interaction: () -> Unit = { overlay.updateInteraction(scope) { controls = it } }
    val closeMenu: () -> Unit = {
        menu = null
        showControls()
    }
    val openMenu: (LivePlayerMenu) -> Unit = {
        hideControls(); banner = false; menu = it
        if (it == LivePlayerMenu.SETTINGS) engine.refreshTracks()
    }
    val input: (KeyEvent) -> Boolean = { event ->
        when {
            event.key == Key.F11 || event.key == Key.F -> {
                if (event.type == KeyEventType.KeyDown) toggleFullscreen()
                true
            }
            event.key == Key.Escape || event.key == Key.Back -> {
                if (event.type == KeyEventType.KeyUp) when {
                    menu != null -> closeMenu()
                    controls -> hideControls()
                    banner -> banner = false
                    else -> controller.stopPlayback()
                }
                true
            }
            menu != null -> false
            event.key == Key.PageUp || event.key == Key.PageDown ||
                (!controls && event.key in listOf(Key.DirectionUp, Key.DirectionDown)) -> {
                if (event.type == KeyEventType.KeyUp) controller.stepChannel(
                    if (event.key in listOf(Key.PageUp, Key.DirectionUp)) -1 else 1)
                true
            }
            !controls && event.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar) -> {
                if (event.type == KeyEventType.KeyUp) showControls()
                true
            }
            else -> { interaction(); false }
        }
    }
    val currentInput by rememberUpdatedState(input)
    DisposableEffect(engine) {
        registerPlayer(engine); registerInput { currentInput(it) }
        onDispose { registerInput(null); registerPlayer(null); overlay.hideOverlayImmediately(); engine.close(); logos.close() }
    }
    LaunchedEffect(session.channel.id, credentials, retry) {
        hideControls(); menu = null; banner = true
        engine.play(LiveStreamAddress.build(credentials.baseUrl, credentials.username, credentials.password, session.channel.id))
        delay(3000); banner = false
    }
    LaunchedEffect(preferences.volume, preferences.muted) {
        engine.setVolume(preferences.volume)
        if (engine.state.value.muted != preferences.muted) engine.toggleMute()
    }
    LaunchedEffect(menu, status.error, holdingControls) {
        overlay.onSeekingStateChanged(menu != null || status.error != null || holdingControls, scope) { controls = it }
        if (status.error != null && menu == null) showControls()
    }
    LaunchedEffect(sleepMinutes) {
        sleepMinutes?.let { delay(it * 60_000L); controller.stopPlayback() }
    }
    LaunchedEffect(controls, banner) {
        while (controls || banner) { now = System.currentTimeMillis() / 1000; delay(1000) }
    }
    val programme = state.programmes.firstOrNull { (it.start ?: 0) <= now && (it.end ?: Long.MAX_VALUE) > now }
    val upcoming = state.programmes.filter { (it.start ?: 0) > now }.take(2)
    val progress = programme?.let {
        val start = it.start; val end = it.end
        if (start != null && end != null && end > start) ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f) else 0f
    } ?: 0f
    val guide = programme?.title ?: when {
        state.epgLoading -> "Carregando programação…"
        state.epgUnavailable -> "Programação temporariamente indisponível"
        else -> "Sem programação informada pelo servidor"
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        NativeVideoSurface(engine, Modifier.fillMaxSize(),
            onPointerMove = { if (menu == null) showControls() },
            onClick = { if (menu != null) closeMenu() else if (controls) hideControls() else showControls() })
    }
    NativePlayerLayer(banner && !controls && menu == null, PlayerLayerPlacement.TOP_START,
        heightDp = if (programme != null) 84 else 68, widthDp = 360, interactive = false) {
        CompactChannelBanner(session.channel.name, null, programme?.title, progress, true,
            palette.background, palette.primary, palette.onSurface, palette.onSurfaceVariant,
            MaterialTheme.typography.titleMedium, MaterialTheme.typography.bodyMedium, MaterialTheme.typography.labelMedium) {
            Surface(Modifier.size(44.dp), shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = .06f)) {
                Box(Modifier.padding(4.dp)) { ChannelLogo(session.channel.iconUrl, logos, Modifier.fillMaxSize()) }
            }
        }
    }
    NativePlayerLayer(controls && menu == null, PlayerLayerPlacement.BOTTOM, heightDp = 232, onKey = input) {
        LaunchedEffect(controls) {
            if (controls) { withFrameNanos { }; pauseFocus.requestFocus() }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)
            .onPointerEvent(PointerEventType.Move) { interaction() }
            .onPointerEvent(PointerEventType.Press) { holdingControls = true; interaction() }
            .onPointerEvent(PointerEventType.Release) { holdingControls = false; interaction() },
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelLogo(session.channel.iconUrl, logos, Modifier.size(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(session.channel.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(guide, style = MaterialTheme.typography.bodySmall, color = palette.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("AO VIVO", style = MaterialTheme.typography.labelSmall, color = palette.error)
                PlayerIcon(Icons.Rounded.Refresh, "Reconectar", { retry++; interaction() })
                PlayerIcon(Icons.Rounded.Close, "Encerrar reprodução", controller::stopPlayback)
            }
            if (programme != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = palette.primary, trackColor = Color.White.copy(alpha = .08f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(programTime(programme.start), style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                    Text(programTime(programme.end), style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                }
            }
            CenteredPlayerActions(16.dp, Modifier.weight(1f), left = {
                PlayerIcon(Icons.AutoMirrored.Rounded.List, "Canais desta lista", { openMenu(LivePlayerMenu.CHANNELS) })
                Spacer(Modifier.width(16.dp))
                PlayerIcon(Icons.Rounded.SkipPrevious, "Canal anterior", { controller.stepChannel(-1) })
            }, center = {
                FilledIconButton(onClick = { engine.togglePause(); interaction() },
                    modifier = Modifier.size(64.dp).focusRequester(pauseFocus), shape = CircleShape) {
                    Icon(if (status.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        if (status.paused) "Reproduzir" else "Pausar", Modifier.size(38.dp))
                }
            }, right = {
                PlayerIcon(Icons.Rounded.SkipNext, "Próximo canal", { controller.stepChannel(1) })
                Spacer(Modifier.width(16.dp))
                PlayerIcon(Icons.Rounded.Settings, "Configurações do player", { openMenu(LivePlayerMenu.SETTINGS) })
            })
            status.error?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                ?: upcoming.firstOrNull()?.let { Text("A seguir: ${programTime(it.start)} ${it.title}",
                    style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PlayerIcon(if (status.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    "Silenciar/ativar som", { settings.update { it.copy(muted = !it.muted) }; interaction() })
                var volume by remember(preferences.volume) { mutableFloatStateOf(preferences.volume.toFloat()) }
                Slider(volume, { volume = it; engine.setVolume(it.toInt()); interaction() }, valueRange = 0f..100f,
                    onValueChangeFinished = { settings.update { it.copy(volume = volume.toInt()) } }, modifier = Modifier.width(140.dp))
                Spacer(Modifier.weight(1f))
                Text(session.sourceName, style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant,
                    modifier = Modifier.weight(2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                PlayerIcon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    "Alternar tela cheia (F11)", toggleFullscreen)
            }
        }
    }
    NativePlayerLayer(menu != null, PlayerLayerPlacement.CENTER, heightDp = 450, widthDp = 760, onKey = input) {
        when (menu) {
            LivePlayerMenu.CHANNELS -> LiveSourcePanel(session, controller, closeMenu)
            LivePlayerMenu.SETTINGS -> LiveSettingsPanel(status, engine, sleepMinutes, { sleepMinutes = it }, closeMenu)
            null -> Unit
        }
    }
}

internal enum class LivePlayerMenu { CHANNELS, SETTINGS }

private fun programTime(time: Long?): String = time?.takeIf { it in 1..253402300799L }?.let {
    Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
}.orEmpty()
