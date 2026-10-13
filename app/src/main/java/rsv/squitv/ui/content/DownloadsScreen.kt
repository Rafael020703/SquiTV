package rsv.squitv.ui.content

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.components.states.EmptyState
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.local.entities.DownloadEntity
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.hilt.navigation.compose.hiltViewModel
import rsv.squitv.util.rememberWindowInfo
import java.util.Locale

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: MainViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onPlay: (Int, String, String, String?, Int?, Int?) -> Unit
) {
    val downloads by libraryViewModel.downloads.collectAsState()
    var selectedTab by remember { mutableIntStateOf(1) } // Default to Movies
    val tabs = listOf("CANAIS", "FILMES", "SÉRIES")
    val windowInfo = rememberWindowInfo()
    val tokens = AppDesignSystem

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                title = stringResource(R.string.downloads_nav_title),
                subtitle = stringResource(R.string.downloads_nav_subtitle),
                onBack = onBack
            )

            Surface(
                color = tokens.colors.surface.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = tokens.colors.primary,
                    indicator = { 
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(selectedTab),
                            color = tokens.colors.primary
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    style = tokens.typography.label,
                                    fontWeight = if (selectedTab == index) FontWeight.Black else FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = if (selectedTab == index) tokens.colors.primary else tokens.colors.textSecondary.copy(alpha = 0.5f)
                                )
                            }
                        )
                    }
                }
            }

            val filteredDownloads = remember(downloads, selectedTab) {
                when (selectedTab) {
                    0 -> downloads.filter { it.type == "live" }
                    1 -> downloads.filter { it.type == "movie" || it.type == "vod" }
                    2 -> downloads.filter { it.type == "series" }
                    else -> downloads
                }
            }

            DownloadsContent(
                downloads = filteredDownloads,
                onPlay = onPlay,
                onDelete = { libraryViewModel.removeDownload(it) },
                isExpanded = windowInfo.isExpanded
            )
        }
    }
}

@Composable
fun DownloadsContent(
    downloads: List<DownloadEntity>,
    onPlay: (Int, String, String, String?, Int?, Int?) -> Unit,
    onDelete: (Int) -> Unit,
    isExpanded: Boolean
) {
    val groupedDownloads = remember(downloads) {
        downloads.groupBy { it.seriesId ?: -1 }
    }
    val tokens = AppDesignSystem

    Box(modifier = Modifier.fillMaxSize()) {
        if (downloads.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_results_found),
                description = "Baixe seus filmes e episódios favoritos para assistir sem internet.",
                icon = Icons.Rounded.DownloadForOffline
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(if (isExpanded) tokens.spacing.giant else tokens.spacing.large),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.large)
            ) {
                groupedDownloads.forEach { (seriesId, items) ->
                    if (seriesId == -1) {
                        items(items) { download ->
                            DownloadItemCard(download, onPlay, onDelete, isExpanded)
                        }
                    } else {
                        item {
                            Text(
                                text = items.first().name.substringBefore(" - ").uppercase(),
                                style = tokens.typography.title,
                                fontWeight = FontWeight.Black,
                                color = tokens.colors.primary,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(bottom = tokens.spacing.small, top = tokens.spacing.medium)
                            )
                        }
                        items(items) { download ->
                            DownloadItemCard(download, onPlay, onDelete, isExpanded)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    download: DownloadEntity,
    onPlay: (Int, String, String, String?, Int?, Int?) -> Unit,
    onDelete: (Int) -> Unit,
    isExpanded: Boolean
) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { onPlay(download.streamId, download.name, download.type, download.container, download.seriesId, download.seasonNumber) },
        modifier = Modifier
            .fillMaxWidth()
            .adaptiveFocus(
                shape = tokens.shapes.medium,
                onFocus = { isFocused = it }
            ),
        shape = tokens.shapes.medium,
        color = if (isFocused) tokens.colors.surfaceElevated else tokens.colors.surface.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, if (isFocused) tokens.colors.primary else Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(if (isExpanded) 180.dp else 120.dp).aspectRatio(1.77f).clip(tokens.shapes.small)) {
                AsyncImage(
                    model = download.icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                if (download.status == "DOWNLOADING") {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { download.progress / 100f }, 
                            modifier = Modifier.size(36.dp), 
                            strokeWidth = 3.dp,
                            color = tokens.colors.primary
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(tokens.spacing.extraLarge))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.name.uppercase(), 
                    style = tokens.typography.title, 
                    color = if (isFocused) tokens.colors.primary else tokens.colors.textPrimary, 
                    fontWeight = FontWeight.Black, 
                    maxLines = 1,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${download.duration ?: "N/D"} • ${formatFileSize(download.downloadedBytes)}".uppercase(),
                    style = tokens.typography.caption,
                    color = tokens.colors.textSecondary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
            }
            
            AppIconButton(
                icon = Icons.Rounded.Delete, 
                onClick = { onDelete(download.streamId) },
                tint = tokens.colors.error.copy(alpha = 0.7f)
            )
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
