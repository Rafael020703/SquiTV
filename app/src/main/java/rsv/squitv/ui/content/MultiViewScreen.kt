package rsv.squitv.ui.content

import androidx.annotation.OptIn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import rsv.squitv.R
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.ui.viewmodel.MultiViewViewModel
import rsv.squitv.ui.viewmodel.MainViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.shared.catalog.MultiViewPolicy
import rsv.squitv.core.ui.components.player.MultiViewSlotSurface
import rsv.squitv.core.ui.components.player.MultiViewEmptyContent
import rsv.squitv.core.ui.components.player.MultiViewGrid
import rsv.squitv.core.ui.components.player.ChannelPickerState
import rsv.squitv.core.ui.components.player.ChannelPickerContent
import rsv.squitv.core.ui.components.player.VideoScaleChoices
import rsv.squitv.shared.player.VideoScaleMode
import rsv.squitv.ui.content.player.AndroidVideoViewport
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clipToBounds

@OptIn(UnstableApi::class)
@Composable
fun MultiViewScreen(
    mainViewModel: MainViewModel,
    viewModel: MultiViewViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val players by viewModel.players.collectAsState()
    val activeChannels by viewModel.activeChannels.collectAsState()
    val focusedIndex by viewModel.focusedIndex.collectAsState()
    
    var showChannelPicker by remember { mutableStateOf(false) }
    var pickingForIndex by remember { mutableStateOf(0) }
    
    val catalog by viewModel.pickerCatalog.collectAsState()
    val loading by viewModel.pickerLoading.collectAsState()
    val error by viewModel.pickerError.collectAsState()
    val pickerState = remember(showChannelPicker) { ChannelPickerState() }
    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    val focusRequester = remember { FocusRequester() }
    val tokens = AppDesignSystem
    DisposableEffect(viewModel) { onDispose { viewModel.closePicker() } }

    BackHandler(enabled = showChannelPicker) {
        if (!pickerState.back()) { showChannelPicker = false; viewModel.closePicker() }
    }

    LaunchedEffect(Unit) {
        delay(500)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                title = stringResource(R.string.multiview_nav_title),
                subtitle = stringResource(R.string.multiview_nav_subtitle),
                actions = { VideoScaleChoices(scaleMode) { scaleMode = it } }
            )

            MultiViewGrid(Modifier.weight(1f).padding(tokens.spacing.large)) { index, slotModifier ->
                PlayerSlot(index, players[index], activeChannels[index], focusedIndex == index,
                    modifier = slotModifier.then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                    onFocus = { viewModel.setFocus(index) }, onAdd = {
                        pickingForIndex = index; showChannelPicker = true; viewModel.openPicker()
                    }, onRemove = { viewModel.removeChannel(index) }, scaleMode = scaleMode)
            }
        }

        if (showChannelPicker) Dialog(onDismissRequest = {
            if (!pickerState.back()) { showChannelPicker = false; viewModel.closePicker() }
        }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxHeight().widthIn(max = 460.dp).fillMaxWidth(), color = tokens.colors.surface) {
                ChannelPickerContent(catalog, pickerState, loading, error, viewModel::openPicker,
                    { showChannelPicker = false; viewModel.closePicker() }, { channel ->
                        viewModel.playSelection(pickingForIndex, channel)
                        showChannelPicker = false; viewModel.closePicker()
                    })
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerSlot(
    index: Int,
    player: Player?,
    channel: XtreamStream?,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    scaleMode: VideoScaleMode = VideoScaleMode.FIT
) {
    val context = LocalContext.current
    val tokens = AppDesignSystem
    
    MultiViewSlotSurface(
        modifier = modifier
            .clipToBounds()
            .adaptiveFocus(
                shape = tokens.shapes.large,
                onFocus = { if (it) onFocus() }
            )
            .clickable { if (channel == null) onAdd() },
        shape = tokens.shapes.large,
        background = tokens.colors.background,
        primary = tokens.colors.primary,
        focused = isFocused
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (player != null && channel != null) {
                AndroidView(
                    factory = { AndroidVideoViewport(it).apply { bind(player, scaleMode) } },
                    update = { it.bind(player, scaleMode) },
                    onRelease = { it.releaseSurface() },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Overlay info
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(end = 48.dp)
                        .padding(tokens.spacing.medium)
                        .background(tokens.colors.background.copy(alpha = 0.7f), tokens.shapes.small)
                        .padding(horizontal = tokens.spacing.small, vertical = tokens.spacing.tiny),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}. ${channel.name?.uppercase()}",
                        color = tokens.colors.textPrimary,
                        style = tokens.typography.caption,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).padding(tokens.spacing.tiny)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cancel, 
                        contentDescription = null, 
                        tint = Color.White.copy(alpha = 0.4f), 
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (isFocused) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = tokens.colors.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(tokens.spacing.medium).size(28.dp)
                    )
                }
            } else {
                MultiViewEmptyContent(isFocused, tokens.colors.primary, tokens.colors.textPrimary,
                    tokens.colors.textSecondary, tokens.typography.label, "ADICIONAR CANAL")
            }
        }
    }
}
