package rsv.squitv.ui.content.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import rsv.squitv.core.ui.components.cards.ChannelCard
import rsv.squitv.core.ui.components.cards.PosterCard
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.util.rememberWindowInfo

private val IptvItem.gridKey: String get() = "${type.name}:$id"

@UnstableApi
@Composable
fun ContentGrid(
    items: List<IptvItem>,
    gridFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    favorites: List<IptvItem> = emptyList(),
    watchProgress: Map<Int, Float> = emptyMap(),
    targetItemId: String? = null,
    state: ContentGridState = rememberContentGridState("default"),
    onToggleFavorite: ((IptvItem, Boolean) -> Unit)? = null,
    onItemClick: (IptvItem) -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    val useFocus = rememberWindowInfo().isTv || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val isLive = items.firstOrNull()?.type == ContentType.LIVE
    val columns = when {
        responsive.widthDp > 1600.dp -> if (isLive) 7 else 8
        responsive.widthDp > 1200.dp -> 6
        responsive.widthDp > 900.dp -> 5
        responsive.widthDp > 600.dp -> 4
        else -> 2
    }
    val favoriteKeys = remember(favorites) {
        favorites.mapTo(HashSet(favorites.size)) { it.id to it.type }
    }
    val savedIndex = remember(items, state.focusedItem) { items.indexOfFirst { it.gridKey == state.focusedItem } }
    val explicitIndex = remember(items, targetItemId) { items.indexOfFirst { it.id == targetItemId } }
    val focusIndex = when {
        savedIndex >= 0 -> savedIndex
        state.hasInteracted -> state.focusedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        explicitIndex >= 0 -> explicitIndex
        else -> state.scroll.firstVisibleItemIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    }

    LaunchedEffect(state) {
        snapshotFlow { state.scroll.isScrollInProgress }.collect { scrolling ->
            if (scrolling) state.hasInteracted = true
        }
    }

    // Returning from details restores the saved offset. Only a first live-channel selection scrolls explicitly.
    var entered by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state, items.isNotEmpty()) {
        if (items.isEmpty() || entered) return@LaunchedEffect
        entered = true
        if (!state.hasInteracted && explicitIndex >= 0) state.scroll.scrollToItem(explicitIndex)
        if (useFocus) {
            if (state.hasInteracted && state.scroll.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) {
                withFrameNanos { }
                if (state.scroll.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) {
                    state.scroll.scrollToItem(focusIndex)
                }
            }
            withFrameNanos { }
            runCatching { gridFocusRequester.requestFocus() }
        }
    }

    // Removing the focused favourite keeps navigation on the neighbouring card.
    LaunchedEffect(items) {
        if (items.isNotEmpty() && state.focusedItem.isNotEmpty() && savedIndex < 0) {
            state.focusedItem = items[focusIndex].gridKey
            if (useFocus) {
                if (state.scroll.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) {
                    state.scroll.scrollToItem(focusIndex)
                }
                withFrameNanos { }
                runCatching { gridFocusRequester.requestFocus() }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state.scroll,
        modifier = Modifier.fillMaxSize().focusProperties { left = sidebarFocusRequester },
        contentPadding = PaddingValues(
            horizontal = responsive.dp(tokens.spacing.extraLarge),
            vertical = responsive.dp(tokens.spacing.large)
        ),
        verticalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.large)),
        horizontalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.large))
    ) {
        itemsIndexed(items, key = { _, item -> item.gridKey }) { index, item ->
            val isFavorite = favoriteKeys.contains(item.id to item.type)
            val itemModifier = (if (index == focusIndex) Modifier.focusRequester(gridFocusRequester) else Modifier)
                .onFocusChanged {
                    if (it.hasFocus) {
                        state.focusedItem = item.gridKey
                        state.focusedIndex = index
                        state.hasInteracted = true
                    }
                }
            val onOpen = {
                state.focusedItem = item.gridKey
                state.focusedIndex = index
                state.hasInteracted = true
                onItemClick(item)
            }
            val onFavorite = onToggleFavorite?.let { toggle -> { toggle(item, !isFavorite) } }
            if (item.type == ContentType.LIVE) {
                ChannelCard(item.copy(isFavorite = isFavorite), modifier = itemModifier,
                    onToggleFavorite = onFavorite, onClick = onOpen)
            } else {
                PosterCard(item.copy(isFavorite = isFavorite), modifier = itemModifier,
                    progress = watchProgress[item.id.toIntOrNull() ?: -1],
                    onToggleFavorite = onFavorite, onClick = onOpen)
            }
        }
    }
}
