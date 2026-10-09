package rsv.squitv.ui.content

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import rsv.squitv.core.ui.components.content.MetadataItem
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.core.ui.theme.AppDimensions
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.ui.dashboard.*
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import rsv.squitv.util.rememberWindowInfo
import androidx.core.net.toUri
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import coil.request.ImageRequest
import coil.size.Precision
import rsv.squitv.data.model.VodDetails
import rsv.squitv.data.model.XtreamVodInfo
import rsv.squitv.util.WindowInfo
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun VodDetailScreen(
    vodId: Int,
    vodName: String,
    icon: String?,
    viewModel: MainViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onPlay: (Int, String, String?) -> Unit,
    onActorClick: (String) -> Unit,
    onGenreClick: (String) -> Unit = {},
    onDirectorClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var vodInfo by remember { mutableStateOf<XtreamVodInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val favorites by libraryViewModel.favorites.collectAsState()
    val credentials = viewModel.credentials

    val isFavorite = remember(favorites, vodId) { 
        favorites.any { it.id == vodId.toString() && (it.type == ContentType.MOVIE) } 
    }

    LaunchedEffect(vodId) {
        if (credentials != null) {
            isLoading = true
            try {
                vodInfo = libraryViewModel.getVodInfo(credentials, vodId)
            } catch (_: Exception) {}
            finally { isLoading = false }
        }
    }

    val similarContent by libraryViewModel.getSimilarContent(IptvItem(vodId.toString(), vodName, icon, ContentType.MOVIE)).collectAsStateWithLifecycle()
    val watchProgress by libraryViewModel.watchProgress.collectAsStateWithLifecycle()
    val currentProgress = watchProgress[vodId]
    
    val windowInfo = rememberWindowInfo()
    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            delay(500)
            try { playButtonFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    PortalBackground {
        VodDetailContent(
            vodId = vodId,
            vodName = vodName,
            icon = icon,
            vodInfo = vodInfo,
            isLoading = isLoading,
            isFavorite = isFavorite,
            currentProgress = currentProgress,
            onBack = onBack,
            onPlay = onPlay,
            onActorClick = onActorClick,
            onGenreClick = onGenreClick,
            onDirectorClick = onDirectorClick,
            onToggleFavorite = {
                val item = IptvItem(
                    id = vodId.toString(),
                    name = vodName,
                    icon = icon,
                    type = ContentType.MOVIE,
                    rating = vodInfo?.info?.rating,
                    releaseDate = vodInfo?.info?.releaseDate,
                    containerExtension = vodInfo?.movieData?.container_extension
                )
                libraryViewModel.toggleFavorite(item, !isFavorite)
            },
            onDownload = {
                val item = IptvItem(
                    id = vodId.toString(),
                    name = vodName,
                    icon = icon,
                    type = ContentType.MOVIE,
                    containerExtension = vodInfo?.movieData?.container_extension ?: "mp4"
                )
                libraryViewModel.startDownload(item)
            },
            similarContent = similarContent,
            watchProgress = watchProgress,
            windowInfo = windowInfo,
            playButtonFocusRequester = playButtonFocusRequester,
            onViewTrailer = { trailerId ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$trailerId".toUri())
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun VodDetailContent(
    vodId: Int,
    vodName: String,
    icon: String?,
    vodInfo: XtreamVodInfo?,
    isLoading: Boolean,
    isFavorite: Boolean,
    currentProgress: Float?,
    onBack: () -> Unit,
    onPlay: (Int, String, String?) -> Unit,
    onActorClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onDirectorClick: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    similarContent: List<IptvItem>,
    watchProgress: Map<Int, Float>,
    windowInfo: WindowInfo,
    playButtonFocusRequester: FocusRequester,
    onViewTrailer: (String) -> Unit
) {
    val tokens = AppDesignSystem
    val isExpanded = windowInfo.isExpanded
    val isTv = windowInfo.isTv
    val backdropUrl = vodInfo?.info?.backdropPath?.firstOrNull() ?: icon
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }.toInt()
        val screenHeightPx = with(density) { maxHeight.toPx() }.toInt()

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(backdropUrl)
                .size(screenWidthPx, screenHeightPx)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build(),
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

        if (isLoading) {
            LoadingState(message = stringResource(R.string.refreshing_label))
        } else {
            val movie = vodInfo?.info
            val scrollState = rememberScrollState()

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

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isExpanded) tokens.spacing.extraLarge * 2 else tokens.spacing.large)
                        .verticalScroll(scrollState)
                ) {
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .width(380.dp)
                                .padding(top = tokens.spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = tokens.shapes.extraLarge,
                                shadowElevation = 32.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(AppDimensions.posterAspectRatio)
                                    .border(
                                        tokens.dimensions.standardBorderWidth * 2,
                                        Color.White.copy(alpha = 0.1f),
                                        tokens.shapes.extraLarge
                                    )
                            ) {
                                AsyncImage(
                                    model = icon,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(tokens.spacing.giant * 2))
                    }

                    Column(modifier = Modifier.weight(1f).padding(bottom = tokens.spacing.giant * 2)) {
                        val titleStyle = if (isTv) tokens.typography.display else tokens.typography.headline
                        
                        Text(
                            text = vodName.uppercase(), 
                            style = titleStyle, 
                            fontWeight = FontWeight.Black, 
                            color = tokens.colors.textPrimary,
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.padding(vertical = tokens.spacing.large), 
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RatingBadge(movie?.rating ?: "0")
                            movie?.releaseDate?.let { MetadataItem(it) }
                            val age = movie?.age ?: movie?.mpaaRating
                            if (!age.isNullOrBlank()) { AgeBadge(age) }
                            movie?.duration?.let { MetadataItem(it) }
                        }

                        // ACTIONS
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.extraLarge),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large)
                        ) {
                            val isResuming = currentProgress != null && currentProgress > 0.05f
                            val containerExt = vodInfo?.movieData?.container_extension ?: "mp4"
                            AppButton(
                                text = if (isResuming) stringResource(R.string.row_continue_watching) else stringResource(R.string.watch_now_button),
                                icon = if (isResuming) Icons.Rounded.History else Icons.Rounded.PlayArrow,
                                onClick = { onPlay(vodId, vodName, containerExt) },
                                modifier = Modifier
                                    .height(if (isTv) 72.dp else 56.dp)
                                    .weight(1f)
                                    .focusRequester(playButtonFocusRequester)
                            )
                            
                            if (!movie?.youtubeTrailer.isNullOrBlank()) {
                                AppSecondaryButton(
                                    text = "TRAILER",
                                    icon = Icons.Rounded.PlayCircle,
                                    onClick = { onViewTrailer(movie.youtubeTrailer!!) },
                                    modifier = Modifier.height(if (isTv) 72.dp else 56.dp)
                                )
                            }

                            AppIconButton(
                                icon = Icons.Rounded.Download,
                                onClick = onDownload,
                                modifier = Modifier.size(if (isTv) 72.dp else 56.dp)
                            )
                        }

                        if (currentProgress != null) {
                            LinearProgressIndicator(
                                progress = { currentProgress },
                                modifier = Modifier.fillMaxWidth().height(tokens.spacing.micro).clip(CircleShape),
                                color = tokens.colors.primary,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.extraLarge))
                        }

                        Text(
                            text = movie?.plot ?: stringResource(R.string.no_description), 
                            style = tokens.typography.body, 
                            color = tokens.colors.textSecondary, 
                            lineHeight = 28.sp, 
                            modifier = Modifier.padding(vertical = tokens.spacing.medium)
                        )

                        if (!movie?.cast.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(tokens.spacing.extraLarge))
                            Text(
                                text = stringResource(R.string.cast_label).uppercase(),
                                style = tokens.typography.title,
                                fontWeight = FontWeight.Black,
                                color = tokens.colors.primary,
                                letterSpacing = 1.sp
                            )
                            val actors = remember(movie.cast) { movie.cast.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
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
                        
                        MetadataDetailsSection(movie, onGenreClick, onDirectorClick)

                        if (similarContent.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(tokens.spacing.giant))
                            Text(
                                text = stringResource(R.string.similar_content).uppercase(),
                                style = tokens.typography.title,
                                fontWeight = FontWeight.Black,
                                color = tokens.colors.primary,
                                letterSpacing = 1.sp
                            )
                            ContentRow(
                                title = "",
                                items = similarContent,
                                watchProgress = watchProgress,
                                onItemClick = { item -> onPlay(item.id.toInt(), item.name, null) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataDetailsSection(
    movie: VodDetails?,
    onGenreClick: (String) -> Unit,
    onDirectorClick: (String) -> Unit
) {
    val tokens = AppDesignSystem
    Column(modifier = Modifier.padding(vertical = tokens.spacing.medium)) {
        if (!movie?.director.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.director_label).uppercase(),
                style = tokens.typography.caption,
                color = tokens.colors.primary,
                fontWeight = FontWeight.Black
            )
            Text(
                text = movie.director!!, 
                color = tokens.colors.textPrimary, 
                style = tokens.typography.title, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(tokens.shapes.small)
                    .clickable { onDirectorClick(movie.director!!) }
                    .padding(vertical = tokens.spacing.tiny)
            )
        }
        if (!movie?.genre.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(tokens.spacing.large))
            Text(
                text = stringResource(R.string.genre_label).uppercase(),
                style = tokens.typography.caption,
                color = tokens.colors.primary,
                fontWeight = FontWeight.Black
            )
            val genres = remember(movie.genre) { movie.genre!!.split(",").map { it.trim() } }
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
