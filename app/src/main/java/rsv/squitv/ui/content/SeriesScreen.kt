package rsv.squitv.ui.content

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import rsv.squitv.ui.content.components.rememberContentGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.unit.dp
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.components.navigation.AppSidebar
import rsv.squitv.core.ui.components.states.EmptyState
import rsv.squitv.core.ui.components.states.LoadingState
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.CategoryViewModel
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.content.components.SortMenuDialog
import rsv.squitv.ui.content.components.ContentGrid
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.ui.viewmodel.settings.SettingsViewModel
import rsv.squitv.util.WindowSize
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun SeriesScreen(
    title: String,
    viewModel: MainViewModel,
    categoryViewModel: CategoryViewModel,
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onSeriesClick: (Int, String, String?) -> Unit,
    onCategoryClick: (String, String?) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val contentRows by categoryViewModel.currentContentRows.collectAsStateWithLifecycle()
    val categories by categoryViewModel.navigationCategories.collectAsStateWithLifecycle()
    val isLoading by categoryViewModel.isLoading.collectAsStateWithLifecycle()
    val sortOrder by categoryViewModel.sortOrder.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    val watchProgress by libraryViewModel.watchProgress.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val prepState by viewModel.prepState.collectAsStateWithLifecycle()
    
    var showSortMenu by remember { mutableStateOf(false) }
    var showPrepDialog by remember { mutableStateOf(false) }
    
    val isSettingsReady = settings.credentials != null
    val accountKey = settings.credentials?.let { rsv.squitv.data.model.SettingsBackupCodec.accountKey(it) } ?: ""
    val isPromptDismissed = remember(accountKey, settings.activeProfileId, settings.seriesPromptDismissedMap, isSettingsReady) {
        if (!isSettingsReady || accountKey.isBlank()) true
        else settings.isSeriesPromptDismissed(accountKey, settings.activeProfileId)
    }
    val currentMode = settings.getSeriesCategoryMode(accountKey, settings.activeProfileId)
    var showChoicePrompt by remember(accountKey, settings.activeProfileId, isSettingsReady, isPromptDismissed) {
        mutableStateOf(isSettingsReady && accountKey.isNotBlank() && !isPromptDismissed)
    }

    LaunchedEffect(isPromptDismissed, isSettingsReady) {
        if (isSettingsReady && isPromptDismissed) showChoicePrompt = false
    }

    LaunchedEffect(Unit) {
        viewModel.setPrepActiveType("SERIES")
    }

    val windowInfo = rememberWindowInfo()
    val isCompact = windowInfo.widthSize == WindowSize.COMPACT

    val sidebarFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }

    val selectedCategoryId by categoryViewModel.activeCategoryId.collectAsStateWithLifecycle()
    val gridState = rememberContentGridState("${settings.credentials?.baseUrl}#${settings.credentials?.username}/series/$selectedCategoryId/$sortOrder")

    val handleCategorySelect: (String) -> Unit = { catId ->
        viewModel.saveLastCategory("series", catId)
        categoryViewModel.loadContent("series", catId)
    }

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            val activeCat = categories.find { cat -> 
                cat.id == selectedCategoryId || contentRows.any { it.catId == cat.id }
            }
            AppHeader(
                title = title,
                subtitle = activeCat?.name ?: stringResource(R.string.all_categories_label),
                actions = {
                    rsv.squitv.ui.content.components.CatalogPreparationIndicator(
                        state = prepState,
                        onClick = { showPrepDialog = true }
                    )
                    AppIconButton(Icons.AutoMirrored.Rounded.Sort, { showSortMenu = true })
                    AppIconButton(Icons.Rounded.Search, onNavigateToSearch)
                }
            )

            if (isCompact) {
                rsv.squitv.ui.content.components.ContentCategoryStrip(
                    categories, selectedCategoryId, handleCategorySelect
                )
            }
            Row(modifier = Modifier.fillMaxSize()) {
                if (!isCompact) {
                    AppSidebar(
                        items = categories,
                        selectedItemPredicate = { cat -> 
                            !cat.isHeader && (cat.id == selectedCategoryId || (selectedCategoryId == null && contentRows.any { it.catId == cat.id }))
                        },
                        itemLabel = { it.name },
                        itemCount = { it.count },
                        itemKey = { it.id },
                        isHeaderPredicate = { it.isHeader },
                        onItemClick = { cat ->
                            cat.id.let { catId ->
                                handleCategorySelect(catId)
                                try { gridFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                        },
                        onItemFocus = { _ ->
                            // Do not change open category on visual focus change
                        },
                        focusRequester = sidebarFocusRequester,
                        nextFocusRequester = gridFocusRequester
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (isLoading && contentRows.isEmpty()) {
                        LoadingState(message = stringResource(R.string.msg_connecting))
                    } else if (contentRows.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.no_results_found),
                            description = if (selectedCategoryId == "FAVORITES") stringResource(R.string.empty_favorites_series) else "Nenhuma série encontrada."
                        )
                    } else {
                        ContentGrid(
                            items = contentRows.flatMap { it.items },
                            gridFocusRequester = gridFocusRequester,
                            sidebarFocusRequester = sidebarFocusRequester,
                            state = gridState,
                            onToggleFavorite = libraryViewModel::toggleFavorite,
                            favorites = favorites,
                            watchProgress = watchProgress,
                            onItemClick = { item -> onSeriesClick(item.id.toInt(), item.name, item.icon) }
                        )
                    }
                }
            }
        }
    }

    if (showChoicePrompt) {
        rsv.squitv.ui.content.components.CategoryChoiceDialog(
            title = stringResource(R.string.first_use_series_prompt_title),
            currentMode = currentMode,
            onConfirm = { mode ->
                viewModel.updateSeriesCategoryMode(mode)
                showChoicePrompt = false
            },
            onDismiss = {
                viewModel.dismissSeriesPrompt()
                showChoicePrompt = false
            }
        )
    }

    if (showPrepDialog) {
        rsv.squitv.ui.content.components.CatalogPreparationDialog(
            state = prepState,
            onPause = { viewModel.pausePrep() },
            onResume = { viewModel.resumePrep() },
            onRetry = { viewModel.retryPrepFailed() },
            onSpeedChange = { viewModel.setPreparationSpeed(it) },
            onDismiss = { showPrepDialog = false }
        )
    }

    if (showSortMenu) {
        SortMenuDialog(
            currentSort = sortOrder,
            onSortChanged = { categoryViewModel.onSortOrderChanged(it); showSortMenu = false },
            onDismiss = { showSortMenu = false },
            showRecent = true
        )
    }
}
