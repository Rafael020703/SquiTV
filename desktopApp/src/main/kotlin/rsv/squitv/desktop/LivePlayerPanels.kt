package rsv.squitv.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.sp
import rsv.squitv.core.ui.components.player.*
import rsv.squitv.desktop.player.PlayerStatus
import rsv.squitv.desktop.player.WindowsLivePlayer
import rsv.squitv.domain.model.LiveChannelLabels

@Composable
internal fun LiveSourcePanel(session: LiveSession, controller: DesktopController, logos: ChannelLogos, close: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = session.index)
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; selectedFocus.requestFocus() }
    Column(Modifier.fillMaxSize().background(palette.surface.copy(alpha = .95f))) {
        Surface(Modifier.fillMaxWidth().height(80.dp), color = Color.White.copy(alpha = .05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .05f))) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = palette.primary)
                Spacer(Modifier.width(12.dp))
                Text(session.sourceName.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    color = palette.onSurface, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                IconButton(close) { Icon(Icons.Rounded.Close, "Fechar lista", tint = palette.onSurfaceVariant) }
            }
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 12.dp)) {
                items(session.channels, key = { it.stableKey }) { channel ->
                    val selected = channel.containsStream(session.channel.id)
                    val interaction = rememberVisualInteraction()
                    val text by animateColorAsState(if (interaction.emphasized) Color.Black else if (selected) palette.primary else palette.onSurface)
                    val background by animateColorAsState(if (interaction.emphasized) palette.primary else if (selected) palette.primary.copy(alpha = .1f) else Color.Transparent)
                    PlayerChannelRow(channel.name, selected, interaction.emphasized, palette.primary, text, background,
                        MaterialTheme.typography.titleLarge,
                        (if (selected) Modifier.focusRequester(selectedFocus) else Modifier)
                            .clickable(interactionSource = interaction.source, indication = null) {
                                controller.selectPlaybackChannel(channel.id); close()
                            },
                        logoContent = {
                            ChannelLogo(channel.iconUrl, logos, Modifier.fillMaxSize())
                        })
                }
            }
            if (listState.canScrollBackward || listState.canScrollForward)
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd), style = desktopScrollbarStyle())
        }
    }
}

/** Android PlayerSettingsDialog hierarchy and tabs; options call the native Windows adapter. */
@Composable
internal fun LiveSettingsPanel(status: PlayerStatus, engine: WindowsLivePlayer, sleep: Int?,
    setSleep: (Int?) -> Unit, close: () -> Unit, session: LiveSession? = null,
    controller: DesktopController? = null, store: DesktopPreferencesStore? = null) {
    val preferences = store?.state?.collectAsState()?.value
    val preferencesError = store?.error?.collectAsState()?.value
    val versions = remember(session?.channels, session?.channel?.id) { session?.versions.orEmpty() }
    val tabs = (listOf("Imagem", "Áudio", "Legendas", "Velocidade", "Temporizador") +
        if (session != null && controller != null && store != null) listOf("Canais") else emptyList())
        .mapIndexed { index, title -> PlayerSettingsTab(index, title) }
    var selected by remember { mutableIntStateOf(0) }
    val initialFocus = remember { FocusRequester() }
    PlayerSettingsPanel(tabs, selected, 0, { selected = it }, close, initialFocus,
        palette.primary, palette.surface, palette.onSurface, palette.onSurfaceVariant, palette.outline,
        Modifier.fillMaxSize()) {
        key(selected) {
            val listState = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    when (selected) {
                        0 -> items(rsv.squitv.shared.player.VideoScaleMode.entries) { mode ->
                            PlayerSettingsOption(mode.displayName(), mode.description(), status.scaleMode == mode,
                                palette.primary, palette.onSurface, palette.onSurfaceVariant, onClick = { engine.setScaleMode(mode) })
                        }
                        1, 2 -> {
                            val subtitles = selected == 2
                            val tracks = if (subtitles) status.subtitleTracks else status.audioTracks
                            val active = if (subtitles) status.subtitleTrack else status.audioTrack
                            if (subtitles) item { PlayerOption("Desativadas", active == -1) { engine.selectTrack(-1, true) } }
                            items(tracks.filter { it.id >= 0 }, key = { it.id }) { track ->
                                PlayerOption(track.name, active == track.id) { engine.selectTrack(track.id, subtitles) }
                            }
                            if (tracks.none { it.id >= 0 }) item {
                                Text(if (subtitles) "Nenhuma legenda informada pela transmissão." else "Nenhuma faixa de áudio disponível.",
                                    color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            item {
                                OutlinedButton(engine::refreshTracks, shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primary),
                                    border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.5f))) {
                                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Atualizar faixas", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        3 -> items(listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)) { speed ->
                            PlayerOption(if (speed == 1f) "Normal (1×)" else "${speed}×", status.speed == speed) { engine.setSpeed(speed) }
                        }
                        4 -> items(listOf<Int?>(null, 15, 30, 45, 60, 90, 120)) { minutes ->
                            PlayerOption(minutes?.let { "$it minutos" } ?: "Desativado", sleep == minutes) { setSleep(minutes) }
                        }
                        5 -> if (preferences != null && controller != null && session != null) {
                            item {
                                PlayerSettingsOption("Agrupar versões do canal",
                                    if (preferences.groupLiveChannels) "Ativado" else "Desativado",
                                    preferences.groupLiveChannels, palette.primary, palette.onSurface, palette.onSurfaceVariant,
                                    action = true, onClick = { store.update { it.copy(groupLiveChannels = !it.groupLiveChannels) } })
                            }
                            items(versions, key = { it.id }) { version ->
                                val label = LiveChannelLabels.parse(version.name)
                                PlayerSettingsOption(label.resolution?.desktopLabel() ?: "Qualidade não informada",
                                    listOfNotNull(version.name, label.codec).joinToString(" · "),
                                    version.id == session.channel.id, palette.primary, palette.onSurface, palette.onSurfaceVariant,
                                    onClick = { controller.selectChannelVersion(version.id) })
                            }
                            if (versions.isEmpty()) item {
                                Text("Nenhuma outra versão disponível nesta lista.", color = palette.onSurfaceVariant)
                            }
                            preferencesError?.let { message -> item {
                                Text(message, color = palette.error, style = MaterialTheme.typography.bodySmall)
                            } }
                        }
                    }
                    status.optionError?.let { message -> item { Text(message, color = palette.error, style = MaterialTheme.typography.bodySmall) } }
                }
                if (listState.canScrollBackward || listState.canScrollForward) {
                    VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd), style = desktopScrollbarStyle())
                }
            }
        }
    }
}

@Composable
private fun PlayerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    PlayerSettingsOption(label, selected = selected, primary = palette.primary, text = palette.onSurface,
        secondary = palette.onSurfaceVariant, onClick = onClick)
}
