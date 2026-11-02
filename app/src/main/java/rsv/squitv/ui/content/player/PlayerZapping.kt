package rsv.squitv.ui.content.player

import rsv.squitv.core.ui.components.player.CompactChannelBanner
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
        modifier = Modifier.align(Alignment.TopStart).displayCutoutPadding()
            .padding(horizontal = if (isExpanded) 20.dp else 12.dp, vertical = if (isExpanded) 16.dp else 12.dp)
            .widthIn(max = if (isExpanded) 360.dp else 300.dp).fillMaxWidth()
    ) {
        CompactChannelBanner(
            name = channel?.name.orEmpty(), number = channel?.num,
            programme = programmeTitle, progress = program?.let(::calculateProgramProgress) ?: 0f,
            expanded = isExpanded, background = colors.backgroundSecondary, primary = colors.primary,
            text = colors.textPrimary, secondary = colors.textSecondary,
            titleStyle = AppDesignSystem.typography.title, bodyStyle = AppDesignSystem.typography.body,
            labelStyle = AppDesignSystem.typography.label
        ) { CompactChannelLogo(channel?.streamIcon, logoSize) }
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
