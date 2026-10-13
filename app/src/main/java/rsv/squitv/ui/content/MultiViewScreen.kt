package rsv.squitv.ui.content

import androidx.annotation.OptIn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.media3.ui.PlayerView
import rsv.squitv.R
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.ui.viewmodel.MultiViewViewModel
import rsv.squitv.ui.viewmodel.MainViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import rsv.squitv.ui.content.player.QuickSwitchSidebar
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.core.ui.components.common.adaptiveFocus

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
    
    val liveStreams by mainViewModel.allLiveStreams.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val tokens = AppDesignSystem

    BackHandler(enabled = showChannelPicker) {
        showChannelPicker = false
    }

    LaunchedEffect(Unit) {
        mainViewModel.loadAllLiveStreams()
        delay(500)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                title = stringResource(R.string.multiview_nav_title),
                subtitle = stringResource(R.string.multiview_nav_subtitle),
                onBack = onBack
            )

            // Players Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(tokens.spacing.large),
                contentPadding = PaddingValues(tokens.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.large)
            ) {
                items(4) { index ->
                    PlayerSlot(
                        index = index,
                        player = players[index],
                        channel = activeChannels[index],
                        isFocused = focusedIndex == index,
                        modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                        onFocus = { viewModel.setFocus(index) },
                        onAdd = {
                            pickingForIndex = index
                            showChannelPicker = true
                        },
                        onRemove = { viewModel.removeChannel(index) }
                    )
                }
            }
        }

        // Channel Picker Sidebar
        QuickSwitchSidebar(
            visible = showChannelPicker,
            streams = liveStreams,
            zappingEpg = emptyMap(),
            onDismiss = { showChannelPicker = false },
            onStreamSelected = { stream ->
                viewModel.playChannel(pickingForIndex, stream)
                showChannelPicker = false
            },
            isExpanded = true
        )
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
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val tokens = AppDesignSystem
    
    Surface(
        modifier = modifier
            .aspectRatio(1.77f)
            .adaptiveFocus(
                shape = tokens.shapes.large,
                onFocus = { if (it) onFocus() }
            )
            .clickable { if (channel == null) onAdd() },
        shape = tokens.shapes.large,
        color = tokens.colors.background,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) tokens.colors.primary else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (player != null && channel != null) {
                AndroidView(
                    factory = {
                        PlayerView(context).apply {
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Overlay info
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(tokens.spacing.medium)
                        .background(tokens.colors.background.copy(alpha = 0.7f), tokens.shapes.small)
                        .padding(horizontal = tokens.spacing.small, vertical = tokens.spacing.tiny),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}. ${channel.name?.uppercase()}",
                        color = tokens.colors.textPrimary,
                        style = tokens.typography.caption,
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
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddCircle,
                        contentDescription = null,
                        tint = if (isFocused) tokens.colors.primary else tokens.colors.textSecondary.copy(alpha = 0.2f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.medium))
                    Text(
                        text = "ADICIONAR CANAL",
                        color = if (isFocused) tokens.colors.textPrimary else tokens.colors.textSecondary.copy(alpha = 0.3f),
                        style = tokens.typography.label,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
