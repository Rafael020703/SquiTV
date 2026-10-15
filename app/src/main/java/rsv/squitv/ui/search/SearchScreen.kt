package rsv.squitv.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.cards.ChannelCard
import rsv.squitv.core.ui.components.cards.PosterCard
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.components.states.EmptyState
import rsv.squitv.core.ui.components.states.LoadingState
import rsv.squitv.core.ui.components.inputs.SearchField
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.*
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.SearchViewModel
import rsv.squitv.ui.viewmodel.SearchResult
import rsv.squitv.util.WindowInfo
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.delay
import java.util.*

import androidx.hilt.navigation.compose.hiltViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel

@UnstableApi
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onItemClick: (id: Int, name: String, type: String, container: String?, seriesId: Int?, seasonNumber: Int?, icon: String?) -> Unit
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val windowInfo = rememberWindowInfo()

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    viewModel.onQueryChanged(spokenText)
                }
            }
        }
    )

    val launchVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o nome do canal, filme ou série")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) { }
    }

    PortalBackground {
        SearchScreenContent(
            query = query,
            selectedType = selectedType,
            results = results,
            favorites = favorites,
            isLoading = isLoading,
            onQueryChanged = { viewModel.onQueryChanged(it) },
            onTypeChanged = { viewModel.onTypeChanged(it) },
            onNavigateBack = onNavigateBack,
            onItemClick = onItemClick,
            onVoiceSearch = launchVoiceSearch,
            windowInfo = windowInfo
        )
    }
}

@UnstableApi
@Composable
fun SearchScreenContent(
    query: String,
    selectedType: String,
    results: List<SearchResult>,
    favorites: List<IptvItem> = emptyList(),
    isLoading: Boolean,
    onQueryChanged: (String) -> Unit,
    onTypeChanged: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onItemClick: (id: Int, name: String, type: String, container: String?, seriesId: Int?, seasonNumber: Int?, icon: String?) -> Unit,
    onVoiceSearch: () -> Unit,
    windowInfo: WindowInfo
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    val searchFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(500)
        try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(isLoading, results) {
        if (!isLoading && results.isNotEmpty()) {
            delay(500)
            try { gridFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = stringResource(R.string.search_label),
            onBack = onNavigateBack,
            actions = {
                SearchField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier
                        .widthIn(max = responsive.dp(500.dp))
                        .fillMaxWidth(if (windowInfo.isExpanded) 0.5f else 0.7f)
                        .focusRequester(searchFocusRequester),
                    placeholder = stringResource(R.string.search_placeholder),
                    leadingIcon = {
                        AppIconButton(
                            icon = Icons.Rounded.Mic,
                            onClick = onVoiceSearch,
                            tint = tokens.colors.primary
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            AppIconButton(
                                icon = Icons.Rounded.Close,
                                onClick = { onQueryChanged("") },
                                tint = tokens.colors.textSecondary.copy(alpha = 0.5f)
                            )
                        }
                    }
                )
            }
        )

        // Filters Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = responsive.dp(tokens.spacing.extraLarge), vertical = responsive.dp(tokens.spacing.medium)),
            horizontalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.small)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filters = listOf(
                "ALL" to stringResource(R.string.filter_all).uppercase(),
                "LIVE" to stringResource(R.string.filter_live).uppercase(),
                "VOD" to stringResource(R.string.filter_movies).uppercase(),
                "SERIES" to stringResource(R.string.filter_series).uppercase()
            )
            filters.forEach { (type, label) ->
                SearchFilterChip(
                    selected = selectedType == type,
                    label = label,
                    onClick = { onTypeChanged(type) }
                )
            }
        }

        // Results Grid
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                LoadingState()
            } else if (results.isEmpty() && query.isNotEmpty()) {
                EmptyState(
                    title = stringResource(R.string.no_results_found),
                    description = "Tente buscar por outro termo ou mude os filtros."
                )
            } else if (query.isEmpty()) {
                 EmptyState(
                     title = stringResource(R.string.search_empty_title),
                     description = stringResource(R.string.search_empty_subtitle),
                     icon = Icons.Rounded.Search
                 )
            } else {
                val isLiveOnly = selectedType == "LIVE"
                val columns = when {
                    responsive.widthDp > 1600.dp -> if (isLiveOnly) 5 else 8
                    responsive.widthDp > 1200.dp -> if (isLiveOnly) 4 else 6
                    responsive.widthDp > 900.dp -> if (isLiveOnly) 3 else 5
                    responsive.widthDp > 600.dp -> if (isLiveOnly) 2 else 4
                    else -> if (isLiveOnly) 1 else 2
                }

                val gridSpacing = responsive.dp(tokens.spacing.extraLarge)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
                    itemsIndexed(results, key = { _, it -> 
                        when(it) {
                            is SearchResult.Stream -> "s_${it.stream.id}"
                            is SearchResult.Episode -> "e_${it.episode.episodeId}"
                        }
                    }) { index, result ->
                        val item = when(result) {
                            is SearchResult.Stream -> IptvItem(
                                id = result.stream.id.toString(),
                                name = result.stream.name,
                                icon = result.stream.logo,
                                type = ContentType.fromString(result.stream.streamType),
                                isFavorite = result.stream.isFavorite,
                                rating = result.stream.rating
                            )
                            is SearchResult.Episode -> IptvItem(
                                id = result.episode.streamId.toString(),
                                name = result.episode.title,
                                icon = null,
                                type = ContentType.SERIES
                            )
                        }

                        val isFav = favorites.any { it.id == item.id && it.type == item.type }
                        val itemModifier = if (index == 0) Modifier.focusRequester(gridFocusRequester) else Modifier
                        
                        if (item.type == ContentType.LIVE) {
                            val sId = (result as? SearchResult.Stream)?.stream?.id ?: 0
                            ChannelCard(
                                item = item.copy(isFavorite = isFav),
                                modifier = itemModifier,
                                onClick = {
                                    onItemClick(sId, item.name, "live", null, null, null, item.icon)
                                }
                            )
                        } else {
                            PosterCard(
                                item = item.copy(isFavorite = isFav),
                                modifier = itemModifier,
                                onClick = {
                                    when (result) {
                                        is SearchResult.Stream -> {
                                            onItemClick(result.stream.id, result.stream.name, result.stream.streamType.lowercase(), result.stream.containerExtension, null, null, result.stream.logo)
                                        }
                                        is SearchResult.Episode -> {
                                            onItemClick(result.episode.streamId, result.episode.title, "series", result.episode.containerExtension, result.episode.seriesId, result.episode.seasonNumber, null)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    var isFocused by remember { mutableStateOf(false) }
    
    val textColor by animateColorAsState(
        if (isFocused) Color.Black 
        else if (selected) tokens.colors.primary 
        else tokens.colors.textSecondary
    )
    val bgColor by animateColorAsState(
        if (isFocused) tokens.colors.primary 
        else if (selected) tokens.colors.primary.copy(alpha = 0.1f) 
        else Color.Transparent
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .adaptiveFocus(
                shape = tokens.shapes.medium,
                onFocus = { isFocused = it }
            ),
        color = bgColor,
        shape = tokens.shapes.medium,
        border = if (!isFocused && selected) BorderStroke(responsive.dp(1.dp), tokens.colors.primary.copy(alpha = 0.3f)) else null
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = responsive.dp(tokens.spacing.large), vertical = responsive.dp(tokens.spacing.small)),
            style = tokens.typography.label.copy(fontSize = responsive.sp(tokens.typography.label.fontSize)),
            fontWeight = if (isFocused || selected) FontWeight.Black else FontWeight.Bold,
            color = textColor,
            letterSpacing = responsive.sp(1.sp)
        )
    }
}
