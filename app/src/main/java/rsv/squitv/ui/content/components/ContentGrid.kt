package rsv.squitv.ui.content.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import rsv.squitv.core.ui.components.cards.ChannelCard
import rsv.squitv.core.ui.components.cards.PosterCard
import rsv.squitv.core.ui.theme.Spacing
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.util.WindowInfo

@UnstableApi
@Composable
fun ContentGrid(
    items: List<IptvItem>,
    gridFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    favorites: List<IptvItem> = emptyList(),
    watchProgress: Map<Int, Float> = emptyMap(),
    targetItemId: String? = null,
    onItemClick: (IptvItem) -> Unit,
    onItemLongClick: (IptvItem, Boolean) -> Unit = { _, _ -> }
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    val isLive = items.firstOrNull()?.type == ContentType.LIVE
    
    val columns = when {
        responsive.widthDp > 1600.dp -> if (isLive) 7 else 8
        responsive.widthDp > 1200.dp -> if (isLive) 6 else 6
        responsive.widthDp > 900.dp -> if (isLive) 5 else 5
        responsive.widthDp > 600.dp -> if (isLive) 4 else 4
        else -> if (isLive) 2 else 2
    }

    val gridSpacing = responsive.dp(tokens.spacing.extraLarge)
    val gridState = rememberLazyGridState()

    val targetIndex = remember(items, targetItemId) {
        val idx = items.indexOfFirst { it.id == targetItemId }
        if (idx != -1) idx else 0
    }

    val targetFocusRequester = remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(targetIndex, items) {
        if (items.isNotEmpty() && targetIndex in items.indices) {
            gridState.scrollToItem(targetIndex)
            kotlinx.coroutines.delay(50)
            try {
                targetFocusRequester.requestFocus()
                gridFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { left = sidebarFocusRequester },
        contentPadding = PaddingValues(gridSpacing),
        verticalArrangement = Arrangement.spacedBy(gridSpacing),
        horizontalArrangement = Arrangement.spacedBy(gridSpacing)
    ) {
        itemsIndexed(items, key = { _, item -> item.id + item.type.toString() }) { index, item ->
            val isFav = favorites.any { it.id == item.id && it.type == item.type }
            val progress = watchProgress[item.id.toIntOrNull() ?: -1]
            val itemModifier = if (index == targetIndex) {
                Modifier.focusRequester(targetFocusRequester).focusRequester(gridFocusRequester)
            } else {
                Modifier
            }
            
            if (isLive) {
                ChannelCard(
                    item = item.copy(isFavorite = isFav),
                    modifier = itemModifier,
                    onClick = { onItemClick(item) }
                )
            } else {
                PosterCard(
                    item = item.copy(isFavorite = isFav),
                    progress = progress,
                    modifier = itemModifier,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item, isFav) }
                )
            }
        }
    }
}
