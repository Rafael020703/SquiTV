package rsv.squitv.ui.epg

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.local.entities.EpgProgramEntity
import rsv.squitv.data.model.XtreamCategory
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.ui.content.player.QualityBadge
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.EpgViewModel
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EpgGridScreen(
    mainViewModel: MainViewModel,
    epgViewModel: EpgViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onChannelClick: (Int, String, String?, String?) -> Unit
) {
    val epgChannels by epgViewModel.epgChannels.collectAsStateWithLifecycle()
    val epgGridData by epgViewModel.epgGridData.collectAsStateWithLifecycle()
    val timeRange by epgViewModel.currentTimeRange.collectAsStateWithLifecycle()
    val categories by mainViewModel.categories.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        mainViewModel.loadLiveCategories()
        epgViewModel.syncEpg()
        delay(500)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(selectedCategoryId, favorites) {
        epgViewModel.loadChannelsForEpg(selectedCategoryId, favorites)
    }

    val windowInfo = rememberWindowInfo()

    PortalBackground {
        EpgGridContent(
            liveChannels = epgChannels,
            epgGridData = epgGridData,
            timeRange = timeRange,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelect = { selectedCategoryId = it },
            onBack = onBack,
            onChannelClick = { id, name, epgId -> onChannelClick(id, name, epgId, selectedCategoryId) },
            onShiftTime = epgViewModel::shiftTimeRange,
            isExpanded = windowInfo.isExpanded,
            focusRequester = focusRequester
        )
    }
}

@Composable
fun EpgGridContent(
    liveChannels: List<IptvItem>,
    epgGridData: Map<String, List<EpgProgramEntity>>,
    timeRange: Pair<Long, Long>,
    categories: List<XtreamCategory>,
    selectedCategoryId: String?,
    onCategorySelect: (String?) -> Unit,
    onBack: () -> Unit,
    onChannelClick: (Int, String, String?) -> Unit,
    onShiftTime: (Int) -> Unit,
    isExpanded: Boolean,
    focusRequester: FocusRequester
) {
    val tokens = AppDesignSystem
    val channelColumnWidth = if (isExpanded) 220.dp else 180.dp
    val hourWidth = 400.dp 
    
    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = stringResource(R.string.epg_label),
            actions = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconButton(icon = Icons.AutoMirrored.Rounded.NavigateBefore, onClick = { onShiftTime(-2) })
                    AppIconButton(icon = Icons.AutoMirrored.Rounded.NavigateNext, onClick = { onShiftTime(2) })
                }
            }
        )

        // Category Filter Row
        Surface(color = tokens.colors.surface.copy(alpha = 0.1f)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.medium),
                contentPadding = PaddingValues(horizontal = tokens.spacing.extraLarge)
            ) {
                item {
                    EpgFilterChip(
                        selected = selectedCategoryId == null,
                        label = stringResource(R.string.all_categories_label),
                        modifier = Modifier.focusRequester(focusRequester),
                        onClick = { onCategorySelect(null) }
                    )
                }
                items(categories) { cat ->
                    EpgFilterChip(
                        selected = selectedCategoryId == cat.categoryId,
                        label = cat.categoryName ?: "",
                        onClick = { onCategorySelect(cat.categoryId) }
                    )
                }
            }
        }

        // Time Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.colors.surface.copy(alpha = 0.05f))
                .padding(vertical = tokens.spacing.small)
        ) {
            Spacer(modifier = Modifier.width(channelColumnWidth))
            val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val startCal = Calendar.getInstance().apply { timeInMillis = timeRange.first * 1000 }
            repeat(7) {
                Text(
                    text = timeFormatter.format(startCal.time),
                    modifier = Modifier.width(hourWidth),
                    style = tokens.typography.label,
                    color = tokens.colors.primary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                startCal.add(Calendar.HOUR_OF_DAY, 1)
            }
        }

        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(liveChannels, key = { it.id }) { channel ->
                EpgChannelRow(
                    channel = channel,
                    programs = epgGridData[channel.id] ?: emptyList(),
                    timeRange = timeRange,
                    columnWidth = channelColumnWidth,
                    hourWidth = hourWidth,
                    isExpanded = isExpanded,
                    onChannelClick = onChannelClick
                )
            }
        }
    }
}

@Composable
fun EpgFilterChip(selected: Boolean, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        color = if (isFocused) tokens.colors.primary else if (selected) tokens.colors.primary.copy(alpha = 0.15f) else Color.Transparent,
        shape = tokens.shapes.medium,
        modifier = modifier.adaptiveFocus(shape = tokens.shapes.medium, onFocus = { isFocused = it })
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.padding(horizontal = tokens.spacing.large, vertical = tokens.spacing.small),
            style = tokens.typography.label,
            fontWeight = FontWeight.Black,
            color = if (isFocused) Color.Black else if (selected) tokens.colors.primary else tokens.colors.textPrimary
        )
    }
}

@Composable
fun EpgChannelRow(
    channel: IptvItem,
    programs: List<EpgProgramEntity>,
    timeRange: Pair<Long, Long>,
    columnWidth: Dp,
    hourWidth: Dp,
    isExpanded: Boolean,
    onChannelClick: (Int, String, String?) -> Unit
) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isExpanded) 90.dp else 70.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel Info
        Surface(
            modifier = Modifier
                .width(columnWidth)
                .fillMaxHeight()
                .adaptiveFocus(
                    shape = tokens.shapes.medium,
                    onFocus = { isFocused = it }
                )
                .clickable { onChannelClick(channel.id.toInt(), channel.name, channel.epgId) },
            color = if (isFocused) tokens.colors.primary.copy(alpha = 0.08f) else Color.Transparent,
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Row(modifier = Modifier.padding(tokens.spacing.small), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(if (isExpanded) 56.dp else 42.dp)) {
                    AsyncImage(
                        model = channel.icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(tokens.shapes.small),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.width(tokens.spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name.uppercase(), 
                        style = tokens.typography.caption, 
                        maxLines = 1, 
                        fontWeight = FontWeight.Black, 
                        overflow = TextOverflow.Ellipsis,
                        color = tokens.colors.textPrimary
                    )
                    QualityBadge(channel.name)
                }
            }
        }

        // Programs Grid
        Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
            programs.forEach { prog ->
                val start = prog.startTime.coerceAtLeast(timeRange.first)
                val stop = prog.stopTime.coerceAtMost(timeRange.second)
                
                if (stop > start) {
                    val offsetHours = (start - timeRange.first) / 3600f
                    val durationHours = (stop - start) / 3600f
                    
                    EpgProgramBlock(
                        program = prog,
                        modifier = Modifier
                            .offset(x = hourWidth * offsetHours)
                            .width(hourWidth * durationHours)
                            .fillMaxHeight()
                    )
                }
            }
            
            if (programs.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_info_label).uppercase(), 
                    color = tokens.colors.textSecondary.copy(alpha = 0.2f), 
                    modifier = Modifier.padding(tokens.spacing.large).align(Alignment.CenterStart),
                    style = tokens.typography.caption,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EpgProgramBlock(
    program: EpgProgramEntity,
    modifier: Modifier = Modifier
) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .padding(1.dp)
            .adaptiveFocus(
                shape = tokens.shapes.small,
                onFocus = { isFocused = it }
            )
            .focusable(),
        color = if (isFocused) tokens.colors.primary else tokens.colors.surface.copy(alpha = 0.15f),
        shape = tokens.shapes.small,
        border = if (isFocused) BorderStroke(1.dp, tokens.colors.primary) else null
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.small), verticalArrangement = Arrangement.Center) {
            Text(
                text = program.title.uppercase(),
                style = tokens.typography.caption,
                fontWeight = FontWeight.Black,
                color = if (isFocused) Color.Black else tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val timeStr = "${formatEpochTime(program.startTime)} - ${formatEpochTime(program.stopTime)}"
            Text(
                text = timeStr,
                style = tokens.typography.caption,
                color = if (isFocused) Color.Black.copy(alpha = 0.7f) else tokens.colors.textSecondary.copy(alpha = 0.5f),
                maxLines = 1,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
        }
    }
}

fun formatEpochTime(seconds: Long): String {
    val date = Date(seconds * 1000)
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
}
