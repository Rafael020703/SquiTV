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
import rsv.squitv.core.ui.components.cards.CompactFavoriteButton
import rsv.squitv.ui.content.components.rememberContentGridState
import rsv.squitv.core.ui.components.common.PageHeadingText
import rsv.squitv.shared.catalog.CategoryCustomization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import rsv.squitv.shared.catalog.LiveCatalogView

/** Desktop adaptation of Android AppHeader, AppSidebar and ChannelCard; same data/actions. */
@Composable
internal fun LiveCatalogScreen(state: DesktopState, controller: DesktopController, store: DesktopPreferencesStore, modifier: Modifier) {
    val preferences by store.state.collectAsState()
    val logos = rememberDesktopImages(DesktopImageKind.CHANNELS)
    val categoriesState = rememberLazyListState()
    var editingSearch by remember { mutableStateOf(false) }
    val accountScope = remember(state.credentials) { liveCategoryScope(state.credentials) }
    var favoriteCategory by rememberSaveable(accountScope) { mutableStateOf(false) }
    val selectedId = if (favoriteCategory) "FAVORITES" else state.selectedCategory?.id
    val selectedName = if (favoriteCategory) "Favoritos" else state.selectedCategory?.name
    var search by rememberSaveable(accountScope, selectedId) { mutableStateOf("") }
    var favoritesOnly by rememberSaveable(accountScope, selectedId) { mutableStateOf(false) }
    val favoriteFilterFocus = remember { FocusRequester() }
    val favoriteRowFocus = remember { FocusRequester() }
    val firstCategoryFocus = remember { FocusRequester() }
    val favoriteIds = preferences.favoriteLiveChannelsByAccount[accountScope].orEmpty()
    val favoritesState by controller.favorites.state.collectAsState()
    val favoriteSnapshot = favoritesState.takeIf { it.accountScope == accountScope && it.requestedType == DesktopSearchType.LIVE }
    val favoriteResults = favoriteSnapshot?.results.orEmpty().filter { it.type == DesktopSearchType.LIVE &&
        it.channel?.isFavorite(favoriteIds) == true }
    val favoriteChannels = favoriteResults.mapNotNull { it.channel }
    val preferencesError by store.error.collectAsState()
    val hidden = preferences.hiddenLiveCategoriesByAccount[accountScope].orEmpty()
    val categoryOrder = preferences.liveCategoryOrderByAccount[accountScope]
    val providerCategories = remember(state.categories, hidden, categoryOrder) {
        CategoryCustomization.apply(state.categories, categoryOrder, hidden) { it.id }
    }
    val index by controller.liveIndex.state.collectAsState()
    var countedView by remember(accountScope) { mutableStateOf<LiveCatalogView?>(null) }
    LaunchedEffect(state.credentials) { state.credentials?.let { controller.liveIndex.load(it) } }
    DisposableEffect(controller) { onDispose { controller.liveIndex.cancel() } }
    LaunchedEffect(index, state.categories, hidden, categoryOrder, preferences.groupLiveChannels, preferences.preferredChannelQuality) {
        countedView = withContext(Dispatchers.Default) {
            if (!index.loaded || index.accountScope != accountScope) null else
                LiveCatalogView.build(state.categories, index.channels, accountScope.orEmpty(), preferences.groupLiveChannels,
                    ChannelQuality.fromPreference(preferences.preferredChannelQuality), categoryOrder.orEmpty(), hidden)
        }
    }
    val categories = remember(providerCategories, countedView) {
        countedView?.categories?.map { rsv.squitv.shared.xtream.LiveCategory(it.id, it.name) } ?: providerCategories
    }
    LaunchedEffect(index.channels, index.loaded, favoriteCategory) {
        if (!favoriteCategory && index.loaded) controller.refreshLiveSelectionFromIndex()
    }
    val preferencesLoaded by store.loaded.collectAsState()
    LaunchedEffect(favoriteCategory, state.credentials, preferencesLoaded, preferences) {
        if (favoriteCategory && preferencesLoaded) {
            if (favoriteIds.isEmpty()) favoriteCategory = false
            else state.credentials?.let { controller.favorites.open(it, preferences, onlyType = DesktopSearchType.LIVE) }
        }
    }
    DisposableEffect(favoriteCategory) { onDispose { if (favoriteCategory) controller.favorites.leave() } }
    LaunchedEffect(categories, state.selectedCategory?.id, preferencesLoaded, state.busy,
        state.channelsLoaded, state.loadingChannels, state.channelError, favoriteCategory, index.loaded, index.error, countedView) {
        if (favoriteCategory) return@LaunchedEffect
        if (!index.loaded && index.error == null) return@LaunchedEffect
        if (index.loaded && countedView == null) return@LaunchedEffect
        if (preferencesLoaded && !state.busy && state.selectedCategory?.id !in categories.map { it.id }) {
            if (categories.isEmpty()) {
                if (state.selectedCategory != null) controller.clearLiveSelection()
            } else controller.loadChannels(categories.first())
        } else if (preferencesLoaded && !state.busy && !state.channelsLoaded && !state.loadingChannels && state.channelError == null) {
            categories.firstOrNull { it.id == state.selectedCategory?.id }?.let(controller::loadChannels)
        }
    }
    val allHidden = state.categories.isNotEmpty() && providerCategories.isEmpty() && categories.isEmpty()
    val showAll: () -> Unit = { store.update {
        val key = accountScope ?: return@update it
        it.copy(hiddenLiveCategoriesByAccount = it.hiddenLiveCategoriesByAccount - key)
    } }
    val query = search.trim()
    val rawChannels = if (favoriteCategory) favoriteChannels else state.channels
    var preparedChannels by remember(rawChannels, accountScope, preferences.groupLiveChannels,
        preferences.preferredChannelQuality, query) { mutableStateOf<List<LiveChannel>?>(null) }
    LaunchedEffect(rawChannels, accountScope, preferences.groupLiveChannels, preferences.preferredChannelQuality, query, favoriteCategory) {
        preparedChannels = withContext(Dispatchers.Default) {
            // Android CategoryViewModel also prepares/sorts its projection on Dispatchers.Default.
            val visible = if (favoriteCategory) rawChannels else LiveChannelGroups.visible(rawChannels, accountScope.orEmpty(), preferences.groupLiveChannels,
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
    val channels = remember(preparedChannels, favoritesOnly, favoriteIds, state.selectedCategory?.id in hidden, favoriteCategory) {
        if (!favoriteCategory && state.selectedCategory?.id in hidden) emptyList()
        else preparedChannels.orEmpty().let { source -> if (favoritesOnly) source.filter { it.isFavorite(favoriteIds) } else source }
    }
    val gridMemory = rememberContentGridState("${accountScope.orEmpty()}/live/$selectedId/$query/$favoritesOnly")
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
    LaunchedEffect(channels, favoritesOnly, preparedChannels != null, favoriteCategory) {
        // Removing a favorite can remove the focused card; keep keyboard navigation in this filtered list.
        if ((favoritesOnly || favoriteCategory) && preparedChannels != null && !state.loadingChannels && useFocus && !editingSearch &&
            gridMemory.hasInteracted && savedIndex < 0) {
            withFrameNanos { }
            if (channels.isNotEmpty() && gridState.layoutInfo.visibleItemsInfo.none { it.index == focusIndex })
                gridState.scrollToItem(focusIndex)
            withFrameNanos { }
            runCatching { if (channels.isNotEmpty()) gridFocus.requestFocus()
                else if (favoriteCategory && favoriteIds.isNotEmpty()) favoriteRowFocus.requestFocus()
                else if (favoriteCategory) firstCategoryFocus.requestFocus() else favoriteFilterFocus.requestFocus() }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 920.dp && selectedName != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CatalogHeading(selectedName, Modifier.weight(1f))
                    }
                    CatalogSearch(search, { search = it }, Modifier.fillMaxWidth()) { editingSearch = it }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    CatalogHeading(selectedName, Modifier.weight(1f))
                    if (selectedName != null)
                        CatalogSearch(search, { search = it }, Modifier.width(300.dp)) { editingSearch = it }
                }
            }
        }
        preferencesError?.let { StatusNotice(it, error = true) }
        index.error?.let { error ->
            StatusNotice(error, error = true)
            TextButton({ state.credentials?.let { controller.liveIndex.load(it, force = true) } }) { Text("Tentar contagens novamente") }
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
                        Text(if (state.busy || state.refreshingCatalog) "…" else (categories.size + if (favoriteIds.isNotEmpty()) 1 else 0).toString(),
                            style = MaterialTheme.typography.labelSmall, color = palette.primary)
                    }
                    if (state.busy || state.refreshingCatalog || index.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                        if (favoriteIds.isNotEmpty()) item(key = "SPECIAL:FAVORITES") {
                            DesktopCategoryRow("Favoritos", favoriteCategory,
                                count = favoriteSnapshot?.takeIf { !it.loading }?.results?.size,
                                modifier = Modifier.focusRequester(favoriteRowFocus), onClick = { favoriteCategory = true })
                        }
                        items(categories, key = { it.id }) { category ->
                            val selected = !favoriteCategory && category.id == state.selectedCategory?.id
                            DesktopCategoryRow(
                                name = category.name,
                                selected = selected,
                                count = countedView?.categories?.firstOrNull { it.id == category.id }?.count,

                                modifier = if (category.id == categories.firstOrNull()?.id) Modifier.focusRequester(firstCategoryFocus) else Modifier,
                                onClick = { favoriteCategory = false; controller.loadChannels(category) }
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
                if (favoriteCategory) favoriteSnapshot?.errors?.get(DesktopSearchType.LIVE)?.let { error ->
                    StatusNotice(error, error = true)
                    TextButton({ state.credentials?.let { controller.favorites.open(it, preferences, force = true, onlyType = DesktopSearchType.LIVE) } }) {
                        Text("Tentar novamente")
                    }
                }
                if (selectedName != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if ((!favoriteCategory && state.loadingChannels) || favoriteCategory && (favoriteSnapshot == null || favoriteSnapshot.loading) || preparedChannels == null) "Carregando…" else "${channels.size} canais",
                        Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                    if (!favoriteCategory) FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, { Text("Favoritos desta categoria") },
                        enabled = preferencesLoaded,
                        leadingIcon = { Icon(if (favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            null, Modifier.size(16.dp)) }, modifier = Modifier.focusRequester(favoriteFilterFocus))
                }
                when {
                    !favoriteCategory && ((!index.loaded && index.loading) || index.loaded && countedView == null) -> CatalogMessage("Carregando contagens…", loading = true, modifier = Modifier.weight(1f))
                    allHidden && !favoriteCategory -> CatalogMessage("Todas as categorias estão ocultas", modifier = Modifier.weight(1f),
                        onRetry = showAll, actionLabel = "Mostrar todas")
                    favoriteCategory && channels.isEmpty() && (favoriteSnapshot == null || favoriteSnapshot.loading) ||
                        !favoriteCategory && state.loadingChannels || preparedChannels == null -> CatalogMessage("Carregando canais…", loading = true, modifier = Modifier.weight(1f))
                    state.channelError != null && !favoriteCategory -> CatalogMessage(state.channelError, error = true,
                        modifier = Modifier.weight(1f), onRetry = state.selectedCategory?.let { category ->
                            { controller.loadChannels(category) }
                        })
                    state.selectedCategory == null && !favoriteCategory -> CatalogMessage("Selecione uma categoria para assistir", modifier = Modifier.weight(1f))
                    channels.isEmpty() -> CatalogMessage(if (favoritesOnly) "Nenhum favorito nesta categoria corresponde ao filtro"
                        else if (search.isBlank()) "Nenhum canal disponível" else "Nenhum canal encontrado",
                        modifier = Modifier.weight(1f))
                    else -> {
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
                                DesktopChannelCard(channel, logos, modifier, isFavorite = channel.isFavorite(favoriteIds),
                                    favoriteEnabled = preferencesLoaded && accountScope != null,
                                    onToggleFavorite = { accountScope?.let { account -> store.update {
                                        it.toggleLiveFavorite(account, channel.id, channel.versions.map { it.id })
                                    } } }) {
                                    gridMemory.focusedItem = channel.stableKey
                                    gridMemory.focusedStreamKey = "LIVE:${channel.id}"
                                    gridMemory.focusedIndex = index
                                    gridMemory.hasInteracted = true
                                    if (favoriteCategory) favoriteResults.firstOrNull { it.channel == channel }?.let { item ->
                                        val byKey = favoriteResults.associateBy { it.channel?.stableKey }
                                        val visible = channels.mapNotNull { byKey[it.stableKey] }
                                        if (visible.size == channels.size) controller.openFavoriteResult(item, visible)
                                    } else controller.play(channel, channels, favoriteSource = favoritesOnly)
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
internal fun DesktopChannelCard(channel: LiveChannel, logos: ChannelLogos, modifier: Modifier = Modifier,
    isFavorite: Boolean = false, favoriteEnabled: Boolean = true, onToggleFavorite: (() -> Unit)? = null, play: () -> Unit) {
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
            if (onToggleFavorite != null) {
                val favoriteInteraction = rememberVisualInteraction()
                CompactFavoriteButton(isFavorite,
                    "${channel.name}: ${if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos"}",
                    onToggleFavorite, if (favoriteInteraction.emphasized) palette.primary else palette.background.copy(alpha = .68f),
                    if (favoriteInteraction.emphasized) Color.Black else palette.error,
                    if (favoriteInteraction.emphasized) Color.Black else palette.onSurface,
                    Modifier.align(Alignment.TopEnd).padding(4.dp), enabled = favoriteEnabled,
                    interactionSource = favoriteInteraction.source)
            }
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
