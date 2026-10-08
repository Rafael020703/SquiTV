package rsv.squitv.ui.content.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
    val tokens = AppDesignSystem
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isExpanded) 36.dp else 20.dp, start = tokens.spacing.large, end = tokens.spacing.large), 
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = tokens.colors.backgroundSecondary.copy(alpha = 0.95f),
                shape = tokens.shapes.extraLarge,
                border = BorderStroke(1.dp, tokens.colors.primary.copy(alpha = 0.6f)),
                tonalElevation = 16.dp,
                modifier = Modifier.fillMaxWidth(if (isExpanded) 0.85f else 1f)
            ) {
                Row(modifier = Modifier.padding(tokens.spacing.large), verticalAlignment = Alignment.CenterVertically) {
                    // Logo Box
                    Box(
                        modifier = Modifier
                            .size(if (isExpanded) 120.dp else 90.dp)
                            .clip(tokens.shapes.large)
                            .background(tokens.colors.surface.copy(alpha = 0.1f))
                            .border(1.dp, tokens.colors.border.copy(alpha = 0.2f), tokens.shapes.large)
                    ) {
                        AsyncImage(
                            model = channel?.streamIcon,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(tokens.spacing.medium),
                            contentScale = ContentScale.Fit
                        )
                        if (channel?.num != null) {
                            Surface(
                                color = tokens.colors.primary,
                                shape = RoundedCornerShape(bottomEnd = 12.dp),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = channel.num.toString(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = tokens.typography.label,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(tokens.spacing.large))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel?.name?.uppercase() ?: "", 
                                color = tokens.colors.textPrimary, 
                                style = tokens.typography.headline, 
                                fontWeight = FontWeight.Black, 
                                maxLines = 1, 
                                overflow = TextOverflow.Ellipsis, 
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(tokens.spacing.medium))
                            QualityBadge(channel?.name)
                        }
                        
                        if (program != null) {
                            Text(
                                text = program.title ?: "", 
                                color = tokens.colors.primary, 
                                style = tokens.typography.title, 
                                fontWeight = FontWeight.Bold, 
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.small))
                            EpgMiniProgress(program, isExpanded)
                        } else {
                            Text(
                                text = stringResource(R.string.no_epg_available), 
                                color = tokens.colors.textSecondary.copy(alpha = 0.4f),
                                style = tokens.typography.body
                            )
                        }
                    }
                }
            }
        }
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
