package rsv.squitv.desktop

import androidx.compose.foundation.*
import androidx.compose.ui.graphics.vector.ImageVector
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
import rsv.squitv.core.ui.components.player.*
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
    val controlsFocus = remember { FocusRequester() }
    val engine = remember { WindowsLivePlayer(diagnostics = controller.liveDiagnostics) }
    val status by engine.state.collectAsState()
    val preferences by settings.state.collectAsState()
    val preferencesLoaded by settings.loaded.collectAsState()
    val preferencesError by settings.error.collectAsState()
    var volumeNotice by remember { mutableStateOf<Int?>(null) }
    var volumeRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(volumeRevision) { delay(1500); volumeNotice = null }
    val accountScope = remember(credentials) { liveCategoryScope(credentials) }
    LaunchedEffect(session.channel.id, session.sourceCategoryId, preferencesLoaded) {
        if (preferencesLoaded) settings.rememberCatalogPosition(accountScope, "live", session.sourceCategoryId,
            session.channel.id.toString(), immediate = true)
    }
    LaunchedEffect(controls, menu) { if (controls || menu != null) volumeNotice = null }
    val versionIds = remember(session.channels, session.channel.id) { session.versions.map { it.id } }
    val isFavorite = rsv.squitv.domain.model.LiveChannelFavorites.isFavorite(
        preferences.favoriteLiveChannelsByAccount[accountScope].orEmpty(), session.channel.id, versionIds)
    val logos = rememberDesktopImages(DesktopImageKind.CHANNELS)
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
                if (event.type == KeyEventType.KeyUp) toggleFullscreen()
                true
            }
            event.key == Key.Escape || event.key == Key.Back -> {
                if (event.type == KeyEventType.KeyUp) when {
                    menu != null -> closeMenu()
                    else -> controller.stopPlayback()
                }
                true
            }
            menu != null -> false
            !controls && event.key in listOf(Key.DirectionLeft, Key.DirectionRight) -> {
                if (event.type == KeyEventType.KeyUp && preferencesLoaded) {
                    val next = rsv.squitv.shared.player.PlayerVolume.adjust(
                        volumeNotice ?: engine.state.value.let { if (it.muted) 0 else it.volume },
                        if (event.key == Key.DirectionRight) 1 else -1)
                    engine.setVolume(next)
                    if (engine.state.value.muted) engine.toggleMute()
                    settings.update { it.copy(volume = next, muted = false) }
                    volumeNotice = next; volumeRevision++
                }
                true
            }
            event.key == Key.PageUp || event.key == Key.PageDown ||
                (!controls && event.key in listOf(Key.DirectionUp, Key.DirectionDown)) -> {
                if (event.type == KeyEventType.KeyUp) controller.stepChannel(
                    if (event.key in listOf(Key.PageUp, Key.DirectionUp)) -1 else 1)
                true
            }
            event.key == Key.Spacebar -> {
                if (event.type == KeyEventType.KeyUp) { engine.togglePause(); showControls() }
                true
            }
            !controls && event.key in listOf(Key.Enter, Key.NumPadEnter) -> {
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
    var previousOpening by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(session.channel.id, credentials, retry) {
        hideControls(); menu = null; banner = true
        val manual = previousOpening?.let { it.first == session.channel.id && it.second != retry } == true
        previousOpening = session.channel.id to retry
        engine.play(LiveStreamAddress.build(credentials.baseUrl, credentials.username, credentials.password, session.channel.id),
            session.channel.id, preferences.preferredAudioLanguage, preferences.preferredSubtitleLanguage, manualRetry = manual)
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
            controlsVisible = controls, pointerRevealEnabled = menu == null,
            onPointerMove = { if (menu == null) showControls() },
            onClick = { if (menu != null) closeMenu() else if (controls) hideControls() else showControls() })
    }
    NativePlayerLayer(volumeNotice != null && !controls && menu == null, PlayerLayerPlacement.CENTER,
        heightDp = 76, widthDp = 168, interactive = false) {
        volumeNotice?.let { PlayerVolumeNotice(it) }
    }
    NativePlayerLayer(status.reconnecting && !controls && menu == null, PlayerLayerPlacement.TOP_START,
        heightDp = 54, widthDp = 340, interactive = false) {
        Surface(Modifier.fillMaxSize(), color = palette.surface, shape = RoundedCornerShape(12.dp)) {
            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text("Reconectando ao canal…", color = palette.primary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    NativePlayerLayer(banner && !status.reconnecting && !controls && menu == null, PlayerLayerPlacement.TOP_START,
        heightDp = if (programme != null) 84 else 68, widthDp = 360, interactive = false) {
        CompactChannelBanner(session.channel.name, null, programme?.title, progress, true,
            palette.background, palette.primary, palette.onSurface, palette.onSurfaceVariant,
            MaterialTheme.typography.titleMedium, MaterialTheme.typography.bodyMedium, MaterialTheme.typography.labelMedium) {
            Surface(Modifier.size(44.dp), shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = .06f)) {
                Box(Modifier.padding(4.dp)) { ChannelLogo(session.channel.iconUrl, logos, Modifier.fillMaxSize()) }
            }
        }
    }
    NativePlayerLayer(controls && menu == null, PlayerLayerPlacement.BOTTOM,
        heightDp = if (status.error != null || preferencesError != null) 320 else if (programme != null) 224 else 176,
        onKey = input, onInputCancelled = { holdingControls = false }) {
        LaunchedEffect(controls) {
            if (controls) { withFrameNanos { }; controlsFocus.requestFocus() }
        }
        LivePlayerCard(session.channel.name, guide, false, palette.background, palette.primary,
            palette.onSurface, palette.onSurfaceVariant, MaterialTheme.typography.titleLarge,
            MaterialTheme.typography.bodyLarge, MaterialTheme.typography.labelSmall,
            Modifier.fillMaxSize()
                .onPointerEvent(PointerEventType.Move) { interaction() }
                .onPointerEvent(PointerEventType.Press) { holdingControls = true; interaction() }
                .onPointerEvent(PointerEventType.Release) { holdingControls = false; interaction() },
            badge = { PlayerLiveBadge("AO VIVO", palette.error, MaterialTheme.typography.labelSmall) },
            logo = {
                Surface(Modifier.size(60.dp), shape = RoundedCornerShape(12.dp),
                    color = palette.surface.copy(alpha = .2f),
                    border = BorderStroke(1.dp, palette.outline.copy(alpha = .2f))) {
                    Box(Modifier.padding(8.dp)) { ChannelLogo(session.channel.iconUrl, logos, Modifier.fillMaxSize()) }
                }
            }, actions = {
                LivePlayerButton(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos", {
                        accountScope?.let { account -> settings.update {
                            it.toggleLiveFavorite(account, session.channel.id, versionIds)
                        } }
                        interaction()
                    }, tint = if (isFavorite) palette.error else Color.White,
                    enabled = preferencesLoaded && accountScope != null)
                LivePlayerButton(Icons.AutoMirrored.Rounded.List, "Canais desta lista", { openMenu(LivePlayerMenu.CHANNELS) },
                    Modifier.focusRequester(controlsFocus))
                LivePlayerButton(Icons.Rounded.Settings, "Configurações do player", { openMenu(LivePlayerMenu.SETTINGS) })
            }, progress = if (programme != null) { {
                PlayerProgrammeProgress(progress, programTime(programme.start), programTime(programme.end), false,
                    palette.primary, palette.surfaceVariant, palette.onSurfaceVariant, MaterialTheme.typography.labelSmall)
            } } else null,
            upcoming = upcoming.map { "A seguir: ${programTime(it.start)} ${it.title}" }, extraControls = {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(session.sourceName, style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                status.error?.let {
                    Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ retry++; interaction() }) { Text("Tentar novamente") }
                        if (controller.liveDiagnostics != null)
                            TextButton(controller::openNotifications) { Text("Ver diagnóstico") }
                    }
                }
                if (status.reconnecting) Text("Reconectando ao canal…", color = palette.primary, style = MaterialTheme.typography.bodySmall)
                preferencesError?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            })
    }
    NativePlayerLayer(menu != null, if (menu == LivePlayerMenu.CHANNELS) PlayerLayerPlacement.START else PlayerLayerPlacement.CENTER,
        heightDp = if (menu == LivePlayerMenu.CHANNELS) null else 450,
        widthDp = if (menu == LivePlayerMenu.CHANNELS) 360 else 760, onKey = input) {
        when (menu) {
            LivePlayerMenu.CHANNELS -> LiveSourcePanel(session, controller, logos, closeMenu)
            LivePlayerMenu.SETTINGS -> LiveSettingsPanel(status, engine, sleepMinutes, { sleepMinutes = it },
                closeMenu, session, controller, settings)
            null -> Unit
        }
    }
}

internal enum class LivePlayerMenu { CHANNELS, SETTINGS }

private fun programTime(time: Long?): String = time?.takeIf { it in 1..253402300799L }?.let {
    Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
}.orEmpty()

@Composable
private fun LivePlayerButton(icon: ImageVector, label: String, action: () -> Unit, modifier: Modifier = Modifier,
    tint: Color = Color.White, enabled: Boolean = true) {
    val interaction = rememberVisualInteraction()
    PlayerRoundButton(icon, label, action,
        if (interaction.emphasized) palette.primary else Color.White.copy(alpha = .12f),
        if (interaction.emphasized) Color.Black else tint,
        if (interaction.emphasized) palette.primary else Color.White.copy(alpha = .15f),
        if (interaction.emphasized) 2.5.dp else 1.dp, modifier, interactionSource = interaction.source, enabled = enabled)
}
