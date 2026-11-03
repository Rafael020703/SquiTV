package rsv.squitv.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import rsv.squitv.core.ui.components.player.*
import rsv.squitv.shared.xtream.OnDemandPlayback
import rsv.squitv.shared.brand.AppBrand
import rsv.squitv.ui.content.components.rememberContentGridState
import rsv.squitv.core.ui.components.common.PageHeadingText
import rsv.squitv.shared.catalog.CategoryCustomization
import rsv.squitv.shared.catalog.RecentlyAddedCatalog
import java.text.Collator
import java.util.Locale

/** Android MoviesScreen/SeriesScreen layout: header, provider categories, poster grid. */
@Composable
internal fun OnDemandCatalogScreen(state: OnDemandState, controller: DesktopController, store: DesktopPreferencesStore) {
    val browse = state.current
    val kind = state.kind
    val preferences by store.state.collectAsState()
    val preferencesLoaded by store.loaded.collectAsState()
    val preferencesError by store.error.collectAsState()
    val account = controller.state.value.credentials
    val accountScope = remember(account) { liveCategoryScope(account).orEmpty() }
    val mode = preferences.catalogMode(accountScope, kind)
    var selectedNav by rememberSaveable(accountScope, kind, mode) { mutableStateOf<String?>(null) }
    var selectionRestored by rememberSaveable(accountScope, kind, mode) { mutableStateOf(false) }
    val savedPosition = preferences.browseMemory.position(accountScope, kind.name)
    LaunchedEffect(preferencesLoaded, accountScope, kind, mode) {
        if (preferencesLoaded && !selectionRestored) {
            selectedNav = savedPosition?.categoryId
            selectionRestored = true
        }
    }
    val favoriteRequested = selectedNav == "FAVORITES"
    val favoriteType = if (kind == CatalogKind.SERIES) DesktopSearchType.SERIES else DesktopSearchType.MOVIES
    val favoriteIds = preferences.favoriteCatalogItemsByScope["$accountScope/${kind.name}"].orEmpty()
    val favoritesState by controller.favorites.state.collectAsState()
    val favoriteSnapshot = favoritesState.takeIf { it.accountScope == accountScope && it.requestedType == favoriteType }
    val favoriteResults = favoriteSnapshot?.results.orEmpty().filter { it.type == favoriteType && it.id in favoriteIds }
    var search by rememberSaveable(kind, selectedNav) { mutableStateOf("") }
    val historyData = controller.watchHistory?.state?.collectAsState()?.value
    val historyError = controller.watchHistory?.error?.collectAsState()?.value
    val continuing = remember(historyData, accountScope, kind) { controller.watchHistory?.continuing(accountScope, kind == CatalogKind.SERIES).orEmpty() }
    val progressByCatalog = remember(continuing) { continuing.associateBy { it.catalogId } }
    val preparation = controller.catalog.preparation
    val preparationState = preparation?.state?.collectAsState()?.value
    DisposableEffect(kind, mode, preparation, browse.loadingAll, favoriteRequested) {
        preparation?.setActive(if (mode == rsv.squitv.domain.model.CategoryMode.GENRE && !browse.loadingAll && !favoriteRequested) kind else null)
        onDispose { preparation?.setActive(null) }
    }
    DisposableEffect(kind, mode) { onDispose { controller.catalog.cancelAllItemsLoading(kind) } }
    LaunchedEffect(kind, mode, preferencesLoaded, browse.categoriesLoaded, browse.allItemsLoaded, browse.loadingAll, browse.allError, favoriteRequested) {
        if (favoriteRequested) controller.catalog.cancelAllItemsLoading(kind)
        else if (preferencesLoaded && browse.categoriesLoaded && !browse.allItemsLoaded && !browse.loadingAll && browse.allError == null)
            controller.catalog.loadAllItems(kind)
    }
    var alphabetical by rememberSaveable(kind) { mutableStateOf(false) }
    var editingSearch by remember { mutableStateOf(false) }
    val sidebar = androidx.compose.foundation.lazy.rememberLazyListState()
    val images = rememberDesktopImages(DesktopImageKind.POSTERS)
    val hidden = preferences.hiddenCategories(accountScope, kind)
    val categoryOrder = preferences.categoryOrder(accountScope, kind)
    val categories = remember(browse.categories, hidden, categoryOrder) {
        CategoryCustomization.apply(browse.categories, categoryOrder, hidden) { it.id }
    }
    var navigation by remember(accountScope, kind, mode) {
        mutableStateOf<List<rsv.squitv.shared.catalog.CatalogCategoryEntry<CatalogItem>>?>(null)
    }
    LaunchedEffect(accountScope, kind, categories, browse.allItems, browse.allItemsLoaded, mode, hidden, continuing, favoriteResults, favoriteIds.isNotEmpty()) {
        navigation = withContext(Dispatchers.Default) {
            val visible = browse.allItems.filter { it.categoryId !in hidden }
            val base = if (!browse.allItemsLoaded && mode == rsv.squitv.domain.model.CategoryMode.PROVIDER) categories.map {
                rsv.squitv.shared.catalog.CatalogCategoryEntry<CatalogItem>(it.id, it.name, emptyList())
            } else if (!browse.allItemsLoaded) listOf(
                rsv.squitv.shared.catalog.CatalogCategoryEntry<CatalogItem>("ALL", "Todos", emptyList())) else {
                rsv.squitv.shared.catalog.CatalogCategoryGrouping.catalog(visible, categories.map { it.id to it.name },
                    "Todos", "Sem classificação", "FILTROS POR GÊNERO", "CATEGORIAS DO SERVIDOR", mode,
                    Locale.forLanguageTag("pt-BR"), { it.categoryId }, { it.genre })
            }
            val special = buildList {
                if (continuing.isNotEmpty()) add(rsv.squitv.shared.catalog.CatalogCategoryEntry("CONTINUE_WATCHING", "Continuar assistindo", continuing.map { it.item() }))
                if (favoriteIds.isNotEmpty()) add(rsv.squitv.shared.catalog.CatalogCategoryEntry("FAVORITES", "Favoritos", favoriteResults.mapNotNull { it.catalogItem }))
                if (kind == CatalogKind.MOVIES && browse.allItemsLoaded) {
                    val recent = RecentlyAddedCatalog.select(visible, 100, added = { it.added }, id = { it.id })
                    if (recent.isNotEmpty()) add(rsv.squitv.shared.catalog.CatalogCategoryEntry("RECENTS", "Adicionados recentemente", recent))
                }
            }
            if (base.firstOrNull()?.id == "ALL") base.take(1) + special + base.drop(1) else special + base
        }
    }
    val entries = navigation.orEmpty()
    val requestedCategory = selectedNav ?: browse.category?.id
    val visibleCategoryIds = entries.filterNot { it.isHeader }.map { it.id }
    val resolvedCategory = rsv.squitv.shared.catalog.CatalogBrowseMemory.selectCategory(visibleCategoryIds, requestedCategory)
    val canResolveCategory = browse.allItemsLoaded || browse.allError != null || requestedCategory in visibleCategoryIds
    val selected = if (!selectionRestored || !canResolveCategory) null else entries.firstOrNull { it.id == resolvedCategory }
    val continueSelected = selected?.id == "CONTINUE_WATCHING"
    val favoriteSelected = selected?.id == "FAVORITES"
    val specialSelected = continueSelected || favoriteSelected || selected?.id == "RECENTS"
    LaunchedEffect(favoriteSelected, account, preferencesLoaded, preferences) {
        if (favoriteSelected && preferencesLoaded && account != null)
            controller.favorites.open(account, preferences, onlyType = favoriteType)
    }
    DisposableEffect(favoriteSelected, favoriteType) { onDispose { if (favoriteSelected) controller.favorites.leave() } }
    val allHidden = browse.categories.isNotEmpty() && categories.isEmpty() && !specialSelected &&
        (mode == rsv.squitv.domain.model.CategoryMode.PROVIDER || navigation != null && entries.none { !it.isHeader })
    LaunchedEffect(entries, selected?.id, browse.category?.id, browse.categoriesLoaded, browse.loadingCategories,
        browse.itemsLoaded, browse.loading, browse.error, preferencesLoaded, mode, selectionRestored) {
        if (preferencesLoaded && selectionRestored && browse.categoriesLoaded && !browse.loadingCategories && navigation != null &&
            (browse.allItemsLoaded || browse.allError != null || favoriteRequested && favoriteSelected)) {
            selectedNav = selected?.id
            store.rememberCatalogPosition(accountScope, kind.name, selected?.id, immediate = true)
            if (mode == rsv.squitv.domain.model.CategoryMode.PROVIDER && !browse.allItemsLoaded && browse.allError != null && !specialSelected && browse.error == null) {
                val provider = categories.firstOrNull { it.id == selected?.id }
                if (provider != null && (browse.category?.id != provider.id || !browse.itemsLoaded && !browse.loading)) controller.catalog.selectCategory(provider)
                else if (provider == null && browse.category != null) controller.catalog.clearSelection(kind)
            }
        }
    }
    val sourceItems = when {
        specialSelected -> selected?.items.orEmpty()
        browse.allItemsLoaded || mode == rsv.squitv.domain.model.CategoryMode.GENRE -> selected?.items.orEmpty()
        selected != null && selected.id == browse.category?.id -> browse.items
        else -> emptyList()
    }
    val query = search.trim()
    var preparedItems by remember(sourceItems, query, alphabetical) { mutableStateOf<List<CatalogItem>?>(null) }
    LaunchedEffect(sourceItems, query, alphabetical) {
        preparedItems = withContext(Dispatchers.Default) {
            val filtered = if (query.isBlank()) sourceItems else sourceItems.filter {
                ensureActive()
                it.name.contains(query, true)
            }
            ensureActive()
            if (!alphabetical) filtered else {
                val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
                filtered.sortedWith { a, b -> collator.compare(a.name, b.name) }
            }
        }
    }
    val items = preparedItems.orEmpty()
    val gridMemory = rememberContentGridState("$accountScope/${kind.name}/${mode.name}/${selected?.id}/$alphabetical/${search.trim()}")
    val grid = gridMemory.scroll
    val gridFocus = remember { FocusRequester() }
    val selectedCategoryFocus = remember { FocusRequester() }
    val useFocus = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val savedIndex = remember(items, gridMemory.focusedItem) { items.indexOfFirst { it.id.toString() == gridMemory.focusedItem } }
    val persistentIndex = remember(items, savedPosition, selected?.id) {
        val item = selected?.id?.let { savedPosition?.itemsByCategory?.get(it) }
            ?: savedPosition?.takeIf { it.categoryId == selected?.id }?.itemId
        items.indexOfFirst { it.id.toString() == item }
    }
    val focusIndex = if (savedIndex >= 0) savedIndex else if (!gridMemory.hasInteracted && persistentIndex >= 0) persistentIndex else {
        (if (gridMemory.hasInteracted) gridMemory.focusedIndex else grid.firstVisibleItemIndex)
            .coerceIn(0, items.lastIndex.coerceAtLeast(0))
    }
    var entered by remember(gridMemory) { mutableStateOf(false) }
    LaunchedEffect(gridMemory) {
        snapshotFlow { grid.isScrollInProgress }.collect { if (it) gridMemory.hasInteracted = true }
    }
    LaunchedEffect(gridMemory, items.isNotEmpty(), selectionRestored) {
        if (!entered && items.isNotEmpty() && selectionRestored && (browse.allItemsLoaded || browse.allError != null || specialSelected)) {
            entered = true
            if (!gridMemory.hasInteracted && persistentIndex >= 0) grid.scrollToItem(persistentIndex)
            if (useFocus && !editingSearch) {
                withFrameNanos { }
                if (grid.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) grid.scrollToItem(focusIndex)
                withFrameNanos { }
                runCatching { gridFocus.requestFocus() }
            }
        }
    }
    LaunchedEffect(items, favoriteSelected) {
        if (favoriteSelected && useFocus && !editingSearch && gridMemory.hasInteracted && savedIndex < 0) {
            withFrameNanos { }
            if (items.isNotEmpty() && grid.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) grid.scrollToItem(focusIndex)
            withFrameNanos { }
            runCatching { if (items.isEmpty()) selectedCategoryFocus.requestFocus() else gridFocus.requestFocus() }
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DesktopPageHeading(kind.title, selected?.name ?: "Categorias do servidor") {
            CatalogModeSelector(mode, preferencesLoaded) { chosen -> store.update { it.withCatalogMode(accountScope, kind, chosen) } }
            FilterChip(alphabetical, { alphabetical = !alphabetical }, { Text("A–Z") },
                leadingIcon = { Icon(Icons.Rounded.SortByAlpha, null, Modifier.size(18.dp)) })
            OutlinedTextField(search, { search = it }, singleLine = true,
                placeholder = { Text("Buscar nesta categoria") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.width(260.dp).onFocusChanged { editingSearch = it.hasFocus }, shape = RoundedCornerShape(16.dp))
        }
        preferencesError?.let { StatusNotice(it, error = true) }
        if (mode == rsv.squitv.domain.model.CategoryMode.PROVIDER && browse.allError != null) {
            StatusNotice("Contagens indisponíveis. ${browse.allError}", error = true)
            TextButton({ controller.catalog.loadAllItems(kind, force = true) }) { Text("Tentar contagens novamente") }
        }
        historyError?.let { StatusNotice(it, error = true) }
        if (favoriteSelected) favoriteSnapshot?.errors?.get(favoriteType)?.let { error ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { StatusNotice(error, error = true) }
                TextButton({ account?.let { controller.favorites.open(it, preferences, force = true, onlyType = favoriteType) } }) {
                    Text("Tentar novamente")
                }
            }
        }
        if (preferencesLoaded && accountScope.isNotEmpty() && "$accountScope/${kind.name}" !in preferences.catalogModesByScope)
            CatalogFirstChoice(accountScope, kind, store)
        if (mode == rsv.squitv.domain.model.CategoryMode.GENRE && preparation != null && preparationState?.kind == kind)
            CatalogPreparationPanel(preparationState, preparation) { controller.catalog.loadAllItems(kind, force = true) }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(Modifier.width(220.dp).fillMaxHeight(), shape = RoundedCornerShape(18.dp),
                color = palette.surface.copy(alpha = .55f), border = BorderStroke(1.dp, palette.outline.copy(alpha = .2f))) {
                Column(Modifier.padding(10.dp)) {
                    Text("CATEGORIAS", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    if (browse.loadingCategories || browse.loadingAll) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f)) {
                        LazyColumn(state = sidebar, verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
                            items(entries, key = { it.id }) { entry ->
                                if (entry.isHeader) Text(entry.name, Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.labelSmall, color = palette.primary)
                                else DesktopCategoryRow(entry.name, selected?.id == entry.id,
                                    modifier = if (selected?.id == entry.id) Modifier.focusRequester(selectedCategoryFocus) else Modifier,
                                    count = if (entry.id == "FAVORITES") favoriteSnapshot?.takeIf { !it.loading }?.results?.size
                                        else if (browse.allItemsLoaded || entry.id == "CONTINUE_WATCHING") entry.items.size else null,
                                    onClick = {
                                        selectedNav = entry.id
                                        if (mode == rsv.squitv.domain.model.CategoryMode.PROVIDER && !browse.allItemsLoaded && entry.id !in setOf("CONTINUE_WATCHING", "FAVORITES", "RECENTS"))
                                            categories.firstOrNull { it.id == entry.id }?.let(controller.catalog::selectCategory)
                                    })
                            }
                        }
                        if (sidebar.canScrollBackward || sidebar.canScrollForward)
                            VerticalScrollbar(rememberScrollbarAdapter(sidebar), Modifier.align(Alignment.CenterEnd),
                                style = desktopScrollbarStyle())
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    !specialSelected && !browse.allItemsLoaded && browse.loadingAll && !browse.itemsLoaded -> CatalogStateMessage("Carregando catálogo e contagens…", loading = true)
                    favoriteSelected && sourceItems.isEmpty() && (favoriteSnapshot == null || favoriteSnapshot.loading) -> CatalogStateMessage("Carregando favoritos…", loading = true)
                    !browse.categoriesLoaded && browse.error != null && !specialSelected -> CatalogStateMessage(browse.error) {
                        controller.catalog.refreshCategories()
                    }
                    allHidden -> CatalogStateMessage("Todas as categorias estão ocultas", actionLabel = "Mostrar todas") {
                        store.update { it.withHiddenCategories(accountScope, kind, emptySet()) }
                    }
                    mode == rsv.squitv.domain.model.CategoryMode.GENRE && browse.allError != null && !specialSelected -> CatalogStateMessage(browse.allError) {
                        controller.catalog.loadAllItems(kind, force = true)
                    }
                    !specialSelected && (browse.loadingCategories || mode == rsv.squitv.domain.model.CategoryMode.GENRE && (!browse.allItemsLoaded || browse.loadingAll) ||
                        mode == rsv.squitv.domain.model.CategoryMode.PROVIDER && !browse.allItemsLoaded && (browse.loading || selected?.id != browse.category?.id && selected != null)) || preparedItems == null -> CatalogStateMessage("Carregando catálogo…", loading = true)
                    browse.error != null && !browse.allItemsLoaded && !specialSelected && mode == rsv.squitv.domain.model.CategoryMode.PROVIDER -> CatalogStateMessage(browse.error) {
                        browse.category?.let(controller.catalog::selectCategory) ?: controller.catalog.refreshCategories()
                    }
                    items.isEmpty() -> CatalogStateMessage(if (search.isBlank()) "Nenhum conteúdo disponível" else "Nenhum resultado encontrado")
                    else -> {
                        LazyVerticalGrid(GridCells.Adaptive(140.dp), state = grid,
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp), contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                                val modifier = (if (index == focusIndex) Modifier.focusRequester(gridFocus) else Modifier)
                                    .onFocusChanged { focus -> if (focus.hasFocus) {
                                        gridMemory.focusedItem = item.id.toString()
                                        store.rememberCatalogPosition(accountScope, kind.name, selected?.id, item.id.toString())
                                        gridMemory.focusedIndex = index
                                        gridMemory.hasInteracted = true
                                    } }
                                CatalogPoster(item, images, modifier, progress = progressByCatalog[item.id]?.progress,
                                    isFavorite = preferences.isCatalogFavorite(accountScope, kind, item.id),
                                    favoriteEnabled = preferencesLoaded && accountScope.isNotEmpty(),
                                    onToggleFavorite = { store.update { it.toggleCatalogFavorite(accountScope, kind, item.id) } }) {
                                    gridMemory.focusedItem = item.id.toString()
                                    gridMemory.focusedIndex = index
                                    gridMemory.hasInteracted = true
                                    store.rememberCatalogPosition(accountScope, kind.name, selected?.id, item.id.toString(), immediate = true)
                                    if (continueSelected) progressByCatalog[item.id]?.let(controller::openContinueEntry)
                                    else if (favoriteSelected) favoriteResults.firstOrNull { it.id == item.id }?.let {
                                        controller.openFavoriteResult(it, favoriteResults)
                                    }
                                    else controller.catalog.openDetail(item)
                                }
                            }
                        }
                        if (grid.canScrollBackward || grid.canScrollForward)
                            VerticalScrollbar(rememberScrollbarAdapter(grid), Modifier.align(Alignment.CenterEnd), style = desktopScrollbarStyle())
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopPageHeading(title: String, subtitle: String,
    actions: (@Composable RowScope.() -> Unit)? = null) {
    val drag = LocalWindowDragArea.current
    val heading: @Composable () -> Unit = {
        PageHeadingText(title, subtitle, palette.primary, palette.onSurfaceVariant,
            MaterialTheme.typography.headlineLarge, MaterialTheme.typography.labelSmall,
            titleMaxLines = 1, subtitleMaxLines = 2)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 760.dp && actions != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                drag(Modifier.fillMaxWidth(), heading)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                drag(Modifier.weight(1f), heading)
                actions?.invoke(this)
            }
        }
    }
}

@Composable
internal fun DesktopMetadataBadge(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text.uppercase(Locale.ROOT),
            color = palette.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

@Composable
internal fun CatalogPoster(item: CatalogItem, images: ChannelLogos, modifier: Modifier = Modifier,
    progress: Float? = null, isFavorite: Boolean = false, favoriteEnabled: Boolean = true,
    onToggleFavorite: (() -> Unit)? = null, open: () -> Unit) {
    val interaction = rememberVisualInteraction()
    Surface(onClick = open, interactionSource = interaction.source, modifier = modifier, color = Color.Transparent,
        shape = RoundedCornerShape(AppBrand.CARD_CORNER_DP.dp),
        border = if (interaction.emphasized) BorderStroke(2.dp, palette.primary) else BorderStroke(1.dp, palette.outline.copy(alpha = 0.15f))) {
        rsv.squitv.core.ui.components.cards.PosterCardContent(
            name = item.name, focused = interaction.emphasized, primary = palette.primary,
            text = palette.onSurface, background = palette.surfaceVariant.copy(alpha = .1f),
            titleStyle = MaterialTheme.typography.labelSmall, titleSize = MaterialTheme.typography.labelSmall.fontSize,
            shape = RoundedCornerShape(AppBrand.CARD_CORNER_DP.dp), aspectRatio = 2f / 3f,
            spacing = 8.dp, titlePadding = 4.dp, progressHeight = 2.dp,
            progress = progress,
            image = { CatalogImage(item.cover, images, it) },
            badges = {
                if (onToggleFavorite != null) DesktopCatalogFavoriteButton(item.name, isFavorite, favoriteEnabled,
                    onToggleFavorite, Modifier.align(Alignment.TopEnd).padding(4.dp))
                item.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { rating ->
                    rsv.squitv.core.ui.components.cards.RatingBadgeContent(rating, MaterialTheme.typography.labelLarge,
                        Modifier.align(Alignment.TopStart).padding(8.dp))
                }
            })
    }
}

@Composable
private fun DesktopCatalogFavoriteButton(name: String, isFavorite: Boolean, enabled: Boolean,
    toggle: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = rememberVisualInteraction()
    rsv.squitv.core.ui.components.cards.CompactFavoriteButton(isFavorite,
        "$name: ${if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos"}", toggle,
        if (interaction.emphasized) palette.primary else palette.background.copy(alpha = .68f),
        if (interaction.emphasized) Color.Black else palette.error,
        if (interaction.emphasized) Color.Black else palette.onSurface,
        modifier, enabled = enabled, interactionSource = interaction.source)
}

@Composable
internal fun CatalogImage(url: String?, images: ChannelLogos, modifier: Modifier, fallbacks: List<String?> = emptyList()) {
    val bitmap by rememberRemoteBitmap(listOf(url) + fallbacks, images)
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Rounded.Movie, null, Modifier.size(48.dp), tint = palette.onSurfaceVariant.copy(alpha = .35f))
    }
}

@Composable
internal fun CatalogStateMessage(message: String, loading: Boolean = false, actionLabel: String = "Tentar novamente", retry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 400.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            Text(message, color = palette.onSurfaceVariant, textAlign = TextAlign.Center)
            retry?.let { Button(it) { Text(actionLabel) } }
        }
    }
}

/** Android details hierarchy: poster, metadata/synopsis and play; seasons with real episodes. */
@Composable
internal fun OnDemandDetailScreen(detail: CatalogDetail, controller: DesktopController, store: DesktopPreferencesStore) {
    val preferences by store.state.collectAsState()
    val preferencesLoaded by store.loaded.collectAsState()
    val preferencesError by store.error.collectAsState()
    val accountScope = liveCategoryScope(controller.state.value.credentials)
    val isFavorite = preferences.isCatalogFavorite(accountScope, detail.kind, detail.item.id)
    val historyData = controller.watchHistory?.state?.collectAsState()?.value
    val historyError = controller.watchHistory?.error?.collectAsState()?.value
    val saved = remember(historyData, detail.item.id, detail.kind) {
        liveCategoryScope(controller.state.value.credentials)?.let {
            controller.watchHistory?.latest(it, detail.kind == CatalogKind.SERIES, detail.item.id)
        }
    }
    val images = rememberDesktopImages(DesktopImageKind.DETAILS)
    val downloads = controller.downloads
    val downloadEntries = downloads?.state?.collectAsState()?.value?.entries.orEmpty()
    val downloaded = downloadEntries.firstOrNull {
        it.account == accountScope && it.kind == DesktopDownloadKind.MOVIE && it.streamId == detail.item.id
    }
    val episodeDownloads = remember(downloadEntries, accountScope, detail.item.id) {
        downloadEntries.filter { it.account == accountScope && it.kind == DesktopDownloadKind.EPISODE && it.seriesId == detail.item.id }
            .associateBy { it.streamId }
    }
    val downloadProgress = downloads?.progress?.collectAsState()?.value
    val downloadNotice = downloads?.notice?.collectAsState()?.value
    val downloadError = downloads?.error?.collectAsState()?.value
    val info = detail.series
    val seasons = remember(info) { info?.let(OnDemandPlayback::seasons).orEmpty() }
    var season by rememberSaveable(detail.kind, detail.item.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(detail.lastPlayedSeason) { detail.lastPlayedSeason?.let { season = it } }
    val selectedSeason = season?.takeIf { selected -> seasons.any { it.first == selected } }
        ?: seasons.firstOrNull { it.first == 1 }?.first ?: seasons.firstOrNull()?.first
    val episodes = remember(info, selectedSeason) { if (info != null && selectedSeason != null)
        OnDemandPlayback.episodes(info, selectedSeason) else emptyList() }
    val metadata = info?.info ?: detail.item.series
    val movie = detail.movie?.info
    val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
    val seasonScroll = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val seasonsFocus = remember { FocusRequester() }
    val episodesFocus = remember { FocusRequester() }
    var focusedEpisode by rememberSaveable(detail.item.id) { mutableStateOf<String?>(null) }
    val useFocus = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val episodeFocusIndex = episodes.indexOfFirst { it.id == (detail.lastPlayedEpisodeId ?: focusedEpisode) }
        .coerceAtLeast(0)
    LaunchedEffect(seasons, selectedSeason) {
        val index = seasons.indexOfFirst { it.first == selectedSeason }
        if (index >= 0) {
            withFrameNanos { }
            if (seasonScroll.layoutInfo.visibleItemsInfo.none { it.index == index }) seasonScroll.scrollToItem(index)
        }
    }
    LaunchedEffect(selectedSeason, episodes, detail.lastPlayedEpisodeId) {
        if (!detail.loading && episodes.isNotEmpty() && useFocus) {
            val index = episodeFocusIndex + 2 // Metadata and season heading precede episode rows.
            withFrameNanos { }
            if (scroll.layoutInfo.visibleItemsInfo.none { it.index == index }) scroll.scrollToItem(index)
            withFrameNanos { }
            runCatching { episodesFocus.requestFocus() }
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DesktopPageHeading(detail.kind.title, detail.item.name) {
            DesktopCatalogFavoriteButton(detail.item.name, isFavorite, preferencesLoaded && accountScope != null, {
                accountScope?.let { account -> store.update { it.toggleCatalogFavorite(account, detail.kind, detail.item.id) } }
            })
            IconButton(controller.catalog::closeDetail) { Icon(Icons.Rounded.Close, "Fechar detalhes") }
        }
        preferencesError?.let { StatusNotice(it, error = true) }
        detail.error?.let { error ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { StatusNotice(error, error = true) }
                TextButton(controller.catalog::retryDetail) { Text("Tentar novamente") }
            }
        }
        if (detail.kind == CatalogKind.SERIES) {
            downloadError?.let { StatusNotice(it, error = true) }
            downloadNotice?.let { Text(it, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
        if (detail.loading && detail.kind == CatalogKind.SERIES) {
            Text("Carregando temporadas e episódios…", color = palette.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (detail.kind == CatalogKind.SERIES && seasons.isNotEmpty()) {
                Surface(Modifier.width(220.dp).fillMaxHeight(), color = palette.surface.copy(alpha = .55f),
                    shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TEMPORADAS", Modifier.padding(8.dp), color = palette.primary,
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        Box(Modifier.weight(1f)) {
                            LazyColumn(state = seasonScroll, modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(seasons, key = { it.first }) { (number, name) ->
                                    val selected = selectedSeason == number
                                    DesktopCategoryRow(
                                        name = name,
                                        selected = selected,
                                        modifier = (if (selected) Modifier.focusRequester(seasonsFocus) else Modifier)
                                            .focusProperties { right = episodesFocus },
                                        onClick = {
                                            season = number
                                            scope.launch { scroll.scrollToItem(1) }
                                        }
                                    )
                                }
                            }
                            if (seasonScroll.canScrollBackward || seasonScroll.canScrollForward)
                                VerticalScrollbar(rememberScrollbarAdapter(seasonScroll), Modifier.align(Alignment.CenterEnd),
                                    style = desktopScrollbarStyle())
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    item(key = "metadata") {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Surface(shape = RoundedCornerShape(18.dp), color = palette.surfaceVariant) {
                            CatalogImage(movie?.movieImage, images,
                                Modifier.width(if (detail.kind == CatalogKind.SERIES) 112.dp else 180.dp).aspectRatio(2f / 3f),
                                fallbacks = listOf(metadata?.cover, detail.item.cover))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(detail.item.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                            val facts = listOfNotNull(movie?.releaseDate ?: metadata?.releaseDate,
                                movie?.genre ?: metadata?.genre, movie?.duration ?: metadata?.episodeRunTime,
                                (movie?.rating ?: metadata?.rating ?: detail.item.rating)?.let { "★ $it" })
                            if (facts.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    facts.forEach { fact -> DesktopMetadataBadge(fact) }
                                }
                            }
                            if (detail.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(movie?.plot?.takeIf(String::isNotBlank) ?: metadata?.plot?.takeIf(String::isNotBlank)
                                ?: "Sinopse não informada pelo servidor.", color = palette.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                            (movie?.director ?: metadata?.director)?.takeIf(String::isNotBlank)?.let {
                                Text("Direção: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            (movie?.cast ?: metadata?.cast)?.takeIf(String::isNotBlank)?.let {
                                Text("Elenco: $it", style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                            }
                            historyError?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
                            if (saved != null) Button(controller.catalog::resumePlayback,
                                enabled = !detail.loading && (detail.kind == CatalogKind.MOVIES || detail.series != null)) {
                                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp))
                                Text("CONTINUAR · ${watchTime(saved.positionMs)}")
                            }
                            if (detail.kind == CatalogKind.MOVIES && saved == null) Button(controller.catalog::playMovie) {
                                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("ASSISTIR")
                            }
                            if (detail.kind == CatalogKind.MOVIES && downloads != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(controller::downloadMovie,
                                        enabled = !detail.loading && downloadProgress == null && downloaded?.status != DesktopDownloadStatus.COMPLETED) {
                                        Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(8.dp))
                                        Text(if (downloadProgress?.key == downloaded?.key && downloadProgress != null) "BAIXANDO…"
                                            else if (downloaded?.status == DesktopDownloadStatus.COMPLETED) "BAIXADO" else "BAIXAR")
                                    }
                                    TextButton(controller::openDownloads) { Text("Ver downloads") }
                                }
                                downloadError?.let { StatusNotice(it, error = true) }
                                downloadNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant) }
                            }
                        }
                    }
                    }
                    if (detail.kind == CatalogKind.SERIES && !detail.loading && detail.error == null) {
                        if (seasons.isEmpty()) item(key = "no-seasons") {
                            Text("O servidor não informou temporadas ou episódios.", color = palette.onSurfaceVariant)
                        }
                        else {
                            item(key = "episodes-heading") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val allSavedOrQueued = episodes.isNotEmpty() && episodes.all { episode ->
                                        episodeDownloads[episode.id?.toIntOrNull()]?.status in setOf(DesktopDownloadStatus.COMPLETED,
                                            DesktopDownloadStatus.QUEUED, DesktopDownloadStatus.DOWNLOADING)
                                    }
                                    OutlinedButton({ selectedSeason?.let { controller.downloadEpisodes(it) } },
                                        enabled = downloads != null && selectedSeason != null && !allSavedOrQueued && episodes.isNotEmpty()) {
                                        Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(8.dp)); Text("Baixar temporada")
                                    }
                                    TextButton(controller::openDownloads) { Text("Ver downloads") }
                                }
                                Text("${seasons.firstOrNull { it.first == selectedSeason }?.second.orEmpty()} · ${episodes.size} ${if (episodes.size == 1) "episódio" else "episódios"}",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.primary)
                                }
                            }
                            if (episodes.isEmpty()) item(key = "no-episodes") {
                                Text("Nenhum episódio disponível nesta temporada.", color = palette.onSurfaceVariant)
                            }
                            itemsIndexed(episodes, key = { _, episode -> "episode-${episode.id}" }) { index, episode ->
                                val interaction = rememberVisualInteraction()
                                val episodeNumStr = episode.episodeNum ?: "${index + 1}"
                                val play = {
                                    focusedEpisode = episode.id
                                    controller.catalog.playEpisode(requireNotNull(selectedSeason), episode)
                                }
                                Surface(onClick = play,
                                    modifier = (if (index == episodeFocusIndex) Modifier.focusRequester(episodesFocus) else Modifier)
                                        .focusProperties { left = seasonsFocus }
                                        .onFocusChanged { if (it.hasFocus) focusedEpisode = episode.id },
                                    interactionSource = interaction.source, shape = RoundedCornerShape(14.dp),
                                    color = if (interaction.emphasized) palette.primary.copy(alpha = .12f) else palette.surface,
                                    border = if (interaction.emphasized) BorderStroke(2.dp, palette.primary) else null) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
                                            episode.info?.movieImage?.takeIf(String::isNotBlank)?.let { image ->
                                                CatalogImage(image, images, Modifier.fillMaxSize())
                                            } ?: Box(Modifier.fillMaxSize().background(palette.surfaceVariant), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.PlayCircle, null, tint = palette.primary, modifier = Modifier.size(32.dp))
                                            }
                                            PlayerEpisodeNumber("E$episodeNumStr", palette.primary, MaterialTheme.typography.labelSmall,
                                                Modifier.align(Alignment.TopStart))
                                        }
                                        PlayerEpisodeDescription(
                                            title = episode.title ?: "Episódio $episodeNumStr",
                                            plot = episode.info?.plot ?: "",
                                            watched = false,
                                            text = palette.onSurface,
                                            secondary = palette.onSurfaceVariant,
                                            titleStyle = MaterialTheme.typography.titleMedium,
                                            bodyStyle = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val savedEpisode = episodeDownloads[episode.id?.toIntOrNull()]
                                        val savedOrQueued = savedEpisode?.status in setOf(DesktopDownloadStatus.COMPLETED,
                                            DesktopDownloadStatus.QUEUED, DesktopDownloadStatus.DOWNLOADING)
                                        IconButton({ selectedSeason?.let { controller.downloadEpisodes(it, episode.id) } },
                                            enabled = downloads != null && !savedOrQueued) {
                                            Icon(if (savedEpisode?.status == DesktopDownloadStatus.COMPLETED) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                                when (savedEpisode?.status) {
                                                    DesktopDownloadStatus.COMPLETED -> "Episódio baixado"
                                                    DesktopDownloadStatus.DOWNLOADING -> "Baixando episódio"
                                                    DesktopDownloadStatus.QUEUED -> "Episódio na fila"
                                                    else -> "Baixar episódio"
                                                }, tint = if (savedOrQueued) palette.onSurfaceVariant else palette.primary)
                                        }
                                        IconButton(onClick = play) {
                                            Icon(Icons.Rounded.PlayArrow, "Assistir episódio", tint = palette.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (scroll.canScrollBackward || scroll.canScrollForward)
                    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd), style = desktopScrollbarStyle())
            }
        }
    }
}
