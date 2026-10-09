package rsv.squitv.ui.content

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
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
import rsv.squitv.ui.content.components.PinUnlockDialog
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.ui.viewmodel.settings.SettingsViewModel
import rsv.squitv.util.WindowSize
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun MoviesScreen(
    title: String,
    viewModel: MainViewModel,
    categoryViewModel: CategoryViewModel,
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onVodClick: (Int, String, String?) -> Unit,
    onCategoryClick: (String, String?) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val contentRows by categoryViewModel.currentContentRows.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isLoading by categoryViewModel.isLoading.collectAsStateWithLifecycle()
    val sortOrder by categoryViewModel.sortOrder.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    val watchProgress by libraryViewModel.watchProgress.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedCategoryIds.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    
    var showSortMenu by remember { mutableStateOf(false) }
    var pendingCategoryToUnlock by remember { mutableStateOf<String?>(null) }
    
    val windowInfo = rememberWindowInfo()
    val isCompact = windowInfo.widthSize == WindowSize.COMPACT
    
    val sidebarFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }

    val ids = remember(settings.hideBlockedCategories, blockedIds) {
        if (settings.hideBlockedCategories) blockedIds else emptySet()
    }

    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(contentRows) {
        val loadedCatId = contentRows.firstOrNull()?.catId
        if (loadedCatId != null && selectedCategoryId != loadedCatId) {
            selectedCategoryId = loadedCatId
        }
    }

    var isInitialLoad by remember { mutableStateOf(true) }

    LaunchedEffect(isLoading) {
        if (!isLoading && isInitialLoad) {
            isInitialLoad = false
            delay(200)
            try { sidebarFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val handleCategorySelect: (String) -> Unit = { catId ->
        if (blockedIds.contains(catId) && !settings.hideBlockedCategories) {
            pendingCategoryToUnlock = catId
        } else {
            selectedCategoryId = catId
            viewModel.saveLastCategory("movie", catId)
            categoryViewModel.loadContent("movie", catId, ids)
        }
    }

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            val activeCat = categories.find { cat -> 
                cat.categoryId == selectedCategoryId || contentRows.any { it.catId == cat.categoryId } 
            }
            AppHeader(
                title = title,
                subtitle = activeCat?.categoryName ?: stringResource(R.string.all_categories_label),
                onBack = onBack,
                actions = {
                    AppIconButton(Icons.AutoMirrored.Rounded.Sort, { showSortMenu = true })
                    AppIconButton(Icons.Rounded.Search, onNavigateToSearch)
                }
            )

            Row(modifier = Modifier.fillMaxSize()) {
                if (!isCompact) {
                    AppSidebar(
                        items = categories,
                        selectedItemPredicate = { cat -> 
                            cat.categoryId == selectedCategoryId || (selectedCategoryId == null && contentRows.any { it.catId == cat.categoryId })
                        },
                        itemLabel = { it.categoryName ?: "" },
                        onItemClick = { cat ->
                            cat.categoryId?.let { catId ->
                                handleCategorySelect(catId)
                                if (!blockedIds.contains(catId) || settings.hideBlockedCategories) {
                                    try { gridFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            }
                        },
                        onItemFocus = { cat ->
                            cat.categoryId?.let { catId ->
                                if (catId != selectedCategoryId) {
                                    handleCategorySelect(catId)
                                }
                            }
                        },
                        focusRequester = sidebarFocusRequester,
                        nextFocusRequester = gridFocusRequester
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (isLoading) {
                        LoadingState(message = stringResource(R.string.msg_connecting))
                    } else if (contentRows.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.no_results_found),
                            description = if (selectedCategoryId == "FAVORITES") stringResource(R.string.empty_favorites_movies) else "Nenhum filme encontrado."
                        )
                    } else {
                        ContentGrid(
                            items = contentRows.flatMap { it.items },
                            gridFocusRequester = gridFocusRequester,
                            sidebarFocusRequester = sidebarFocusRequester,
                            favorites = favorites,
                            watchProgress = watchProgress,
                            onItemClick = { item -> onVodClick(item.id.toInt(), item.name, item.icon) },
                            onItemLongClick = { item, isFav -> libraryViewModel.toggleFavorite(item, !isFav) }
                        )
                    }
                }
            }
        }
    }

    if (showSortMenu) {
        SortMenuDialog(
            currentSort = sortOrder,
            onSortChanged = { categoryViewModel.onSortOrderChanged(it); showSortMenu = false },
            onDismiss = { showSortMenu = false },
            showRating = true
        )
    }

    if (pendingCategoryToUnlock != null) {
        PinUnlockDialog(
            onDismiss = { pendingCategoryToUnlock = null },
            onConfirm = { pin ->
                if (pin == settings.appPin) {
                    val unlockedCat = pendingCategoryToUnlock!!
                    viewModel.unlockCategory(unlockedCat)
                    selectedCategoryId = unlockedCat
                    viewModel.saveLastCategory("movie", unlockedCat)
                    categoryViewModel.loadContent("movie", unlockedCat, ids)
                    pendingCategoryToUnlock = null
                }
            }
        )
    }
}
