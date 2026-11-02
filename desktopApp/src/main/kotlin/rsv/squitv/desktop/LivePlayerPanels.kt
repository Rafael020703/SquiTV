package rsv.squitv.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rsv.squitv.desktop.player.PlayerStatus
import rsv.squitv.desktop.player.WindowsLivePlayer

@Composable
internal fun LiveSourcePanel(session: LiveSession, controller: DesktopController, close: () -> Unit) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = session.index)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Canais", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(session.sourceName, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            }
            IconButton(close) { Icon(Icons.Rounded.Close, "Fechar lista") }
        }
        HorizontalDivider(color = palette.outline.copy(alpha = .4f))
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(session.channels, key = { it.id }) { channel ->
                val selected = channel.id == session.channel.id
                OutlinedButton({ controller.selectPlaybackChannel(channel.id); close() }, Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) palette.primary.copy(alpha = .14f) else Color.Transparent,
                        contentColor = if (selected) palette.primary else palette.onSurface)) {
                    Text(channel.name, Modifier.weight(1f))
                    if (selected) Icon(Icons.Rounded.Check, null)
                }
            }
        }
    }
}

/** Android PlayerSettingsDialog hierarchy and tabs; options call the native Windows adapter. */
@Composable
internal fun LiveSettingsPanel(status: PlayerStatus, engine: WindowsLivePlayer, sleep: Int?,
    setSleep: (Int?) -> Unit, close: () -> Unit) {
    val tabs = listOf("Imagem", "Áudio", "Legendas", "Velocidade", "Temporizador")
    var selected by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Configurações do player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Ajustes para a reprodução", style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            }
            IconButton(close) { Icon(Icons.Rounded.Close, "Fechar configurações") }
        }
        HorizontalDivider(color = palette.outline.copy(alpha = .4f))
        BoxWithConstraints(Modifier.weight(1f)) {
            val options: @Composable () -> Unit = {
                key(selected) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (selected) {
                            0 -> items(listOf(null, "16:9", "4:3")) { aspect ->
                                PlayerOption(aspect ?: "Proporção original", status.aspect == aspect) { engine.setAspect(aspect) }
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
                                        color = palette.onSurfaceVariant)
                                }
                                item { TextButton(engine::refreshTracks) { Text("Atualizar faixas") } }
                            }
                            3 -> items(listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)) { speed ->
                                PlayerOption(if (speed == 1f) "Normal (1×)" else "${speed}×", status.speed == speed) { engine.setSpeed(speed) }
                            }
                            4 -> items(listOf<Int?>(null, 15, 30, 45, 60, 90, 120)) { minutes ->
                                PlayerOption(minutes?.let { "$it minutos" } ?: "Desativado", sleep == minutes) { setSleep(minutes) }
                            }
                        }
                        status.optionError?.let { message -> item { Text(message, color = palette.error) } }
                    }
                }
            }
            val tab: @Composable (Int) -> Unit = { index ->
                OutlinedButton({ selected = index }, shape = RoundedCornerShape(10.dp),
                    modifier = if (maxWidth >= 600.dp) Modifier.fillMaxWidth() else Modifier,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected == index) palette.primary.copy(alpha = .14f) else Color.Transparent,
                        contentColor = if (selected == index) palette.primary else palette.onSurfaceVariant),
                    border = BorderStroke(1.dp, if (selected == index) palette.primary else palette.outline)) {
                    Text(tabs[index])
                }
            }
            if (maxWidth >= 600.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LazyColumn(Modifier.width(164.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tabs.indices.toList()) { tab(it) }
                }
                VerticalDivider(color = palette.outline.copy(alpha = .4f))
                Box(Modifier.weight(1f)) { options() }
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(tabs.indices.toList()) { tab(it) } }
                Box(Modifier.weight(1f)) { options() }
            }
        }
    }
}

@Composable
private fun PlayerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = rememberVisualInteraction()
    Surface(onClick = onClick, interactionSource = interaction.source,
        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
        color = if (selected || interaction.emphasized) palette.primary.copy(alpha = .12f) else palette.surface,
        border = BorderStroke(1.dp, if (selected || interaction.emphasized) palette.primary else palette.outline.copy(alpha = .4f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            if (selected) Icon(Icons.Rounded.Check, null, tint = palette.primary)
        }
    }
}
