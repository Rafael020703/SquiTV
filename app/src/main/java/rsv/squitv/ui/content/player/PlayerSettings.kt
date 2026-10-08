package rsv.squitv.ui.content.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    subtitleSize: Float,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onSelectTrack: (Int, Int, Int) -> Unit,
    onClearOverride: (Int) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onSetSubtitleSize: (Float) -> Unit
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
                .width(540.dp)
                .fillMaxHeight(0.88f)
                .padding(end = 40.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0D121C),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.15f)),
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
                            .width(180.dp)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val categories = listOf(
                            Triple(0, "VÍDEO", Icons.Rounded.VideoSettings),
                            Triple(1, "ÁUDIO", Icons.Rounded.InterpreterMode),
                            Triple(2, "LEGENDAS", Icons.Rounded.ClosedCaption),
                            Triple(3, "SISTEMA", Icons.Rounded.SettingsSuggest)
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
                            0 -> VideoQualitySettingsSection(
                                tracks = tracks,
                                onSelectTrack = onSelectTrack,
                                onClearOverride = onClearOverride
                            )
                            1 -> AudioTrackSettingsSection(
                                tracks = tracks,
                                onSelectTrack = onSelectTrack,
                                onClearOverride = onClearOverride
                            )
                            2 -> SubtitleSettingsSection(
                                tracks = tracks,
                                subtitleSize = subtitleSize,
                                onSelectTrack = onSelectTrack,
                                onClearOverride = onClearOverride,
                                onSetSubtitleSize = onSetSubtitleSize
                            )
                            3 -> SystemSettingsSection(
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
                style = tokens.typography.label,
                fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- VÍDEO / QUALIDADE DE VÍDEO (FASE 2E - TV-FIRST LIST) ---
@Composable
private fun VideoQualitySettingsSection(
    tracks: Tracks?,
    onSelectTrack: (Int, Int, Int) -> Unit,
    onClearOverride: (Int) -> Unit
) {
    val tokens = AppDesignSystem
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "QUALIDADE DE VÍDEO",
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

        val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val isAutoSelected = videoGroups.none { it.isSelected }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Option 1: AUTOMÁTICO
            item {
                TvOptionRowItem(
                    label = "AUTOMÁTICO (RECOMENDADO)",
                    sublabel = "Ajusta a resolução dinamicamente conforme a conexão",
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
                    val label = if (height > 0) "${height}p (${format.frameRate.toInt().coerceAtLeast(24)} fps)" else "Qualidade Padrão"
                    val sublabel = if (height >= 1080) "Alta Definição (Full HD)" else if (height >= 720) "Alta Definição (HD)" else "Definição Padrão (SD)"

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
    subtitleSize: Float,
    onSelectTrack: (Int, Int, Int) -> Unit,
    onClearOverride: (Int) -> Unit,
    onSetSubtitleSize: (Float) -> Unit
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

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "TAMANHO DA FONTE DAS LEGENDAS",
            style = tokens.typography.body,
            color = tokens.colors.textSecondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val sizes = listOf(
                0.8f to "PEQUENA",
                1.0f to "MÉDIA",
                1.3f to "GRANDE"
            )
            sizes.forEach { (size, label) ->
                TvChipItem(
                    label = label,
                    isSelected = subtitleSize == size,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetSubtitleSize(size) }
                )
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
        modifier = Modifier
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
private fun TvChipItem(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        if (isFocused) tokens.colors.primary
        else if (isSelected) tokens.colors.primary.copy(alpha = 0.2f)
        else Color.White.copy(alpha = 0.05f),
        label = "chipBg"
    )
    val contentColor by animateColorAsState(
        if (isFocused) Color.Black
        else tokens.colors.textPrimary,
        label = "chipText"
    )

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
            if (isFocused) FocusGlowCyan else if (isSelected) tokens.colors.primary else Color.Transparent
        ),
        modifier = modifier.adaptiveFocus(shape = RoundedCornerShape(10.dp), onFocus = { isFocused = it })
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = tokens.typography.label,
                fontWeight = FontWeight.Black,
                color = contentColor
            )
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
