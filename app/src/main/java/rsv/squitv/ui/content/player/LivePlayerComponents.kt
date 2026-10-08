package rsv.squitv.ui.content.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import rsv.squitv.R
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.model.EpgProgramme
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.util.rememberWindowInfo

@Composable
fun ReconnectingBadge() {
    val tokens = AppDesignSystem
    Surface(
        color = tokens.colors.warning.copy(alpha = 0.2f),
        shape = tokens.shapes.small,
        border = BorderStroke(1.dp, tokens.colors.warning)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = tokens.colors.warning,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.reconnecting_stream).uppercase(),
                style = tokens.typography.caption.copy(fontSize = 10.sp),
                color = tokens.colors.warning,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BoxScope.LiveBottomOverlay(
    streamName: String,
    streamIcon: String?,
    currentProgram: EpgProgramme?,
    isControlsVisible: Boolean,
    isReconnecting: Boolean = false,
    onShowSettings: () -> Unit,
    onToggleResize: () -> Unit,
    onShowAudio: () -> Unit,
    onShowSubtitles: () -> Unit,
    onShowQuality: () -> Unit = {}
) {
    val tokens = AppDesignSystem
    val windowInfo = rememberWindowInfo()
    val isExpanded = windowInfo.isExpanded
    
    val firstBtnFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            kotlinx.coroutines.delay(150)
            try { firstBtnFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = isControlsVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isExpanded) tokens.spacing.extraLarge * 2 else tokens.spacing.large)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, tokens.colors.backgroundSecondary.copy(alpha = 0.95f))
                    ),
                    tokens.shapes.large
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), tokens.shapes.large)
                .padding(tokens.spacing.large)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Channel Logo
                Surface(
                    modifier = Modifier.size(if (isExpanded) 80.dp else 60.dp),
                    shape = tokens.shapes.medium,
                    color = tokens.colors.surface.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, tokens.colors.border.copy(alpha = 0.2f))
                ) {
                    AsyncImage(
                        model = streamIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(tokens.spacing.small)
                    )
                }

                Spacer(modifier = Modifier.width(tokens.spacing.large))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveBadge()
                        if (isReconnecting) {
                            Spacer(modifier = Modifier.width(tokens.spacing.small))
                            ReconnectingBadge()
                        }
                        Spacer(modifier = Modifier.width(tokens.spacing.small))
                        Text(
                            text = streamName.uppercase(),
                            color = tokens.colors.textPrimary,
                            style = tokens.typography.title,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = currentProgram?.title ?: stringResource(R.string.no_epg_available),
                        color = tokens.colors.primary,
                        style = tokens.typography.body,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Controls
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.medium)) {
                    ControlIconButton(
                        icon = Icons.Rounded.ClosedCaption,
                        onClick = onShowSubtitles,
                        contentDescription = "Legendas",
                        modifier = Modifier.focusRequester(firstBtnFocusRequester)
                    )
                    ControlIconButton(
                        icon = Icons.Rounded.VideoSettings,
                        onClick = onShowQuality,
                        contentDescription = "Qualidade"
                    )
                    ControlIconButton(
                        icon = Icons.Rounded.InterpreterMode,
                        onClick = onShowAudio,
                        contentDescription = "Áudio"
                    )
                    ControlIconButton(
                        icon = Icons.Rounded.AspectRatio,
                        onClick = onToggleResize,
                        contentDescription = "Proporção"
                    )
                    ControlIconButton(
                        icon = Icons.Rounded.Settings,
                        onClick = onShowSettings,
                        contentDescription = "Configurações"
                    )
                }
            }
            
            if (currentProgram != null) {
                Spacer(modifier = Modifier.height(tokens.spacing.large))
                EpgMiniProgress(currentProgram, isExpanded)
            }
        }
    }
}

@Composable
fun ControlIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val tokens = AppDesignSystem

    val bgColor by animateColorAsState(
        if (isFocused) tokens.colors.primary else Color.White.copy(alpha = 0.12f),
        label = "btnBg"
    )
    val iconColor by animateColorAsState(
        if (isFocused) Color.Black else Color.White,
        label = "btnIcon"
    )

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = CircleShape,
        border = BorderStroke(
            if (isFocused) 2.5.dp else 1.dp,
            if (isFocused) FocusGlowCyan else Color.White.copy(alpha = 0.15f)
        ),
        modifier = modifier
            .size(52.dp)
            .adaptiveFocus(
                shape = CircleShape,
                focusedScale = 1.18f,
                onFocus = { isFocused = it }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun LiveSplitView(
    categoryName: String,
    channels: List<XtreamStream>,
    selectedChannelId: Int,
    currentProgram: EpgProgramme?,
    onChannelSelect: (XtreamStream) -> Unit
) {
    val tokens = AppDesignSystem
    val windowInfo = rememberWindowInfo()
    val isExpanded = windowInfo.isExpanded

    Box(modifier = Modifier.fillMaxSize()) {
        // Sidebar - Channel List (Left Overlay)
        Column(
            modifier = Modifier
                .width(if (isExpanded) 400.dp else 320.dp)
                .fillMaxHeight()
                .background(tokens.colors.backgroundSecondary.copy(alpha = 0.95f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(0.dp))
        ) {
            // Category Header
            Surface(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = tokens.spacing.large),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = tokens.colors.primary)
                    Text(
                        text = categoryName.uppercase(),
                        color = tokens.colors.textPrimary,
                        style = tokens.typography.title,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = tokens.colors.primary)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = tokens.spacing.medium)
            ) {
                itemsIndexed(channels) { _, channel ->
                    val isSelected = channel.streamId == selectedChannelId
                    ChannelListItem(
                        name = channel.name ?: "Canal Sem Nome",
                        isSelected = isSelected,
                        onClick = { onChannelSelect(channel) }
                    )
                }
            }
        }

        // Right Info Column (EPG Overlay)
        Column(
            modifier = Modifier
                .width(if (isExpanded) 450.dp else 350.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, tokens.colors.backgroundSecondary.copy(alpha = 0.95f))
                    )
                )
                .padding(tokens.spacing.extraLarge)
        ) {
            Spacer(modifier = Modifier.height(100.dp))
            
            Surface(
                color = tokens.colors.primary.copy(alpha = 0.15f),
                shape = tokens.shapes.small,
                border = BorderStroke(1.dp, tokens.colors.primary.copy(alpha = 0.4f))
            ) {
                Text(
                    text = stringResource(R.string.on_air_now).uppercase(),
                    color = tokens.colors.primary,
                    modifier = Modifier.padding(horizontal = tokens.spacing.medium, vertical = tokens.spacing.tiny),
                    style = tokens.typography.caption,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(tokens.spacing.large))
            
            Text(
                text = channels.find { it.streamId == selectedChannelId }?.name?.uppercase() ?: "CANAL",
                color = tokens.colors.textPrimary,
                style = tokens.typography.headline,
                fontWeight = FontWeight.Black
            )
            
            Spacer(modifier = Modifier.height(tokens.spacing.large))
            
            if (currentProgram != null) {
                Text(
                    text = currentProgram.title ?: "",
                    color = tokens.colors.primary,
                    style = tokens.typography.title,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(tokens.spacing.medium))
                Text(
                    text = currentProgram.description ?: "",
                    color = tokens.colors.textSecondary,
                    style = tokens.typography.body,
                    lineHeight = 24.sp
                )
                
                Spacer(modifier = Modifier.height(tokens.spacing.giant))
                EpgMiniProgress(currentProgram, true)
            } else {
                Text(
                    text = stringResource(R.string.epg_not_found),
                    color = tokens.colors.textSecondary.copy(alpha = 0.4f),
                    style = tokens.typography.body
                )
            }
        }
    }
}

@Composable
fun ChannelListItem(name: String, isSelected: Boolean, onClick: () -> Unit) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }
    
    val textColor by animateColorAsState(
        if (isFocused) Color.Black 
        else if (isSelected) tokens.colors.primary 
        else tokens.colors.textPrimary
    )
    val bgColor by animateColorAsState(
        if (isFocused) tokens.colors.primary 
        else if (isSelected) tokens.colors.primary.copy(alpha = 0.1f) 
        else Color.Transparent
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .adaptiveFocus(
                shape = RoundedCornerShape(0.dp),
                onFocus = { isFocused = it }
            )
            .clickable { onClick() }
            .background(bgColor)
            .padding(vertical = tokens.spacing.large, horizontal = tokens.spacing.extraLarge)
    ) {
        Text(
            text = name.uppercase(),
            color = textColor,
            style = tokens.typography.title,
            fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isSelected && !isFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(24.dp)
                    .background(tokens.colors.primary)
            )
        }
    }
}

@Composable
fun BoxScope.ZappingEdgeControl(onNext: () -> Unit, onPrev: () -> Unit) {
    val tokens = AppDesignSystem
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(end = tokens.spacing.large)
            .width(60.dp)
            .align(Alignment.CenterEnd),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ControlIconButton(Icons.Rounded.KeyboardArrowUp, onPrev)
        Spacer(modifier = Modifier.height(tokens.spacing.large))
        ControlIconButton(Icons.Rounded.KeyboardArrowDown, onNext)
    }
}
