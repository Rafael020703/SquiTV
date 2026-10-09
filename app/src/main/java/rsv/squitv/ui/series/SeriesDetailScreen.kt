@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
package rsv.squitv.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import rsv.squitv.R
import rsv.squitv.core.ui.components.badges.AgeBadge
import rsv.squitv.core.ui.components.badges.GenreBadge
import rsv.squitv.core.ui.components.badges.RatingBadge
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.buttons.AppSecondaryButton
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.components.states.LoadingState
import rsv.squitv.core.ui.components.content.ActorAvatar
import rsv.squitv.core.ui.components.content.EpisodeCard
import rsv.squitv.core.ui.components.content.MetadataItem
import rsv.squitv.core.ui.components.content.SeasonListItem
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.core.ui.theme.AppShapes
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.ui.dashboard.*
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.viewmodel.SeriesDetailViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.util.rememberWindowInfo
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import rsv.squitv.data.local.entities.EpisodeEntity
import rsv.squitv.data.local.entities.SeasonEntity
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.model.XtreamSeries

@Composable
fun SeriesDetailScreen(
    viewModel: SeriesDetailViewModel,
    mainViewModel: MainViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    seriesId: Int,
    seriesName: String,
    cover: String?,
    onBack: () -> Unit,
    onPlayEpisode: (Int, String, String) -> Unit,
    onActorClick: (String) -> Unit,
    onGenreClick: (String) -> Unit = {},
    onDirectorClick: (String) -> Unit = {}
) {
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val episodesMap by viewModel.episodesMap.collectAsStateWithLifecycle()
    val watchProgress by libraryViewModel.watchProgress.collectAsStateWithLifecycle()
    val selectedSeason by viewModel.selectedSeason.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    val credentials = mainViewModel.credentials

    val isFavorite = remember(favorites, seriesId) { 
        favorites.any { it.id == seriesId.toString() && (it.type == ContentType.SERIES) } 
    }

    val windowInfo = rememberWindowInfo()
    val similarContent by libraryViewModel.getSimilarContent(IptvItem(seriesId.toString(), seriesName, cover, ContentType.SERIES)).collectAsState()

    PortalBackground {
        SeriesDetailContent(
            seriesId = seriesId,
            seriesName = seriesName,
            cover = cover,
            seasons = seasons,
            episodesMap = episodesMap,
            watchProgress = watchProgress,
            selectedSeason = selectedSeason,
            isLoading = isLoading,
            isFavorite = isFavorite,
            credentials = credentials,
            onBack = onBack,
            onSeasonSelect = viewModel::selectSeason,
            onPlayEpisode = onPlayEpisode,
            onActorClick = onActorClick,
            onGenreClick = onGenreClick,
            onDirectorClick = onDirectorClick,
            onToggleFavorite = {
                val item = IptvItem(
                    id = seriesId.toString(),
                    name = seriesName,
                    icon = cover,
                    type = ContentType.SERIES
                )
                libraryViewModel.toggleFavorite(item, !isFavorite)
            },
            onDownloadSeason = { sId, sNum -> libraryViewModel.downloadSeason(sId, sNum) },
            onDownloadEpisode = { item, sId, sNum -> libraryViewModel.startDownload(item, sId, sNum) },
            getSeriesMetadata = { creds, id -> libraryViewModel.getSeriesMetadata(creds, id) },
            similarContent = similarContent,
            isExpanded = windowInfo.isExpanded,
            isTv = windowInfo.isTv
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailContent(
    seriesId: Int,
    seriesName: String,
    cover: String?,
    seasons: List<SeasonEntity>,
    episodesMap: Map<Int, List<EpisodeEntity>>,
    watchProgress: Map<Int, Float>,
    selectedSeason: Int?,
    isLoading: Boolean,
    isFavorite: Boolean,
    credentials: XtreamCredentials?,
    onBack: () -> Unit,
    onSeasonSelect: (Int) -> Unit,
    onPlayEpisode: (Int, String, String) -> Unit,
    onActorClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onDirectorClick: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadSeason: (Int, Int) -> Unit,
    onDownloadEpisode: (IptvItem, Int?, Int?) -> Unit,
    getSeriesMetadata: suspend (XtreamCredentials, Int) -> XtreamSeries?,
    similarContent: List<IptvItem>,
    isExpanded: Boolean,
    isTv: Boolean
) {
    val tokens = AppDesignSystem
    var seriesMetadata by remember { mutableStateOf<XtreamSeries?>(null) }
    
    if (credentials != null && seasons.isNotEmpty()) {
        LaunchedEffect(seasons.first().seriesId) {
            seriesMetadata = getSeriesMetadata(credentials, seasons.first().seriesId)
        }
    }

    val backdropUrl = seriesMetadata?.backdropPath?.firstOrNull() ?: cover

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, tokens.colors.background.copy(alpha = 0.8f), tokens.colors.background)
                    )
                )
        )

        val seasonsFocusRequester = remember { FocusRequester() }
        val episodesFocusRequester = remember { FocusRequester() }

        LaunchedEffect(isLoading, selectedSeason, episodesMap) {
            if (!isLoading && episodesMap[selectedSeason]?.isNotEmpty() == true) {
                delay(500)
                try { episodesFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                title = "",
                onBack = onBack,
                actions = {
                    AppIconButton(
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onClick = onToggleFavorite,
                        tint = if (isFavorite) tokens.colors.error else tokens.colors.textPrimary
                    )
                }
            )

            if (isLoading && seasons.isEmpty()) {
                LoadingState(message = stringResource(R.string.refreshing_label))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isExpanded) tokens.spacing.extraLarge * 2 else tokens.spacing.large)
                ) {
                    // Sidebar for Seasons
                    Column(
                        modifier = Modifier
                            .width(if (isExpanded) 320.dp else 220.dp)
                            .fillMaxHeight()
                            .padding(end = tokens.spacing.large)
                    ) {
                        Text(
                            text = stringResource(R.string.series_nav_title).uppercase(), 
                            style = tokens.typography.caption, 
                            color = tokens.colors.primary, 
                            fontWeight = FontWeight.Black, 
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = tokens.spacing.large, start = tokens.spacing.small)
                        )
                        val sortedSeasons = seasons.sortedWith(compareBy({ if (it.seasonNumber == 0) 1 else 0 }, { it.seasonNumber }))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.tiny), 
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(seasonsFocusRequester)
                                .focusProperties { right = episodesFocusRequester }
                        ) {
                            items(sortedSeasons) { season ->
                                SeasonListItem(
                                    name = if (season.seasonNumber == 0) stringResource(R.string.special_season_label).uppercase() else stringResource(R.string.season_prefix, season.seasonNumber).uppercase(), 
                                    isSelected = selectedSeason == season.seasonNumber, 
                                    onClick = { onSeasonSelect(season.seasonNumber) }
                                )
                            }
                        }
                    }

                    val currentEpisodes = episodesMap[selectedSeason] ?: emptyList()
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(episodesFocusRequester)
                            .focusProperties { left = seasonsFocusRequester }, 
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.large), 
                        contentPadding = PaddingValues(bottom = tokens.spacing.giant * 2)
                    ) {
                        item {
                            Column {
                                val titleStyle = if (isTv) tokens.typography.display else tokens.typography.headline
                                Text(
                                    text = seriesName.uppercase(), 
                                    style = titleStyle, 
                                    color = tokens.colors.textPrimary, 
                                    fontWeight = FontWeight.Black, 
                                    letterSpacing = 1.sp
                                )
                                
                                Row(
                                    modifier = Modifier.padding(vertical = tokens.spacing.large), 
                                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large), 
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RatingBadge(seriesMetadata?.rating ?: "0")
                                    seriesMetadata?.releaseDate?.let { MetadataItem(it) }
                                    val age = seriesMetadata?.age ?: seriesMetadata?.mpaaRating
                                    if (!age.isNullOrBlank()) { AgeBadge(age) }
                                    
                                    if (selectedSeason != null) {
                                        AppIconButton(
                                            icon = Icons.Rounded.Download,
                                            onClick = { onDownloadSeason(seriesId, selectedSeason) },
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }
                                
                                SeriesMetadataSection(seriesMetadata, onGenreClick, onDirectorClick)

                                Text(
                                    text = seriesMetadata?.plot ?: stringResource(R.string.no_description), 
                                    style = tokens.typography.body, 
                                    color = tokens.colors.textSecondary, 
                                    lineHeight = 28.sp, 
                                    modifier = Modifier.padding(vertical = tokens.spacing.large)
                                )
                                
                                val castText = seriesMetadata?.cast ?: ""
                                if (castText.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.cast_label).uppercase(), 
                                        style = tokens.typography.title, 
                                        fontWeight = FontWeight.Black, 
                                        color = tokens.colors.primary, 
                                        letterSpacing = 1.sp
                                    )
                                    val actors = remember(castText) { castText.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large), 
                                        contentPadding = PaddingValues(vertical = tokens.spacing.large)
                                    ) {
                                        items(actors) { actor -> 
                                            ActorAvatar(
                                                name = actor, 
                                                onClick = { onActorClick(actor) }, 
                                                isExpanded = isExpanded
                                            ) 
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (isLoading && seasons.isNotEmpty()) {
                            item { 
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { 
                                    CircularProgressIndicator(color = tokens.colors.primary) 
                                } 
                            }
                        } else {
                            items(currentEpisodes) { episode ->
                                val progress = watchProgress[episode.streamId]
                                val isWatched = progress != null && progress > 0.9f
                                EpisodeCard(
                                    title = episode.title, 
                                    episodeNum = episode.episodeNum, 
                                    image = episode.image ?: cover, 
                                    plot = episode.plot, 
                                    progress = progress, 
                                    isWatched = isWatched,
                                    isExpanded = isExpanded, 
                                    onClick = { onPlayEpisode(episode.streamId, episode.title, episode.containerExtension ?: "ts") }, 
                                    onDownload = { onDownloadEpisode(IptvItem(id = episode.streamId.toString(), name = episode.title, icon = episode.image ?: cover, type = ContentType.SERIES, containerExtension = episode.containerExtension ?: "ts"), episode.seriesId, selectedSeason) }
                                )
                            }
                        }

                        if (similarContent.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(tokens.spacing.giant))
                                Text(
                                    text = stringResource(R.string.similar_series).uppercase(),
                                    style = tokens.typography.title,
                                    fontWeight = FontWeight.Black,
                                    color = tokens.colors.primary,
                                    letterSpacing = 1.sp
                                )
                                ContentRow(
                                    title = "",
                                    items = similarContent,
                                    watchProgress = watchProgress,
                                    onItemClick = { item -> /* Navigate to series detail logic is in parent */ }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesMetadataSection(
    meta: XtreamSeries?,
    onGenreClick: (String) -> Unit,
    onDirectorClick: (String) -> Unit
) {
    val tokens = AppDesignSystem
    if (meta == null) return
    Column(modifier = Modifier.padding(vertical = tokens.spacing.medium)) {
        if (!meta.director.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.director_label).uppercase(), 
                style = tokens.typography.caption, 
                color = tokens.colors.primary, 
                fontWeight = FontWeight.Black
            )
            Text(
                text = meta.director!!, 
                color = tokens.colors.textPrimary, 
                style = tokens.typography.title, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(tokens.shapes.small)
                    .clickable { onDirectorClick(meta.director!!) }
                    .padding(vertical = tokens.spacing.tiny)
            )
        }
        if (!meta.genre.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(tokens.spacing.large))
            Text(
                text = stringResource(R.string.genre_label).uppercase(), 
                style = tokens.typography.caption, 
                color = tokens.colors.primary, 
                fontWeight = FontWeight.Black
            )
            val genres = remember(meta.genre) { meta.genre!!.split(",").map { it.trim() } }
            Row(
                modifier = Modifier.padding(top = tokens.spacing.medium), 
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.small)
            ) {
                genres.forEach { genre ->
                    GenreBadge(genre, onClick = { onGenreClick(genre) })
                }
            }
        }
    }
}
