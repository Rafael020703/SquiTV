package rsv.squitv.ui.content

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import rsv.squitv.core.ui.components.states.EmptyState
import rsv.squitv.core.ui.components.states.LoadingState
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.CategoryViewModel
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.content.components.ContentGrid
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun LibraryContentScreen(
    title: String,
    viewModel: MainViewModel,
    categoryViewModel: CategoryViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    onPlay: (Int, String, String, String?, Map<String, Int>, String?) -> Unit,
    onSeriesClick: (Int, String, String?) -> Unit,
    onVodClick: (Int, String, String?) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val contentRows by categoryViewModel.currentContentRows.collectAsStateWithLifecycle()
    val isLoading by categoryViewModel.isLoading.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    val watchProgress by libraryViewModel.watchProgress.collectAsStateWithLifecycle()
    
    val windowInfo = rememberWindowInfo()
    val gridFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isLoading) {
        if (!isLoading && contentRows.isNotEmpty()) {
            delay(300)
            try { gridFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                title = title,
                onBack = onBack,
                actions = {
                    AppIconButton(Icons.Rounded.Search, onNavigateToSearch)
                }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    LoadingState(message = stringResource(R.string.msg_connecting))
                } else if (contentRows.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.no_results_found),
                        description = "Nenhum item encontrado nesta biblioteca."
                    )
                } else {
                    ContentGrid(
                        items = contentRows.flatMap { it.items },
                        gridFocusRequester = gridFocusRequester,
                        sidebarFocusRequester = remember { FocusRequester() }, // Library usually doesn't have a sidebar yet
                        favorites = favorites,
                        watchProgress = watchProgress,
                        onItemClick = { item ->
                            if (item.type.toString() == "live") {
                                onPlay(item.id.toInt(), item.name, item.type.toString(), item.epgId, item.qualities, item.icon)
                            } else if (item.type.toString() == "movie") {
                                onVodClick(item.id.toInt(), item.name, item.icon)
                            } else {
                                onSeriesClick(item.id.toInt(), item.name, item.icon)
                            }
                        }
                    )
                }
            }
        }
    }
}
