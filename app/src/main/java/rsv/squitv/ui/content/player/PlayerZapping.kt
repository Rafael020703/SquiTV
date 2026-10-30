package rsv.squitv.ui.content.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import rsv.squitv.R
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.model.EpgProgramme
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.ui.viewmodel.PlayerUiState
import rsv.squitv.core.ui.components.buttons.AppIconButton

@Composable
fun QuickSwitchSidebar(
    visible: Boolean, 
    streams: List<XtreamStream>, 
    zappingEpg: Map<Int, EpgProgramme>, 
    onDismiss: () -> Unit, 
    onStreamSelected: (XtreamStream) -> Unit, 
    isExpanded: Boolean = false
) {
    val tokens = AppDesignSystem
    AnimatedVisibility(
        visible = visible, 
        enter = slideInHorizontally(), 
        exit = slideOutHorizontally()
    ) {
        Surface(
            modifier = Modifier.fillMaxHeight().width(if (isExpanded) 400.dp else 300.dp), 
            color = tokens.colors.backgroundSecondary.copy(alpha = 0.95f), 
            tonalElevation = 8.dp
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(tokens.spacing.large), 
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.channels_label).uppercase(), 
                        color = tokens.colors.textPrimary, 
                        style = tokens.typography.title, 
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    AppIconButton(icon = Icons.Rounded.Close, onClick = onDismiss)
                }
                
                HorizontalDivider(color = tokens.colors.divider, thickness = 1.dp)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(), 
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(streams) { stream ->
                        ChannelListItem(
                            name = stream.name ?: "",
                            isSelected = false, 
                            onClick = { onStreamSelected(stream) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.ZappingBanner(
    isVisible: Boolean,
    channel: XtreamStream?,
    program: EpgProgramme?,
    isExpanded: Boolean
) {
    val colors = AppDesignSystem.colors
    val logoSize = if (isExpanded) 44.dp else 36.dp
    val programmeTitle = program?.title?.trim()?.takeIf { it.isNotEmpty() }
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(160), initialOffsetY = { -it / 3 }),
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120), targetOffsetY = { -it / 3 }),
        modifier = Modifier
            .align(Alignment.TopStart)
            .displayCutoutPadding()
            .padding(horizontal = if (isExpanded) 20.dp else 12.dp, vertical = if (isExpanded) 16.dp else 12.dp)
            .widthIn(max = if (isExpanded) 360.dp else 300.dp)
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            color = colors.backgroundSecondary.copy(alpha = 0.90f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactChannelLogo(channel?.streamIcon, logoSize)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        channel?.num?.takeIf { it > 0 }?.let { number ->
                            Text(
                                text = number.toString(),
                                modifier = Modifier.widthIn(max = 44.dp),
                                style = AppDesignSystem.typography.label.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = channel?.name?.trim().orEmpty(),
                            modifier = Modifier.weight(1f),
                            color = colors.textPrimary,
                            style = AppDesignSystem.typography.title.copy(fontSize = if (isExpanded) 17.sp else 15.sp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (programmeTitle != null) {
                        Text(
                            text = programmeTitle,
                            color = colors.textSecondary,
                            style = AppDesignSystem.typography.body.copy(fontSize = if (isExpanded) 13.sp else 12.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = { calculateProgramProgress(program) },
                            modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                            color = colors.primary.copy(alpha = 0.8f),
                            trackColor = Color.White.copy(alpha = 0.08f),
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactChannelLogo(url: String?, size: Dp) {
    val colors = AppDesignSystem.colors
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val logoUrl = url?.trim()?.takeIf { it.isNotEmpty() }
    val request = remember(context, logoUrl, sizePx) {
        ImageRequest.Builder(context).data(logoUrl).size(sizePx).crossfade(false).build()
    }
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl != null) AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(4.dp),
            contentScale = ContentScale.Fit,
            onLoading = { logoLoaded = false },
            onSuccess = { logoLoaded = true },
            onError = { logoLoaded = false }
        )
        if (!logoLoaded) Icon(Icons.Rounded.LiveTv, null, tint = colors.textSecondary, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun FullInfoPanel(isVisible: Boolean, uiState: PlayerUiState.Playing, onDismiss: () -> Unit) {
    if (!isVisible) return
    val tokens = AppDesignSystem
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Surface(
            modifier = Modifier.padding(tokens.spacing.large).width(350.dp),
            color = tokens.colors.backgroundSecondary.copy(alpha = 0.95f),
            shape = tokens.shapes.large,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(tokens.spacing.large), verticalArrangement = Arrangement.spacedBy(tokens.spacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.technical_info_title).uppercase(), 
                        style = tokens.typography.label, 
                        fontWeight = FontWeight.Black, 
                        color = tokens.colors.primary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { 
                        Icon(Icons.Rounded.Close, null, tint = tokens.colors.textSecondary) 
                    }
                }
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                
                InfoRow("Resolução", uiState.resolution ?: "Desconhecida")
                InfoRow("Codec Vídeo", uiState.videoCodec?.split("/")?.lastOrNull() ?: "---")
                InfoRow("Codec Áudio", uiState.audioCodec?.split("/")?.lastOrNull() ?: "---")
                InfoRow("Bitrate Est.", "%.2f Mbps".format(uiState.bandwidthMbps))
                InfoRow("Buffer", "${uiState.bufferDelaySeconds}s")
                InfoRow("Framerate", "%.1f fps".format(uiState.frameRate ?: 0f))
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    val tokens = AppDesignSystem
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = tokens.colors.textSecondary.copy(alpha = 0.6f), style = tokens.typography.caption)
        Text(value, color = tokens.colors.textPrimary, style = tokens.typography.caption, fontWeight = FontWeight.Bold)
    }
}
