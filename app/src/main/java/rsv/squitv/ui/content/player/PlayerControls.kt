package rsv.squitv.ui.content.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import rsv.squitv.R
import rsv.squitv.core.ui.theme.*
import rsv.squitv.domain.model.ContentType
import rsv.squitv.data.model.EpgProgramme
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.ui.viewmodel.PlayerUiState
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.buttons.AppButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerControlOverlay(
    streamName: String,
    streamIcon: String? = null,
    uiState: PlayerUiState.Playing,
    currentProgram: EpgProgramme?,
    nextPrograms: List<EpgProgramme>,
    recentChannels: List<XtreamStream>,
    focusRequester: FocusRequester,
    isExpanded: Boolean = false,
    isTv: Boolean = false,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBack: () -> Unit,
    onNextEpisode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReload: () -> Unit,
    onEnterPip: () -> Unit,
    onShowSettings: () -> Unit,
    onShowChannels: () -> Unit,
    onShowQuality: () -> Unit,
    onShowSpeed: () -> Unit,
    onToggleResizeMode: () -> Unit,
    onToggleLock: () -> Unit,
    onSwitchStream: (XtreamStream) -> Unit
) {
    val tokens = AppDesignSystem
    val isLive = uiState.contentType == ContentType.LIVE
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(500)
        try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Gradient for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.6f),
                        0.4f to Color.Transparent,
                        0.7f to tokens.colors.background.copy(alpha = 0.95f)
                    )
                )
        )

        // Top Row: Back button and Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isTv) tokens.spacing.giant else tokens.spacing.large)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = streamName.uppercase(),
                    style = if (isTv) tokens.typography.headline else tokens.typography.title,
                    fontWeight = FontWeight.Black,
                    color = tokens.colors.textPrimary,
                    letterSpacing = 1.sp
                )
                if (isLive && currentProgram != null) {
                    Text(
                        text = currentProgram.title ?: "",
                        style = tokens.typography.body,
                        color = tokens.colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Main Controls Column
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    horizontal = if (isTv) tokens.spacing.giant * 2 else tokens.spacing.large, 
                    vertical = if (isTv) tokens.spacing.giant else tokens.spacing.large
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // PROGRESS BAR
            if (!isLive) {
                DpadSeekBar(
                    position = uiState.position,
                    duration = uiState.duration,
                    bufferedPosition = uiState.bufferedPosition,
                    onSeek = onSeek,
                    isTv = isTv
                )
            } else if (currentProgram != null) {
                EpgMiniProgress(currentProgram, isTv)
            }

            Spacer(modifier = Modifier.height(tokens.spacing.large))

            // BUTTONS ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerActionIcon(Icons.Rounded.ClosedCaption, onShowSettings, isTv, contentDescription = "Legendas")
                Spacer(Modifier.width(if (isTv) tokens.spacing.xl else tokens.spacing.medium))
                PlayerActionIcon(Icons.Rounded.InterpreterMode, onShowSettings, isTv, contentDescription = "Áudio")
                
                Spacer(Modifier.width(if (isTv) tokens.spacing.xxl else tokens.spacing.large))
                
                // Play/Pause Center
                Surface(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(if (isTv) 90.dp else 72.dp)
                        .focusRequester(playPauseFocusRequester)
                        .adaptiveFocus(shape = CircleShape, focusedScale = 1.15f),
                    shape = CircleShape,
                    color = tokens.colors.primary,
                    contentColor = Color.Black
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(if (isTv) 50.dp else 40.dp)
                        )
                    }
                }

                Spacer(Modifier.width(if (isTv) tokens.spacing.xxl else tokens.spacing.large))

                PlayerActionIcon(
                    if (uiState.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    onToggleFavorite,
                    isTv,
                    tint = if (uiState.isFavorite) tokens.colors.error else Color.White
                )
                Spacer(Modifier.width(if (isTv) tokens.spacing.xl else tokens.spacing.medium))
                PlayerActionIcon(Icons.Rounded.Settings, onShowSettings, isTv, contentDescription = "Configurações")
            }
        }
    }
}

@Composable
fun DpadSeekBar(
    position: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit,
    isTv: Boolean
) {
    val tokens = AppDesignSystem
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    val displayPosition = seekTarget ?: position
    
    val scope = rememberCoroutineScope()
    var seekJob: Job? by remember { mutableStateOf(null) }

    val handleSeek = { delta: Long ->
        val current = seekTarget ?: position
        val newTarget = (current + delta).coerceIn(0, duration)
        seekTarget = newTarget
        
        seekJob?.cancel()
        seekJob = scope.launch {
            delay(800)
            onSeek(newTarget)
            seekTarget = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.small)
            .adaptiveFocus(shape = tokens.shapes.medium)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> { handleSeek(-15000L); true }
                        Key.DirectionRight -> { handleSeek(15000L); true }
                        else -> false
                    }
                } else false
            }
            .focusable(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatTime(displayPosition),
            color = if (seekTarget != null) tokens.colors.primary else tokens.colors.textPrimary,
            style = tokens.typography.label,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp)
        )
        
        Box(modifier = Modifier.weight(1f).height(if (isTv) 12.dp else 8.dp).padding(horizontal = tokens.spacing.medium)) {
            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(tokens.colors.surface.copy(alpha = 0.2f)))
            val bufferedProgress = bufferedPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(bufferedProgress).clip(CircleShape).background(tokens.colors.surfaceVariant.copy(alpha = 0.3f)))
            val progress = displayPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(if (seekTarget != null) tokens.colors.primary else tokens.colors.accent))
        }

        Text(
            text = formatTime(duration),
            color = tokens.colors.textSecondary.copy(alpha = 0.6f),
            style = tokens.typography.label,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun PlayerActionIcon(
    icon: ImageVector, 
    onClick: () -> Unit, 
    isTv: Boolean, 
    tint: Color = Color.White,
    contentDescription: String? = null
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(if (isTv) 64.dp else 48.dp)
            .appFocus(shape = CircleShape)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(if (isTv) 32.dp else 24.dp)
        )
    }
}

@Composable
fun TvShortcutsGuide(isLive: Boolean) {
    val tokens = AppDesignSystem
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShortcutBadge(tokens.colors.error, stringResource(R.string.tv_shortcut_red))
        ShortcutBadge(tokens.colors.success, stringResource(R.string.tv_shortcut_green))
        ShortcutBadge(tokens.colors.warning, stringResource(R.string.tv_shortcut_yellow))
        if (isLive) {
            ShortcutBadge(tokens.colors.primary, stringResource(R.string.tv_shortcut_blue))
        }
    }
}

@Composable
fun ShortcutBadge(color: Color, label: String) {
    val tokens = AppDesignSystem
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tokens.spacing.tiny)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            text = label.uppercase(), 
            color = tokens.colors.textSecondary.copy(alpha = 0.6f), 
            style = tokens.typography.caption, 
            fontWeight = FontWeight.Bold
        )
    }
}
