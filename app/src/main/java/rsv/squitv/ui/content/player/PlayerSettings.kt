@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package rsv.squitv.ui.content.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import rsv.squitv.core.ui.components.settings.channelQualityLabel
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.domain.model.ChannelVersion
import java.util.Locale

private data class SettingsTab(val id: Int, val title: String)

@Composable
fun PlayerSettingsDialog(
    tracks: Tracks?,
    sleepTimer: Int?,
    currentSpeed: Float = 1f,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onSelectTrack: (Int, Int, Int) -> Unit,
    onClearOverride: (Int) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onSetSpeed: (Float) -> Unit = {},
    onToggleResizeMode: () -> Unit = {},
    isLive: Boolean = false,
    groupChannels: Boolean = true,
    versions: List<ChannelVersion> = emptyList(),
    selectedVersionId: Int? = null,
    onGroupingChanged: (Boolean) -> Unit = {},
    onVersionSelected: (Int) -> Unit = {},
    trackSelectionParameters: TrackSelectionParameters = TrackSelectionParameters.DEFAULT,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    speedOnly: Boolean = false
) {
    val colors = AppDesignSystem.colors
    val tabs = if (speedOnly) listOf(SettingsTab(3, "Velocidade")) else
        (if (isLive) listOf(SettingsTab(5, "Canais")) else emptyList()) +
        listOf(SettingsTab(0, "Imagem"), SettingsTab(1, "Áudio"), SettingsTab(2, "Legendas"),
            SettingsTab(3, "Velocidade"), SettingsTab(4, "Temporizador"))
    val openingTab = initialTab.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id
    var selectedTab by remember(openingTab) { mutableStateOf(openingTab) }
    val initialFocus = remember { FocusRequester() }
    val tabsState = rememberLazyListState(initialFirstVisibleItemIndex = tabs.indexOfFirst { it.id == openingTab })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val wide = maxWidth >= 600.dp && !speedOnly
            Surface(
                modifier = Modifier.widthIn(max = if (speedOnly) 420.dp else 760.dp)
                    .fillMaxWidth().fillMaxHeight(0.9f),
                shape = RoundedCornerShape(20.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (speedOnly) "Velocidade" else "Configurações do player",
                                style = MaterialTheme.typography.titleLarge, color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold)
                            Text("Ajustes para a reprodução", style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, "Fechar configurações", tint = colors.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = colors.border.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    val tabContent: @Composable (SettingsTab) -> Unit = { tab ->
                        var focused by remember { mutableStateOf(false) }
                        val selected = selectedTab == tab.id
                        OutlinedButton(
                            onClick = { selectedTab = tab.id },
                            modifier = (if (wide || speedOnly) Modifier.fillMaxWidth() else Modifier)
                                .then(if (tab.id == openingTab) Modifier.focusRequester(initialFocus) else Modifier)
                                .onFocusChanged { focused = it.isFocused },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected || focused) colors.primary.copy(alpha = 0.14f) else Color.Transparent,
                                contentColor = if (selected || focused) colors.primary else colors.textSecondary),
                            border = BorderStroke(if (focused) 2.dp else 1.dp,
                                if (selected || focused) colors.primary else colors.border)
                        ) { Text(tab.title, style = MaterialTheme.typography.labelLarge) }
                    }
                    val options: @Composable () -> Unit = {
                        key(selectedTab) {
                            PlayerSettingsOptions(selectedTab, tracks, trackSelectionParameters, sleepTimer,
                                currentSpeed, resizeMode, groupChannels, versions, selectedVersionId,
                                onSelectTrack, onClearOverride, onSetSleepTimer, onSetSpeed,
                                onToggleResizeMode, onGroupingChanged, onVersionSelected,
                                if (speedOnly) initialFocus else null)
                        }
                    }
                    if (wide) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            LazyColumn(Modifier.width(164.dp), state = tabsState,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(tabs, key = { it.id }) { tabContent(it) }
                            }
                            VerticalDivider(color = colors.border.copy(alpha = 0.4f))
                            Box(Modifier.weight(1f)) { options() }
                        }
                    } else {
                        if (!speedOnly) {
                            LazyRow(state = tabsState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(tabs, key = { it.id }) { tabContent(it) }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        Box(Modifier.weight(1f)) { options() }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            initialFocus.requestFocus()
        }
    }
}

@Composable
private fun PlayerSettingsOptions(
    tab: Int, tracks: Tracks?, parameters: TrackSelectionParameters,
    sleepTimer: Int?, speed: Float, resizeMode: Int, grouped: Boolean,
    versions: List<ChannelVersion>, selectedVersionId: Int?,
    selectTrack: (Int, Int, Int) -> Unit, clearTrack: (Int) -> Unit,
    setSleepTimer: (Int?) -> Unit, setSpeed: (Float) -> Unit,
    toggleResize: () -> Unit, setGrouping: (Boolean) -> Unit, selectVersion: (Int) -> Unit,
    speedFocus: FocusRequester? = null
) {
    val colors = AppDesignSystem.colors
    val type = when (tab) { 0 -> C.TRACK_TYPE_VIDEO; 1 -> C.TRACK_TYPE_AUDIO; else -> C.TRACK_TYPE_TEXT }
    val groups = tracks?.groups?.filter { it.type == type }.orEmpty()
    val hasOverride = groups.any { parameters.overrides.containsKey(it.mediaTrackGroup) }
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val focusSpeed = speeds.minBy { kotlin.math.abs(it - speed) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (speedFocus != null) speeds.indexOf(focusSpeed) else 0)
    LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        if (tab in 0..2) {
            if (tab == 0) item {
                val mode = when (resizeMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Preencher"
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                    else -> "Ajustar"
                }
                TvOptionRowItem("Proporção da imagem", "Atual: $mode · pressione para alternar",
                    false, action = true, onClick = toggleResize)
            }
            item {
                TvOptionRowItem(if (tab == 2) "Desativadas" else "Automático",
                    when (tab) {
                        0 -> "A melhor qualidade disponível para a conexão"
                        1 -> "Usar a faixa padrão do conteúdo"
                        else -> "Não exibir legendas"
                    },
                    if (tab == 2) type in parameters.disabledTrackTypes ||
                        (!hasOverride && groups.none { it.isSelected }) else !hasOverride,
                    onClick = { clearTrack(type) })
            }
            groups.forEachIndexed { groupIndex, group ->
                items(group.length, key = { "track-$groupIndex-$it" }) { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    val supported = group.isTrackSupported(trackIndex)
                    val language = format.language?.takeUnless { it == "und" }?.let {
                        Locale.forLanguageTag(it).getDisplayLanguage(Locale.forLanguageTag("pt-BR"))
                    } ?: "Idioma não informado"
                    val label = if (tab == 0) listOfNotNull(
                        format.height.takeIf { it > 0 }?.let { "${it}p" },
                        format.frameRate.takeIf { it > 0 }?.let { "${it.toInt()} fps" }
                    ).joinToString(" · ").ifBlank { format.label ?: "Vídeo" }
                    else listOfNotNull(language, format.label?.takeIf { it.isNotBlank() }).distinct().joinToString(" · ")
                    val detail = if (!supported) "Não compatível com este dispositivo" else if (tab == 1)
                        listOfNotNull(format.channelCount.takeIf { it > 0 }?.let { "$it canais" },
                            format.sampleRate.takeIf { it > 0 }?.let { "${it / 1000f} kHz" }).joinToString(" · ")
                    else if (tab == 0) format.codecs else "Legenda integrada"
                    val override = parameters.overrides[group.mediaTrackGroup]
                    TvOptionRowItem(label, detail,
                        type !in parameters.disabledTrackTypes &&
                            (override?.trackIndices?.contains(trackIndex) == true ||
                                (tab == 2 && !hasOverride && group.isTrackSelected(trackIndex))),
                        enabled = supported, onClick = { selectTrack(groupIndex, trackIndex, type) })
                }
            }
            if (groups.isEmpty()) item {
                Text(if (tab == 2) "Este conteúdo não disponibilizou legendas."
                    else "Nenhuma faixa adicional disponível no momento.",
                    style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        } else when (tab) {
            3 -> items(speeds) { value ->
                TvOptionRowItem("${value}x", if (value == 1f) "Velocidade normal" else null,
                    kotlin.math.abs(speed - value) < 0.01f,
                    modifier = if (speedFocus != null && value == focusSpeed)
                        Modifier.focusRequester(speedFocus) else Modifier,
                    onClick = { setSpeed(value) })
            }
            4 -> {
                item {
                    Text(if (sleepTimer == null) "Interromper a reprodução após o tempo escolhido."
                        else "Restam $sleepTimer minutos para interromper a reprodução.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                items(listOf(0, 15, 30, 60, 120)) { minutes ->
                    TvOptionRowItem(if (minutes == 0) "Desligado" else "$minutes minutos",
                        isSelected = if (minutes == 0) sleepTimer == null else sleepTimer == minutes,
                        onClick = { setSleepTimer(minutes.takeIf { it > 0 }) })
                }
            }
            5 -> {
                item {
                    TvOptionRowItem("Agrupar versões do canal", if (grouped) "Ativado" else "Desativado",
                        grouped, action = true, onClick = { setGrouping(!grouped) })
                }
                items(versions, key = { it.streamId }) { version ->
                    TvOptionRowItem(version.resolution?.let { channelQualityLabel(it) } ?: "Qualidade não informada",
                        listOfNotNull(version.name, version.codec).joinToString(" · "),
                        version.streamId == selectedVersionId, onClick = { selectVersion(version.streamId) })
                }
                if (versions.isEmpty()) item {
                    Text("Nenhuma outra versão disponível nesta lista.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
fun TvOptionRowItem(
    label: String, sublabel: String? = null, isSelected: Boolean,
    modifier: Modifier = Modifier, enabled: Boolean = true, action: Boolean = false, onClick: () -> Unit
) {
    val colors = AppDesignSystem.colors
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .selectable(selected = isSelected, enabled = enabled,
                role = if (action) Role.Button else Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (focused) colors.primary.copy(alpha = 0.2f)
            else if (isSelected) colors.primary.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.035f),
        border = BorderStroke(1.dp, if (focused) colors.primary else Color.Transparent)
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = colors.textPrimary.copy(alpha = if (enabled) 1f else 0.45f))
                if (!sublabel.isNullOrBlank()) Text(sublabel,
                    style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            if (isSelected) Icon(Icons.Rounded.Check, "Selecionado",
                tint = colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun PlayerSpeedDialog(currentSpeed: Float, onDismiss: () -> Unit, onSetSpeed: (Float) -> Unit) {
    PlayerSettingsDialog(tracks = null, sleepTimer = null, currentSpeed = currentSpeed, initialTab = 3,
        onDismiss = onDismiss, onSelectTrack = { _, _, _ -> }, onClearOverride = {},
        onSetSleepTimer = {}, onSetSpeed = { onSetSpeed(it); onDismiss() }, speedOnly = true)
}
