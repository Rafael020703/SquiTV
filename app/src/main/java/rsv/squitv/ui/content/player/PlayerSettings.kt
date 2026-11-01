package rsv.squitv.ui.content.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Tracks
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PlayerSettingsDialog(
    tracks: Tracks?,
    sleepTimer: Int?,
    currentSpeed: Float = 1.0f,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onSelectTrack: (Int, Int, Int) -> Unit,
    onClearOverride: (Int) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onSetSpeed: (Float) -> Unit = {},
    onToggleResizeMode: () -> Unit = {},
    isLive: Boolean = false,
    groupChannels: Boolean = true,
    versions: List<rsv.squitv.domain.model.ChannelVersion> = emptyList(),
    selectedVersionId: Int? = null,
    onGroupingChanged: (Boolean) -> Unit = {},
    onVersionSelected: (Int) -> Unit = {}
) {
    val tokens = AppDesignSystem
    var selectedTab by remember { mutableStateOf(initialTab) }
    
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200)
        try { initialFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Fullscreen Overlay Backdrop (Video remains visible underneath with dark scrim)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Back) {
                    onDismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        // TV-First Settings Side Panel
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.84f)
                .padding(end = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = tokens.colors.surface,
            border = BorderStroke(1.dp, tokens.colors.border.copy(alpha = 0.35f)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = tokens.colors.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "CONFIGURAÇÕES DO PLAYER",
                            style = tokens.typography.title,
                            fontWeight = FontWeight.Black,
                            color = tokens.colors.textPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    AppIconButton(
                        icon = Icons.Rounded.Close,
                        onClick = onDismiss,
                        tint = tokens.colors.textSecondary
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Panel Main Body (Left Category Menu + Right Options)
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Side Category Menu
                    Column(
                        modifier = Modifier
                            .width(144.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val categories = (if (isLive) listOf(Triple(5, stringResource(R.string.channel_version), Icons.Rounded.LiveTv)) else emptyList()) + listOf(
                            Triple(0, stringResource(R.string.video_track), Icons.Rounded.VideoSettings),
                            Triple(1, "ÁUDIO", Icons.Rounded.InterpreterMode),
                            Triple(2, "LEGENDAS", Icons.Rounded.ClosedCaption),
                            Triple(3, "VELOCIDADE", Icons.Rounded.Speed),
                            Triple(4, "SISTEMA", Icons.Rounded.SettingsSuggest)
                        )

                        categories.forEachIndexed { idx, (catId, title, icon) ->
                            SettingsCategoryTab(
                                title = title,
                                icon = icon,
                                isSelected = selectedTab == catId,
                                modifier = if (idx == 0) Modifier.focusRequester(initialFocusRequester) else Modifier,
                                onClick = { selectedTab = catId }
                            )
                        }
                    }

                    VerticalDivider(color = Color.White.copy(alpha = 0.08f))

                    // Right Content Options Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(20.dp)
                    ) {
                        when (selectedTab) {
                            5 -> ChannelVersionsSection(groupChannels, versions, selectedVersionId, onGroupingChanged, onVersionSelected)
                            0 -> VideoQualitySettingsSection(
                                tracks = tracks,
                                onSelectTrack = onSelectTrack,
                                onClearOverride = onClearOverride,
                                onToggleResizeMode = onToggleResizeMode
                            )
                            1 -> AudioTrackSettingsSection(
                                tracks = tracks,
                                onSelectTrack = onSelectTrack,
                                onClearOverride = onClearOverride
                            )
                            2 -> SubtitleSettingsSection(
                                tracks = tracks,
                                onSelectTrack = onSelectTrack,
                                onClearOverride = onClearOverride
                            )
                            3 -> PlaybackSpeedSettingsSection(
                                currentSpeed = currentSpeed,
                                onSetSpeed = onSetSpeed
                            )
                            4 -> SystemSettingsSection(
                                sleepTimer = sleepTimer,
                                onSetSleepTimer = onSetSleepTimer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryTab(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        if (isFocused) tokens.colors.primary
        else if (isSelected) tokens.colors.primary.copy(alpha = 0.20f)
        else Color.Transparent,
        label = "catBg"
    )
    val contentColor by animateColorAsState(
        if (isFocused) Color.Black
        else if (isSelected) tokens.colors.primary
        else tokens.colors.textSecondary,
        label = "catContent"
    )

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .adaptiveFocus(
                shape = RoundedCornerShape(12.dp),
                focusedScale = 1.04f,
                onFocus = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = contentColor,
                style = tokens.typography.label.copy(fontSize = 11.sp),
                fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChannelVersionsSection(
    grouped: Boolean,
    versions: List<rsv.squitv.domain.model.ChannelVersion>,
    selectedId: Int?,
    onGroupingChanged: (Boolean) -> Unit,
    onVersionSelected: (Int) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            rsv.squitv.core.ui.components.settings.SettingsToggle(
                label = stringResource(R.string.group_live_channels), checked = grouped,
                onCheckedChange = onGroupingChanged)
            Text(stringResource(R.string.channel_version_hint), style = MaterialTheme.typography.bodySmall,
                color = AppDesignSystem.colors.textSecondary)
        }
        itemsIndexed(versions, key = { _, version -> version.streamId }) { _, version ->
            val resolution = version.resolution?.let { rsv.squitv.core.ui.components.settings.channelQualityLabel(it) }
                ?: stringResource(R.string.channel_version_unknown)
            TvOptionRowItem(
                label = listOfNotNull(resolution, version.codec).joinToString(" • "),
                sublabel = "${version.name} • #${version.streamId}",
                isSelected = version.streamId == selectedId,
                onClick = { onVersionSelected(version.streamId) })
        }
    }
}

// --- VÍDEO / QUALIDADE DE VÍDEO (FASE 2E - TV-FIRST LIST) ---
@Composable
private fun VideoQualitySettingsSection(
    tracks: Tracks?,
    onSelectTrack: (Int, Int, Int) -> Unit,
    onClearOverride: (Int) -> Unit,
    onToggleResizeMode: () -> Unit = {}
) {
    val tokens = AppDesignSystem
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.video_track),
            style = tokens.typography.title,
            color = tokens.colors.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        val videoGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_VIDEO } ?: emptyList()
        val isAutoSelected = videoGroups.none { it.isSelected }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TvOptionRowItem(
                    label = "PROPORÇÃO DA TELA (AJUSTAR / PREENCHER / ZOOM)",
                    sublabel = "Alternar modo de ajuste da imagem na tela",
                    isSelected = false,
                    onClick = onToggleResizeMode
                )
            }

            // Option 1: AUTOMÁTICO
            item {
                TvOptionRowItem(
                    label = stringResource(R.string.video_track_default),
                    sublabel = stringResource(R.string.video_track_hint),
                    isSelected = isAutoSelected,
                    onClick = { onClearOverride(C.TRACK_TYPE_VIDEO) }
                )
            }

            // Available Video Resolutions
            videoGroups.forEachIndexed { groupIdx, group ->
                items(group.length) { trackIdx ->
                    val format = group.getTrackFormat(trackIdx)
                    val isSelected = group.isTrackSelected(trackIdx)
                    
                    val height = format.height
                    val label = listOfNotNull(if (height > 0) "${height}p" else null,
                        format.frameRate.takeIf { it > 0f }?.let { "$it fps" }, format.codecs)
                        .joinToString(" • ").ifBlank { stringResource(R.string.channel_version_unknown) }
                    val sublabel = format.label.orEmpty()

                    TvOptionRowItem(
                        label = label,
                        sublabel = sublabel,
                        isSelected = isSelected,
                        onClick = { onSelectTrack(groupIdx, trackIdx, C.TRACK_TYPE_VIDEO) }
                    )
                }
            }
        }
    }
}

// --- ÁUDIO ---
@Composable
private fun AudioTrackSettingsSection(
    tracks: Tracks?,
    onSelectTrack: (Int, Int, Int) -> Unit,
    onClearOverride: (Int) -> Unit
) {
    val tokens = AppDesignSystem

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "FAIXAS DE ÁUDIO",
            style = tokens.typography.title,
            color = tokens.colors.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (tracks == null) {
            NoOptionsPlaceholder()
            return
        }

        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val isAutoSelected = audioGroups.none { it.isSelected }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TvOptionRowItem(
                    label = "PADRÃO DO CANAL",
                    sublabel = "Usar idioma original de transmissão",
                    isSelected = isAutoSelected,
                    onClick = { onClearOverride(C.TRACK_TYPE_AUDIO) }
                )
            }

            audioGroups.forEachIndexed { groupIdx, group ->
                items(group.length) { trackIdx ->
                    val format = group.getTrackFormat(trackIdx)
                    val isSelected = group.isTrackSelected(trackIdx)
                    val lang = format.language?.uppercase() ?: "DESCONHECIDO"
                    val label = if (format.label != null) "$lang - ${format.label}" else lang

                    TvOptionRowItem(
                        label = label,
                        sublabel = "Canal ${format.channelCount}.0 • ${format.sampleRate / 1000} kHz",
                        isSelected = isSelected,
                        onClick = { onSelectTrack(groupIdx, trackIdx, C.TRACK_TYPE_AUDIO) }
                    )
                }
            }
        }
    }
}

// --- LEGENDAS ---
@Composable
private fun SubtitleSettingsSection(
    tracks: Tracks?,
    onSelectTrack: (Int, Int, Int) -> Unit,
    onClearOverride: (Int) -> Unit
) {
    val tokens = AppDesignSystem

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "LEGENDAS",
            style = tokens.typography.title,
            color = tokens.colors.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        val textGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()
        val isOffSelected = textGroups.none { it.isSelected }

        TvOptionRowItem(
            label = "DESATIVADAS",
            sublabel = "Sem exibição de legendas",
            isSelected = isOffSelected,
            onClick = { onClearOverride(C.TRACK_TYPE_TEXT) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        textGroups.forEachIndexed { groupIdx, group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val isSelected = group.isTrackSelected(i)
                val lang = format.language?.uppercase() ?: "PORTUGUÊS"
                val label = if (format.label != null) "$lang (${format.label})" else lang

                TvOptionRowItem(
                    label = label,
                    sublabel = "Legendas integradas",
                    isSelected = isSelected,
                    onClick = { onSelectTrack(groupIdx, i, C.TRACK_TYPE_TEXT) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }


    }
}

// --- SISTEMA / SLEEP TIMER ---
@Composable
private fun SystemSettingsSection(
    sleepTimer: Int?,
    onSetSleepTimer: (Int?) -> Unit
) {
    val tokens = AppDesignSystem

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "TEMPORIZADOR (SLEEP TIMER)",
            style = tokens.typography.title,
            color = tokens.colors.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Interrompe a reprodução automaticamente após o tempo selecionado.",
            style = tokens.typography.caption,
            color = tokens.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))

        val options = listOf(
            null to "DESLIGADO",
            15 to "15 MINUTOS",
            30 to "30 MINUTOS",
            60 to "60 MINUTOS (1 HORA)",
            120 to "120 MINUTOS (2 HORAS)"
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(options) { _, (minutes, label) ->
                TvOptionRowItem(
                    label = label,
                    sublabel = if (minutes == null) "O player continuará reproduzindo" else "Desligar player em $minutes minutos",
                    isSelected = sleepTimer == minutes,
                    onClick = { onSetSleepTimer(minutes) }
                )
            }
        }
    }
}

// --- REUSABLE TV-FIRST FOCUSABLE ROW (FASE 2E FULL LINE FOCUS) ---
@Composable
fun TvOptionRowItem(
    label: String,
    sublabel: String? = null,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        if (isFocused) tokens.colors.primary
        else if (isSelected) tokens.colors.primary.copy(alpha = 0.16f)
        else Color.White.copy(alpha = 0.04f),
        label = "rowBg"
    )

    val textColor by animateColorAsState(
        if (isFocused) Color.Black
        else tokens.colors.textPrimary,
        label = "rowText"
    )

    val subtextColor by animateColorAsState(
        if (isFocused) Color.Black.copy(alpha = 0.75f)
        else tokens.colors.textSecondary,
        label = "rowSubtext"
    )

    val iconColor by animateColorAsState(
        if (isFocused) Color.Black
        else if (isSelected) tokens.colors.primary
        else tokens.colors.textSecondary.copy(alpha = 0.4f),
        label = "rowIcon"
    )

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            if (isFocused) 2.5.dp else if (isSelected) 1.dp else 0.dp,
            if (isFocused) FocusGlowCyan else if (isSelected) tokens.colors.primary.copy(alpha = 0.4f) else Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .adaptiveFocus(
                shape = RoundedCornerShape(14.dp),
                focusedScale = 1.03f,
                onFocus = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = textColor,
                    style = tokens.typography.title,
                    fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Bold
                )
                if (!sublabel.isNullOrEmpty()) {
                    Text(
                        text = sublabel,
                        color = subtextColor,
                        style = tokens.typography.caption,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun NoOptionsPlaceholder() {
    val tokens = AppDesignSystem
    Text(
        text = "NENHUMA OPÇÃO ADICIONAL DISPONÍVEL PARA ESTE STREAM.",
        color = tokens.colors.textSecondary.copy(alpha = 0.6f),
        style = tokens.typography.body
    )
}

@Composable
fun PlaybackSpeedSettingsSection(
    currentSpeed: Float,
    onSetSpeed: (Float) -> Unit
) {
    val tokens = AppDesignSystem
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "VELOCIDADE DE REPRODUÇÃO",
            style = tokens.typography.title,
            color = tokens.colors.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ajuste a velocidade de reprodução do filme ou episódio.",
            style = tokens.typography.caption,
            color = tokens.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(speeds) { _, speed ->
                val label = "${speed}x"
                val sublabel = when (speed) {
                    1.0f -> "Velocidade Normal (Padrão)"
                    0.5f, 0.75f -> "Câmera Lenta"
                    else -> "Acelerado"
                }
                TvOptionRowItem(
                    label = label,
                    sublabel = sublabel,
                    isSelected = Math.abs(currentSpeed - speed) < 0.05f,
                    onClick = { onSetSpeed(speed) }
                )
            }
        }
    }
}

@Composable
fun PlayerSpeedDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSetSpeed: (Float) -> Unit
) {
    val tokens = AppDesignSystem
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        try { initialFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Back) {
                    onDismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(420.dp)
                .wrapContentHeight()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0D121C),
            border = BorderStroke(1.5.dp, tokens.colors.primary.copy(alpha = 0.5f)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = tokens.colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "VELOCIDADE",
                            style = tokens.typography.title,
                            fontWeight = FontWeight.Black,
                            color = tokens.colors.textPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    AppIconButton(
                        icon = Icons.Rounded.Close,
                        onClick = onDismiss,
                        tint = tokens.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    speeds.forEachIndexed { idx, speed ->
                        val isSelected = Math.abs(currentSpeed - speed) < 0.05f
                        TvOptionRowItem(
                            label = "${speed}x" + if (speed == 1.0f) " (NORMAL)" else "",
                            sublabel = null,
                            isSelected = isSelected,
                            modifier = if (idx == 2) Modifier.focusRequester(initialFocusRequester) else Modifier,
                            onClick = {
                                onSetSpeed(speed)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 960, heightDp = 540, showBackground = true)
@Composable
private fun ChannelVersionSettingsPreview() {
    MeusCanaisTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSettingsDialog(
                tracks = null, sleepTimer = null, initialTab = 5,
                onDismiss = {}, onSelectTrack = { _, _, _ -> }, onClearOverride = {},
                onSetSleepTimer = {}, isLive = true, groupChannels = true,
                versions = listOf(
                    rsv.squitv.domain.model.ChannelVersion(101, "Canal SP HD", rsv.squitv.domain.model.ChannelQuality.HD),
                    rsv.squitv.domain.model.ChannelVersion(102, "Canal SP FHD HEVC", rsv.squitv.domain.model.ChannelQuality.FHD, "H.265")
                ),
                selectedVersionId = 101)
        }
    }
}
