package rsv.squitv.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rsv.squitv.core.ui.components.player.*
import rsv.squitv.desktop.player.WindowsLivePlayer
import rsv.squitv.shared.catalog.MultiViewPolicy
import rsv.squitv.shared.catalog.LiveCatalogView
import rsv.squitv.shared.player.VideoScaleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rsv.squitv.shared.xtream.*

@Composable
internal fun DesktopMultiViewScreen(controller: DesktopController, store: DesktopPreferencesStore,
    registerInput: (((KeyEvent) -> Boolean)?) -> Unit, toggleFullscreen: () -> Unit) {
    val state by controller.multiView.state.collectAsState()
    val desktop by controller.state.collectAsState()
    val preferences by store.state.collectAsState()
    val credentials = desktop.credentials ?: return
    var picking by remember { mutableStateOf<Int?>(null) }
    val pickerState = remember(picking) { ChannelPickerState() }
    val index by controller.liveIndex.state.collectAsState()
    val accountKey = liveCategoryScope(credentials).orEmpty()
    var view by remember(accountKey) { mutableStateOf(LiveCatalogView()) }
    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    LaunchedEffect(index, preferences.groupLiveChannels, preferences.preferredChannelQuality,
        preferences.categoryOrder(accountKey, null), preferences.hiddenCategories(accountKey, null), desktop.categories) {
        view = withContext(Dispatchers.Default) {
            if (index.accountScope != accountKey || !index.loaded) LiveCatalogView() else
                LiveCatalogView.build(desktop.categories, index.channels, accountKey,
                    preferences.groupLiveChannels, rsv.squitv.domain.model.ChannelQuality.fromPreference(preferences.preferredChannelQuality),
                    preferences.categoryOrder(accountKey, null), preferences.hiddenCategories(accountKey, null))
        }
    }
    val focuses = remember { List(MultiViewPolicy.SLOT_COUNT) { FocusRequester() } }
    LaunchedEffect(credentials) { controller.multiView.open(credentials); withFrameNanos { }; focuses[0].requestFocus() }
    val input: (KeyEvent) -> Boolean = { event ->
        if (event.key == Key.F11) {
            if (event.type == KeyEventType.KeyUp) toggleFullscreen()
            true
        } else if (event.key == Key.Escape || event.key == Key.Back) {
            if (event.type == KeyEventType.KeyUp) {
                if (picking != null) { if (!pickerState.back()) picking = null } else controller.home()
            }
            true
        } else false
    }
    val currentInput by rememberUpdatedState(input)
    LaunchedEffect(picking) {
        if (picking == null) { withFrameNanos { }; runCatching { focuses[state.focused].requestFocus() } }
    }
    DisposableEffect(controller) {
        registerInput { currentInput(it) }
        onDispose { registerInput(null); controller.multiView.leave() }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DesktopPageHeading("MULTI-VIEW", "Até 4 canais · áudio da tela selecionada") {
            VideoScaleChoices(scaleMode) { scaleMode = it }
        }
        index.error?.let { message -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { StatusNotice(message, error = true) }
            TextButton({ controller.multiView.open(credentials, force = true) }) { Text("Tentar novamente") }
        } }
        if (index.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        MultiViewGrid(Modifier.weight(1f)) { index, slotModifier ->
                    val channel = state.slots[index]
                    MultiViewSlotSurface(state.focused == index, palette.background, palette.primary,
                        RoundedCornerShape(16.dp), slotModifier
                            .focusRequester(focuses[index])
                            .onFocusChanged { if (it.hasFocus && picking == null) controller.multiView.focus(index) }
                            .clickable { controller.multiView.focus(index); if (channel == null) picking = index }) {
                        if (channel == null) MultiViewEmptyContent(state.focused == index, palette.primary,
                            palette.onSurface, palette.onSurfaceVariant, MaterialTheme.typography.labelLarge, "ADICIONAR CANAL")
                        else Column(Modifier.fillMaxSize()) {
                            // Controls occupy their own strip: Compose must not be obscured by a native HWND.
                            Row(Modifier.fillMaxWidth().height(40.dp).background(palette.surfaceVariant).padding(start = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}. ${channel.name}", Modifier.weight(1f), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                if (index == state.focused) Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Tela com áudio", Modifier.size(20.dp), tint = palette.primary)
                                IconButton({ controller.multiView.remove(index) }) { Icon(Icons.Rounded.Cancel, "Remover canal ${index + 1}", Modifier.size(22.dp)) }
                            }
                            MultiViewVideo(channel, credentials, scaleMode,
                                if (preferences.muted) 0 else (MultiViewPolicy.audioVolume(index, state.focused) * preferences.volume).toInt(),
                                { controller.multiView.focus(index); runCatching { focuses[index].requestFocus() } }, Modifier.weight(1f).fillMaxWidth())
                        }
                    }
        }
    }
    NativePlayerLayer(picking != null, PlayerLayerPlacement.START, heightDp = null, widthDp = 400, onKey = input) {
        ChannelPickerContent(view, pickerState, index.loading, index.error,
            { controller.multiView.open(credentials, force = true) }, { picking = null }, { channel ->
                picking?.let { controller.multiView.select(it, channel) }; picking = null
            }, Modifier.background(palette.surface))
    }
}

@Composable
private fun MultiViewVideo(channel: LiveChannel, credentials: ServerCredentials, scaleMode: VideoScaleMode, volume: Int,
    focus: () -> Unit, modifier: Modifier) {
    val engine = remember { WindowsLivePlayer().apply { setVolume(0) } }
    val state by engine.state.collectAsState()
    var retry by remember { mutableIntStateOf(0) }
    DisposableEffect(engine) { onDispose { engine.close() } }
    LaunchedEffect(channel.id, credentials, retry) {
        engine.setVolume(volume)
        engine.play(LiveStreamAddress.build(credentials.baseUrl, credentials.username, credentials.password, channel.id), channel.id)
    }
    LaunchedEffect(volume) { engine.setVolume(volume) }
    LaunchedEffect(scaleMode) { engine.setScaleMode(scaleMode) }
    Column(modifier) {
        NativeVideoSurface(engine, Modifier.weight(1f).fillMaxWidth(), onClick = focus)
        if (state.error != null) Row(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(state.error.orEmpty(), Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.labelSmall, color = palette.error)
            TextButton({ retry++ }) { Text("Reconectar") }
        }
    }
}
