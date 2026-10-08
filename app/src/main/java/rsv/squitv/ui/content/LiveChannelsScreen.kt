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
import rsv.squitv.ui.content.components.ContentGrid
import rsv.squitv.ui.content.components.PinUnlockDialog
import rsv.squitv.ui.content.components.SortMenuDialog
import rsv.squitv.ui.viewmodel.settings.SettingsViewModel
import rsv.squitv.util.WindowSize
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.delay
import timber.log.Timber

@UnstableApi
@Composable
fun LiveChannelsScreen(
    viewModel: MainViewModel,
    categoryViewModel: CategoryViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPlay: (Int, String, String, String?, Map<String, Int>, String?) -> Unit,
    onCategoryClick: (String, String?) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val contentRows by categoryViewModel.currentContentRows.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isLoading by categoryViewModel.isLoading.collectAsStateWithLifecycle()
    val sortOrder by categoryViewModel.sortOrder.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedCategoryIds.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    
    var showSortMenu by remember { mutableStateOf(false) }
    var pendingCategoryToUnlock by remember { mutableStateOf<String?>(null) }
    
    val windowInfo = rememberWindowInfo()
    val isCompact = windowInfo.widthSize == WindowSize.COMPACT
    
    val sidebarFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    var wasPinDialogActive by remember { mutableStateOf(false) }

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

    LaunchedEffect(categories, settings.lastLiveCategory) {
        if (categories.isNotEmpty()) {
            val savedCat = settings.lastLiveCategory
            val isSavedValid = savedCat != null && (savedCat == "RECENTS" || savedCat == "FAVORITES" || categories.any { it.categoryId == savedCat })
            if (!isSavedValid && savedCat != null) {
                val fallbackCatId = categories.firstOrNull()?.categoryId ?: "RECENTS"
                if (selectedCategoryId != fallbackCatId) {
                    selectedCategoryId = fallbackCatId
                    viewModel.saveLastCategory("live", fallbackCatId)
                    categoryViewModel.loadContent("live", fallbackCatId, ids)
                }
            }
        }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading && isInitialLoad) {
            isInitialLoad = false
            delay(200)
            try {
                val items = contentRows.flatMap { it.items }
                val hasSavedChannel = settings.lastLiveChannelId != null && items.any { it.id == settings.lastLiveChannelId }
                if (hasSavedChannel) {
                    gridFocusRequester.requestFocus()
                } else {
                    sidebarFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(pendingCategoryToUnlock) {
        if (pendingCategoryToUnlock != null) {
            wasPinDialogActive = true
        } else if (wasPinDialogActive) {
            wasPinDialogActive = false
            delay(150)
            try {
                sidebarFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val handleCategorySelect: (String) -> Unit = { catId ->
        if (blockedIds.contains(catId) && !settings.hideBlockedCategories) {
            pendingCategoryToUnlock = catId
        } else {
            selectedCategoryId = catId
            viewModel.saveLastCategory("live", catId)
            categoryViewModel.loadContent("live", catId, ids)
        }
    }

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            val activeCat = categories.find { cat -> 
                cat.categoryId == selectedCategoryId || contentRows.any { it.catId == cat.categoryId } 
            }
            AppHeader(
                title = stringResource(R.string.live_tv_nav_title),
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
                        onItemFocus = { _ ->
                            // Do not change open category on visual focus change
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
                            description = "Tente outra categoria."
                        )
                    } else {
                        val allItems = remember(contentRows) { contentRows.flatMap { it.items } }
                        val activeCatId = selectedCategoryId ?: activeCat?.categoryId ?: settings.lastLiveCategory ?: categories.firstOrNull()?.categoryId ?: "RECENTS"
                        val targetChannelId = remember(allItems, activeCatId, settings.lastLiveChannelId, settings.lastLiveCategory, settings.lastLiveCategoryChannels) {
                            val globalId = settings.lastLiveChannelId
                            val globalCat = settings.lastLiveCategory
                            if (globalId != null && globalCat == activeCatId && allItems.any { it.id == globalId }) {
                                Timber.i("GLOBAL_CHANNEL_PRIORITY_APPLIED: categoryId=$activeCatId, channelId=$globalId")
                                globalId
                            } else {
                                val catSpecificId = settings.lastLiveCategoryChannels[activeCatId]
                                if (catSpecificId != null && allItems.any { it.id == catSpecificId }) {
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
                            targetItemId = targetChannelId,
                            onItemClick = { item ->
                                Timber.i("PLAYBACK_REQUESTED_BY_USER: channelId=${item.id}, name=${item.name}")
                                viewModel.saveLastChannel("live", activeCatId, item.id, item.name)
                                onPlay(item.id.toInt(), item.name, item.type.toString(), item.epgId, item.qualities, item.icon)
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

    if (pendingCategoryToUnlock != null) {
        PinUnlockDialog(
            onDismiss = { pendingCategoryToUnlock = null },
            onConfirm = { pin ->
                if (pin == settings.appPin) {
                    val unlockedCat = pendingCategoryToUnlock!!
                    viewModel.unlockCategory(unlockedCat)
                    selectedCategoryId = unlockedCat
                    viewModel.saveLastCategory("live", unlockedCat)
                    categoryViewModel.loadContent("live", unlockedCat, ids)
                    pendingCategoryToUnlock = null
                }
            }
        )
    }
}
