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
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.components.navigation.AppSidebar
import rsv.squitv.core.ui.components.states.EmptyState
import rsv.squitv.core.ui.components.states.LoadingState
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.CategoryViewModel
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.content.components.ContentGrid
import rsv.squitv.ui.content.components.SortMenuDialog
import rsv.squitv.ui.viewmodel.settings.SettingsViewModel
import rsv.squitv.util.WindowSize
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import timber.log.Timber

@UnstableApi
@Composable
fun LiveChannelsScreen(
    viewModel: MainViewModel,
    categoryViewModel: CategoryViewModel,
    settingsViewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onPlay: (Int, String, String, String?, String?, String?, String?) -> Unit,
    onCategoryClick: (String, String?) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val contentRows by categoryViewModel.currentContentRows.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    val categories by categoryViewModel.navigationCategories.collectAsStateWithLifecycle()
    val isLoading by categoryViewModel.isLoading.collectAsStateWithLifecycle()
    val sortOrder by categoryViewModel.sortOrder.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    
    var showSortMenu by remember { mutableStateOf(false) }
    
    val windowInfo = rememberWindowInfo()
    val isCompact = windowInfo.widthSize == WindowSize.COMPACT
    
    val sidebarFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }


    val selectedCategoryId by categoryViewModel.activeCategoryId.collectAsStateWithLifecycle()
    val gridState = rememberContentGridState("${settings.credentials?.baseUrl}#${settings.credentials?.username}/live/$selectedCategoryId")


    var isInitialLoad by rememberSaveable { mutableStateOf(true) }


    LaunchedEffect(isLoading) {
        if (!isLoading && isInitialLoad) {
            isInitialLoad = false
            delay(200)
            try {
                val items = contentRows.flatMap { it.items }
                val hasSavedChannel = settings.lastLiveChannelId != null && items.any { it.containsStream(settings.lastLiveChannelId) }
                if (hasSavedChannel) {
                    gridFocusRequester.requestFocus()
                } else {
                    sidebarFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
        }
    }

    val handleCategorySelect: (String) -> Unit = { catId ->
        viewModel.saveLastCategory("live", catId)
        categoryViewModel.loadContent("live", catId)
    }

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            val activeCat = categories.find { cat -> 
                cat.id == selectedCategoryId || contentRows.any { it.catId == cat.id }
            }
            AppHeader(
                title = stringResource(R.string.live_tv_nav_title),
                subtitle = activeCat?.name ?: stringResource(R.string.all_categories_label),
                actions = {
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
                            cat.id == selectedCategoryId || (selectedCategoryId == null && contentRows.any { it.catId == cat.id })
                        },
                        itemLabel = { it.name },
                        itemCount = { it.count },
                        itemKey = { it.id },
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
                            description = "Tente outra categoria."
                        )
                    } else {
                        val allItems = remember(contentRows) { contentRows.flatMap { it.items } }
                        val activeCatId = selectedCategoryId ?: activeCat?.id ?: settings.lastLiveCategory ?: categories.firstOrNull()?.id ?: "RECENTS"
                        val targetChannelId = remember(allItems, activeCatId, settings.lastLiveChannelId, settings.lastLiveCategory, settings.lastLiveCategoryChannels) {
                            val globalId = settings.lastLiveChannelId
                            val globalCat = settings.lastLiveCategory
                            if (globalId != null && globalCat == activeCatId && allItems.any { it.containsStream(globalId) }) {
                                Timber.i("GLOBAL_CHANNEL_PRIORITY_APPLIED: categoryId=$activeCatId, channelId=$globalId")
                                globalId
                            } else {
                                val catSpecificId = settings.lastLiveCategoryChannels[activeCatId]
                                if (catSpecificId != null && allItems.any { it.containsStream(catSpecificId) }) {
                                    Timber.i("CATEGORY_CHANNEL_MEMORY_HIT: categoryId=$activeCatId, channelId=$catSpecificId")
                                    catSpecificId
                                } else {
                                    val fallback = allItems.firstOrNull()?.id
                                    if (fallback != null) {
                                        Timber.i("CHANNEL_FALLBACK_APPLIED: categoryId=$activeCatId, channelId=$fallback")
                                    }
                                    fallback
                                }
                            }
                        }

                        ContentGrid(
                            items = allItems,
                            gridFocusRequester = gridFocusRequester,
                            sidebarFocusRequester = sidebarFocusRequester,
                            state = gridState,
                            onToggleFavorite = libraryViewModel::toggleFavorite,
                            favorites = favorites,
                            targetItemId = targetChannelId,
                            onItemClick = { item ->
                                Timber.i("PLAYBACK_REQUESTED_BY_USER: channelId=${item.id}, name=${item.name}")
                                viewModel.saveLastChannel("live", activeCatId, item.id, item.name)
                                onPlay(item.id.toInt(), item.name, item.type.toString(), item.epgId, categoryViewModel.capturePlaybackSource(), item.icon, activeCatId)
                            }
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
            onDismiss = { showSortMenu = false }
        )
    }

}
