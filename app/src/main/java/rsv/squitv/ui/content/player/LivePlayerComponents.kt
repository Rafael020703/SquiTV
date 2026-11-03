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
import rsv.squitv.core.ui.components.player.LivePlayerCard
import rsv.squitv.core.ui.components.player.PlayerRoundButton
import rsv.squitv.core.ui.components.player.PlayerChannelRow

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
    nextPrograms: List<EpgProgramme> = emptyList(),
    isControlsVisible: Boolean,
    isReconnecting: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShowSettings: () -> Unit,
    onEnterPip: (() -> Unit)? = null
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
        LivePlayerCard(streamName, currentProgram?.title ?: stringResource(R.string.no_epg_available),
            isExpanded, tokens.colors.backgroundSecondary, tokens.colors.primary, tokens.colors.textPrimary,
            tokens.colors.textSecondary, tokens.typography.title, tokens.typography.body, tokens.typography.caption,
            badge = {
                LiveBadge()
                if (isReconnecting) { Spacer(Modifier.width(8.dp)); ReconnectingBadge() }
            }, logo = {
                Surface(Modifier.size(if (isExpanded) 80.dp else 60.dp), shape = tokens.shapes.medium,
                    color = tokens.colors.surface.copy(alpha = .2f),
                    border = BorderStroke(1.dp, tokens.colors.border.copy(alpha = .2f))) {
                    AsyncImage(model = streamIcon, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(8.dp))
                }
            }, actions = {
                ControlIconButton(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    onToggleFavorite, stringResource(if (isFavorite) R.string.quick_favorite_remove else R.string.quick_favorite_add),
                    tint = if (isFavorite) tokens.colors.error else Color.White,
                    modifier = Modifier.focusRequester(firstBtnFocusRequester))
                if (onEnterPip != null) ControlIconButton(Icons.Rounded.PictureInPictureAlt, onEnterPip,
                    stringResource(R.string.pip_label))
                ControlIconButton(Icons.Rounded.Settings, onShowSettings, "Configurações")
            }, progress = if (currentProgram != null) { { EpgMiniProgress(currentProgram, isExpanded) } } else null,
            upcoming = nextPrograms.take(2).map {
                stringResource(R.string.upcoming_program, formatProgramTime(it.start), it.title.orEmpty())
            })
    }
}

@Composable
fun ControlIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String? = null,
    tint: Color? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val tokens = AppDesignSystem

    val bgColor by animateColorAsState(
        if (isFocused) tokens.colors.primary else Color.White.copy(alpha = 0.12f),
        label = "btnBg"
    )
    val iconColor by animateColorAsState(
        if (isFocused) Color.Black else (tint ?: Color.White),
        label = "btnIcon"
    )

    PlayerRoundButton(icon, contentDescription, onClick, bgColor, iconColor,
        if (isFocused) FocusGlowCyan else Color.White.copy(alpha = .15f),
        if (isFocused) 2.5.dp else 1.dp,
        modifier.adaptiveFocus(shape = CircleShape, focusedScale = 1.12f, onFocus = { isFocused = it }))
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
    
    PlayerChannelRow(name, isSelected, isFocused, tokens.colors.primary, textColor, bgColor,
        tokens.typography.title, Modifier
            .adaptiveFocus(shape = RoundedCornerShape(0.dp), onFocus = { isFocused = it })
            .clickable { onClick() })
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
