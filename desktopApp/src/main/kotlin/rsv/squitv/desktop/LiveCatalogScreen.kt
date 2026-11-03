package rsv.squitv.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rsv.squitv.shared.brand.AppBrand
import rsv.squitv.shared.xtream.LiveChannel
import rsv.squitv.domain.model.LiveChannelLabels
import rsv.squitv.domain.model.ChannelQuality
import rsv.squitv.shared.xtream.LiveChannelGroups
import rsv.squitv.core.ui.components.cards.ChannelQualityBadge
import rsv.squitv.ui.content.components.rememberContentGridState
import rsv.squitv.core.ui.components.common.PageHeadingText
import rsv.squitv.shared.catalog.CategoryCustomization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

/** Desktop adaptation of Android AppHeader, AppSidebar and ChannelCard; same data/actions. */
@Composable
internal fun LiveCatalogScreen(state: DesktopState, controller: DesktopController, store: DesktopPreferencesStore, modifier: Modifier) {
    val preferences by store.state.collectAsState()
    val logos = rememberDesktopImages(DesktopImageKind.CHANNELS)
    var search by rememberSaveable(state.selectedCategory?.id) { mutableStateOf("") }
    val categoriesState = rememberLazyListState()
    var editingSearch by remember { mutableStateOf(false) }
    val accountScope = remember(state.credentials) { liveCategoryScope(state.credentials) }
    val hidden = preferences.hiddenLiveCategoriesByAccount[accountScope].orEmpty()
    val categoryOrder = preferences.liveCategoryOrderByAccount[accountScope]
    val categories = remember(state.categories, hidden, categoryOrder) {
        CategoryCustomization.apply(state.categories, categoryOrder, hidden) { it.id }
    }
    val preferencesLoaded by store.loaded.collectAsState()
    LaunchedEffect(categories, state.selectedCategory?.id, preferencesLoaded, state.busy,
        state.channelsLoaded, state.loadingChannels, state.channelError) {
        if (preferencesLoaded && !state.busy && state.selectedCategory?.id !in categories.map { it.id }) {
            if (categories.isEmpty()) {
                if (state.selectedCategory != null) controller.clearLiveSelection()
            } else controller.loadChannels(categories.first())
        } else if (preferencesLoaded && !state.busy && !state.channelsLoaded && !state.loadingChannels && state.channelError == null) {
            categories.firstOrNull { it.id == state.selectedCategory?.id }?.let(controller::loadChannels)
        }
    }
    val allHidden = state.categories.isNotEmpty() && categories.isEmpty()
    val showAll: () -> Unit = { store.update {
        val key = accountScope ?: return@update it
        it.copy(hiddenLiveCategoriesByAccount = it.hiddenLiveCategoriesByAccount - key)
    } }
    val query = search.trim()
    var preparedChannels by remember(state.channels, accountScope, preferences.groupLiveChannels,
        preferences.preferredChannelQuality, query) { mutableStateOf<List<LiveChannel>?>(null) }
    LaunchedEffect(state.channels, accountScope, preferences.groupLiveChannels, preferences.preferredChannelQuality, query) {
        preparedChannels = withContext(Dispatchers.Default) {
            // Android CategoryViewModel also prepares/sorts its projection on Dispatchers.Default.
            val visible = LiveChannelGroups.visible(state.channels, accountScope.orEmpty(), preferences.groupLiveChannels,
                ChannelQuality.fromPreference(preferences.preferredChannelQuality))
            val filtered = if (query.isBlank()) visible else visible.filter { channel ->
                ensureActive()
                channel.name.contains(query, ignoreCase = true) || channel.versions.any {
                    it.name.contains(query, ignoreCase = true)
                }
            }
            ensureActive()
            filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }
    val channels = if (state.selectedCategory?.id in hidden) emptyList() else preparedChannels.orEmpty()
    val gridMemory = rememberContentGridState("${accountScope.orEmpty()}/live/${state.selectedCategory?.id}/$query")
    val gridState = gridMemory.scroll
    val gridFocus = remember { FocusRequester() }
    val useFocus = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val savedIndex = remember(channels, gridMemory.focusedItem, gridMemory.focusedStreamKey) {
        channels.indexOfFirst { it.stableKey == gridMemory.focusedItem }.takeIf { it >= 0 }
            ?: gridMemory.focusedStreamKey.removePrefix("LIVE:").toIntOrNull()?.let { id ->
                channels.indexOfFirst { it.containsStream(id) }
            } ?: -1
    }
    val focusIndex = if (savedIndex >= 0) savedIndex else {
        (if (gridMemory.hasInteracted) gridMemory.focusedIndex else gridState.firstVisibleItemIndex)
            .coerceIn(0, channels.lastIndex.coerceAtLeast(0))
    }
    var entered by remember(gridMemory) { mutableStateOf(false) }
    LaunchedEffect(gridMemory) {
        snapshotFlow { gridState.isScrollInProgress }.collect { if (it) gridMemory.hasInteracted = true }
    }
    LaunchedEffect(gridMemory, channels.isNotEmpty()) {
        if (!entered && channels.isNotEmpty()) {
            entered = true
            if (useFocus && !editingSearch) {
                withFrameNanos { }
                if (gridState.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) gridState.scrollToItem(focusIndex)
                withFrameNanos { }
                runCatching { gridFocus.requestFocus() }
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 920.dp && state.selectedCategory != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CatalogHeading(state.selectedCategory.name, Modifier.weight(1f))
                    }
                    CatalogSearch(search, { search = it }, Modifier.fillMaxWidth()) { editingSearch = it }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    CatalogHeading(state.selectedCategory?.name, Modifier.weight(1f))
                    if (state.selectedCategory != null)
                        CatalogSearch(search, { search = it }, Modifier.width(300.dp)) { editingSearch = it }
                }
            }
        }
        state.notice?.let { StatusNotice(it) }
        state.error?.let { StatusNotice(it, error = true) }
        state.catalogSyncError?.let { StatusNotice(it, error = true) }
        BoxWithConstraints(Modifier.weight(1f)) {
        val sidebarWidth = if (maxWidth < 1000.dp) 220.dp else AppBrand.SIDEBAR_WIDTH_DP.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Surface(Modifier.width(sidebarWidth).fillMaxHeight(), shape = RoundedCornerShape(18.dp),
                color = palette.surface.copy(alpha = .55f), border = BorderStroke(1.dp, palette.outline.copy(alpha = .2f))) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("CATEGORIAS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = palette.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(if (state.busy || state.refreshingCatalog) "…" else categories.size.toString(),
                            style = MaterialTheme.typography.labelSmall, color = palette.primary)
                    }
                    if (state.busy || state.refreshingCatalog) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!state.busy && !state.refreshingCatalog && categories.isEmpty()) {
                        Text(if (allHidden) "Todas as categorias estão ocultas." else "Nenhuma categoria disponível.", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp))
                        TextButton(onClick = if (allHidden) showAll else controller::syncCatalog) {
                            Text(if (allHidden) "Mostrar todas" else "Tentar novamente")
                        }
                    }
                    Box(Modifier.weight(1f)) {
                    LazyColumn(state = categoriesState, modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(categories, key = { it.id }) { category ->
                            val selected = category.id == state.selectedCategory?.id
                            DesktopCategoryRow(
                                name = category.name,
                                selected = selected,

                                onClick = { controller.loadChannels(category) }
                            )
                        }
                    }
                    if (categoriesState.canScrollBackward || categoriesState.canScrollForward)
                        VerticalScrollbar(rememberScrollbarAdapter(categoriesState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), style = desktopScrollbarStyle())
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    allHidden -> CatalogMessage("Todas as categorias estão ocultas", modifier = Modifier.weight(1f),
                        onRetry = showAll, actionLabel = "Mostrar todas")
                    state.loadingChannels || preparedChannels == null -> CatalogMessage("Carregando canais…", loading = true, modifier = Modifier.weight(1f))
                    state.channelError != null -> CatalogMessage(state.channelError, error = true,
                        modifier = Modifier.weight(1f), onRetry = state.selectedCategory?.let { category ->
                            { controller.loadChannels(category) }
                        })
                    state.selectedCategory == null -> CatalogMessage("Selecione uma categoria para assistir", modifier = Modifier.weight(1f))
                    channels.isEmpty() -> CatalogMessage(if (search.isBlank()) "Nenhum canal disponível" else "Nenhum canal encontrado",
                        modifier = Modifier.weight(1f))
                    else -> {
                        Text("${channels.size} canais", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                        Box(Modifier.weight(1f)) {
                        LazyVerticalGrid(columns = GridCells.Adaptive(175.dp), state = gridState,
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 14.dp)) {
                            itemsIndexed(channels, key = { _, channel -> channel.stableKey }) { index, channel ->
                                val modifier = (if (index == focusIndex) Modifier.focusRequester(gridFocus) else Modifier)
                                    .onFocusChanged { focus -> if (focus.hasFocus) {
                                        gridMemory.focusedItem = channel.stableKey
                                        gridMemory.focusedStreamKey = "LIVE:${channel.id}"
                                        gridMemory.focusedIndex = index
                                        gridMemory.hasInteracted = true
                                    } }
                                DesktopChannelCard(channel, logos, modifier) {
                                    gridMemory.focusedItem = channel.stableKey
                                    gridMemory.focusedStreamKey = "LIVE:${channel.id}"
                                    gridMemory.focusedIndex = index
                                    gridMemory.hasInteracted = true
                                    controller.play(channel, channels)
                                }
                            }
                        }
                        if (gridState.canScrollBackward || gridState.canScrollForward)
                            VerticalScrollbar(rememberScrollbarAdapter(gridState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), style = desktopScrollbarStyle())
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CatalogHeading(category: String?, modifier: Modifier = Modifier) {
    val windowDragArea = LocalWindowDragArea.current
    windowDragArea(modifier) {
        PageHeadingText("AO VIVO", category ?: "Categorias", palette.primary, palette.onSurfaceVariant,
            MaterialTheme.typography.headlineLarge, MaterialTheme.typography.labelMedium, subtitleMaxLines = 2)
    }
}

@Composable
private fun CatalogSearch(search: String, onSearch: (String) -> Unit, modifier: Modifier, onEditingChanged: (Boolean) -> Unit) {
    OutlinedTextField(search, onSearch,
        placeholder = { Text("Buscar nesta categoria", style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)) },
        trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }) {
            Icon(Icons.Rounded.Close, "Limpar busca", Modifier.size(18.dp))
        } }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = modifier.onFocusChanged { onEditingChanged(it.hasFocus) },
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = palette.outline.copy(alpha = .4f)))
}

@Composable
internal fun DesktopCategoryRow(
    name: String,
    selected: Boolean,
    focused: Boolean = false,
    count: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = rememberVisualInteraction()
    val emphasized = focused || interaction.emphasized
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        interactionSource = interaction.source,
        shape = RoundedCornerShape(12.dp),
        color = if (selected || emphasized) palette.primary.copy(alpha = .12f) else Color.Transparent,
        border = when {
            emphasized -> BorderStroke(2.dp, palette.primary)
            selected -> BorderStroke(1.dp, palette.primary.copy(alpha = .45f))
            else -> null
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected && !emphasized) {
                Box(Modifier.width(4.dp).height(18.dp).background(palette.primary, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) palette.primary else palette.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (count != null && count > 0) {
                Surface(
                    color = if (selected) palette.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) palette.primary else palette.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun DesktopChannelCard(channel: LiveChannel, logos: ChannelLogos, modifier: Modifier = Modifier, play: () -> Unit) {
    val interaction = rememberVisualInteraction()
    val quality = remember(channel.name, channel.versions) {
        channel.versions.mapNotNull { LiveChannelLabels.parse(it.name).resolution }.maxByOrNull { it.rank }
            ?: LiveChannelLabels.parse(channel.name).resolution
    }
    Surface(onClick = play, modifier = modifier.fillMaxWidth().aspectRatio(AppBrand.CHANNEL_ASPECT_RATIO),
        interactionSource = interaction.source, shape = RoundedCornerShape(AppBrand.CARD_CORNER_DP.dp), color = palette.surface,
        border = if (interaction.emphasized) BorderStroke(AppBrand.FOCUS_BORDER_DP.dp, palette.primary)
            else BorderStroke(1.dp, palette.outline.copy(alpha = .22f))) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF141A26), Color(0xFF090B11))))) {
            Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center) {
                ChannelLogo(channel.iconUrl, logos, Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))
            ChannelQualityBadge(quality, palette.primary, palette.onSurfaceVariant,
                Modifier.align(Alignment.TopStart).padding(8.dp))
            Text(channel.name.uppercase(Locale.ROOT), modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (interaction.emphasized) palette.primary else Color.White)
        }
    }
}

@Composable
private fun CatalogMessage(message: String, loading: Boolean = false, error: Boolean = false,
    modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null, actionLabel: String = "Tentar novamente") {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 240.dp
        Column(Modifier.padding(if (compact) 12.dp else 24.dp).widthIn(max = 400.dp)
            .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            else Icon(if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.Monitor,
                null, Modifier.size(if (compact) 28.dp else 48.dp), tint = palette.onSurfaceVariant.copy(alpha = .6f))
            Text(if (loading) message.uppercase(Locale.ROOT) else message,
                style = if (loading) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                color = if (error) palette.error else palette.onSurfaceVariant, textAlign = TextAlign.Center)
            if (onRetry != null) Button(onClick = onRetry) { Text(actionLabel) }
        }
    }
}
