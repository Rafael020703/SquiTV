package rsv.squitv.ui.content.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
    onSwitchStream: (XtreamStream) -> Unit,
    pipAvailable: Boolean = false,
    onInteraction: () -> Unit = {},
    onSeekingStateChanged: (Boolean) -> Unit = {}
) {
    val tokens = AppDesignSystem
    val isLive = uiState.contentType == ContentType.LIVE
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
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

        // Top row: stream title and current programme
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
            // PROGRESS BAR FOR VOD
            if (!isLive && uiState.duration > 0) {
                DpadSeekBar(
                    position = uiState.position,
                    duration = uiState.duration,
                    bufferedPosition = uiState.bufferedPosition,
                    onSeek = onSeek,
                    isTv = isTv,
                    onInteraction = onInteraction,
                    onSeekingStateChanged = onSeekingStateChanged
                )
            } else if (isLive && currentProgram != null) {
                EpgMiniProgress(currentProgram, isTv)
            }

            Spacer(modifier = Modifier.height(tokens.spacing.large))

            // BUTTONS ROW WITH EXACT GEOMETRIC CENTER PLAY/PAUSE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Wing
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pipAvailable) {
                        PlayerActionIcon(Icons.Rounded.PictureInPictureAlt, onEnterPip, isTv, contentDescription = stringResource(R.string.pip_label))
                        Spacer(Modifier.width(if (isTv) tokens.spacing.large else tokens.spacing.medium))
                    }
                    PlayerActionIcon(
                        if (uiState.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onToggleFavorite,
                        isTv,
                        tint = if (uiState.isFavorite) tokens.colors.error else Color.White,
                        contentDescription = stringResource(if (uiState.isFavorite) R.string.quick_favorite_remove else R.string.quick_favorite_add)
                    )
                    if (!isLive) {
                        Spacer(Modifier.width(if (isTv) tokens.spacing.large else tokens.spacing.medium))
                        PlayerActionIcon(Icons.Rounded.Replay10, onSeekBack, isTv, contentDescription = "Voltar 10s")
                    }
                }

                Spacer(Modifier.width(if (isTv) tokens.spacing.giant else tokens.spacing.large))

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

                Spacer(Modifier.width(if (isTv) tokens.spacing.giant else tokens.spacing.large))

                // Right Wing
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isLive) {
                        PlayerActionIcon(Icons.Rounded.Forward10, onSeekForward, isTv, contentDescription = "Avançar 10s")
                        Spacer(Modifier.width(if (isTv) tokens.spacing.large else tokens.spacing.medium))
                        if (uiState.nextEpisodeStreamId != null) {
                            PlayerActionIcon(Icons.Rounded.SkipNext, onNextEpisode, isTv, contentDescription = "Próximo Episódio")
                            Spacer(Modifier.width(if (isTv) tokens.spacing.large else tokens.spacing.medium))
                        }
                    }
                    PlayerActionIcon(Icons.Rounded.Settings, onShowSettings, isTv, contentDescription = "Configurações")
                }
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
    isTv: Boolean,
    onInteraction: () -> Unit = {},
    onSeekingStateChanged: (Boolean) -> Unit = {}
) {
    if (duration <= 0L) return

    val tokens = AppDesignSystem
    val forceHours = duration >= 3600_000L
    val scope = rememberCoroutineScope()

    val seekController = remember(duration, onSeek) {
        PlayerSeekController(duration, onSeek, onInteraction, onSeekingStateChanged)
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(position) {
        seekController.onPositionUpdate(position)
    }

    val displayPosition = if (isDragging) {
        (dragProgress * duration).toLong().coerceIn(0L, duration)
    } else {
        seekController.seekTarget ?: seekController.pendingConfirmedSeek ?: position
    }

    val currentProgress = (displayPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    val isSeekingActive = seekController.seekTarget != null || isDragging

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(displayPosition, forceHours)} / ${formatTime(duration, forceHours)}",
                color = if (isSeekingActive) tokens.colors.primary else tokens.colors.textPrimary,
                style = tokens.typography.label,
                fontWeight = FontWeight.Bold
            )
            if (isSeekingActive) {
                Surface(
                    color = tokens.colors.primary.copy(alpha = 0.2f),
                    shape = tokens.shapes.small,
                    border = BorderStroke(1.dp, tokens.colors.primary)
                ) {
                    Text(
                        text = "BUSCANDO...",
                        style = tokens.typography.caption,
                        fontWeight = FontWeight.Black,
                        color = tokens.colors.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTv) 28.dp else 24.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && seekController.seekTarget != null) {
                        seekController.commitSeek()
                    }
                }
                .adaptiveFocus(shape = tokens.shapes.medium)
                .onKeyEvent { event ->
                    onInteraction()
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                seekController.handleDpadSeek(position, -10000L, scope)
                                true
                            }
                            Key.DirectionRight -> {
                                seekController.handleDpadSeek(position, 10000L, scope)
                                true
                            }
                            Key.Back -> {
                                if (seekController.seekTarget != null) {
                                    seekController.cancelSeek()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    } else if (event.type == KeyEventType.KeyUp) {
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionRight -> true
                            else -> false
                        }
                    } else false
                }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = currentProgress,
                onValueChange = { newProgress ->
                    onInteraction()
                    isDragging = true
                    onSeekingStateChanged(true)
                    dragProgress = newProgress
                },
                onValueChangeFinished = {
                    val targetMs = (dragProgress * duration).toLong().coerceIn(0L, duration)
                    onSeek(targetMs)
                    isDragging = false
                    onSeekingStateChanged(false)
                    onInteraction()
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (isSeekingActive) tokens.colors.primary else tokens.colors.accent,
                    activeTrackColor = if (isSeekingActive) tokens.colors.primary else tokens.colors.accent,
                    inactiveTrackColor = tokens.colors.surface.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusable(false)
            )
        }
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
