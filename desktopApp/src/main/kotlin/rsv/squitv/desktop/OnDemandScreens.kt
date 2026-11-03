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
    var search by rememberSaveable(kind, browse.category?.id) { mutableStateOf("") }
    var alphabetical by rememberSaveable(kind) { mutableStateOf(false) }
    var editingSearch by remember { mutableStateOf(false) }
    val sidebar = androidx.compose.foundation.lazy.rememberLazyListState()
    val images = rememberDesktopImages(DesktopImageKind.POSTERS)
    val hidden = preferences.hiddenCategories(accountScope, kind)
    val categoryOrder = preferences.categoryOrder(accountScope, kind)
    val categories = remember(browse.categories, hidden, categoryOrder) {
        CategoryCustomization.apply(browse.categories, categoryOrder, hidden) { it.id }
    }
    val allHidden = browse.categories.isNotEmpty() && categories.isEmpty()
    LaunchedEffect(categories, browse.category?.id, browse.categoriesLoaded, browse.loadingCategories,
        browse.itemsLoaded, browse.loading, browse.error, preferencesLoaded) {
        if (preferencesLoaded && browse.categoriesLoaded && !browse.loadingCategories && browse.error == null) {
            val selected = categories.firstOrNull { it.id == browse.category?.id }
            if (selected == null) {
                if (categories.isNotEmpty()) controller.catalog.selectCategory(categories.first())
                else if (browse.category != null) controller.catalog.clearSelection(kind)
            } else if (!browse.itemsLoaded && !browse.loading) controller.catalog.selectCategory(selected)
        }
    }
    val query = search.trim()
    var preparedItems by remember(browse.items, query, alphabetical) { mutableStateOf<List<CatalogItem>?>(null) }
    LaunchedEffect(browse.items, query, alphabetical) {
        preparedItems = withContext(Dispatchers.Default) {
            val filtered = if (query.isBlank()) browse.items else browse.items.filter {
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
    val items = if (browse.category?.id in hidden) emptyList() else preparedItems.orEmpty()
    val gridMemory = rememberContentGridState("$accountScope/${kind.name}/${browse.category?.id}/$alphabetical/${search.trim()}")
    val grid = gridMemory.scroll
    val gridFocus = remember { FocusRequester() }
    val useFocus = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val savedIndex = remember(items, gridMemory.focusedItem) { items.indexOfFirst { it.id.toString() == gridMemory.focusedItem } }
    val focusIndex = if (savedIndex >= 0) savedIndex else {
        (if (gridMemory.hasInteracted) gridMemory.focusedIndex else grid.firstVisibleItemIndex)
            .coerceIn(0, items.lastIndex.coerceAtLeast(0))
    }
    var entered by remember(gridMemory) { mutableStateOf(false) }
    LaunchedEffect(gridMemory) {
        snapshotFlow { grid.isScrollInProgress }.collect { if (it) gridMemory.hasInteracted = true }
    }
    LaunchedEffect(gridMemory, items.isNotEmpty()) {
        if (!entered && items.isNotEmpty()) {
            entered = true
            if (useFocus && !editingSearch) {
                withFrameNanos { }
                if (grid.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) grid.scrollToItem(focusIndex)
                withFrameNanos { }
                runCatching { gridFocus.requestFocus() }
            }
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DesktopPageHeading(kind.title, browse.category?.takeUnless { it.id in hidden }?.name ?: "Categorias do servidor") {
            FilterChip(alphabetical, { alphabetical = !alphabetical }, { Text("A–Z") },
                leadingIcon = { Icon(Icons.Rounded.SortByAlpha, null, Modifier.size(18.dp)) })
            OutlinedTextField(search, { search = it }, singleLine = true,
                placeholder = { Text("Buscar nesta categoria") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.width(260.dp).onFocusChanged { editingSearch = it.hasFocus }, shape = RoundedCornerShape(16.dp))
        }
        preferencesError?.let { StatusNotice(it, error = true) }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(Modifier.width(220.dp).fillMaxHeight(), shape = RoundedCornerShape(18.dp),
                color = palette.surface.copy(alpha = .55f), border = BorderStroke(1.dp, palette.outline.copy(alpha = .2f))) {
                Column(Modifier.padding(10.dp)) {
                    Text("CATEGORIAS", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    if (browse.loadingCategories) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f)) {
                        LazyColumn(state = sidebar, verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
                            items(categories, key = { it.id }) { category ->
                                val selected = browse.category?.id == category.id
                                DesktopCategoryRow(
                                    name = category.name,
                                    selected = selected,

                                    onClick = { controller.catalog.selectCategory(category) }
                                )
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
                    allHidden -> CatalogStateMessage("Todas as categorias estão ocultas", actionLabel = "Mostrar todas") {
                        store.update { it.withHiddenCategories(accountScope, kind, emptySet()) }
                    }
                    browse.loading || browse.loadingCategories || preparedItems == null -> CatalogStateMessage("Carregando catálogo…", loading = true)
                    browse.error != null -> CatalogStateMessage(browse.error) {
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
                                        gridMemory.focusedIndex = index
                                        gridMemory.hasInteracted = true
                                    } }
                                CatalogPoster(item, images, modifier) {
                                    gridMemory.focusedItem = item.id.toString()
                                    gridMemory.focusedIndex = index
                                    gridMemory.hasInteracted = true
                                    controller.catalog.openDetail(item)
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
internal fun CatalogPoster(item: CatalogItem, images: ChannelLogos, modifier: Modifier = Modifier, open: () -> Unit) {
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
            image = { CatalogImage(item.cover, images, it) },
            badges = {
                item.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { rating ->
                    rsv.squitv.core.ui.components.cards.RatingBadgeContent(rating, MaterialTheme.typography.labelLarge,
                        Modifier.align(Alignment.TopStart).padding(8.dp))
                }
            })
    }
}

@Composable
internal fun CatalogImage(url: String?, images: ChannelLogos, modifier: Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url, images) { value = images.load(url) }
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
internal fun OnDemandDetailScreen(detail: CatalogDetail, controller: DesktopController) {
    val images = rememberDesktopImages(DesktopImageKind.DETAILS)
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
            IconButton(controller.catalog::closeDetail) { Icon(Icons.Rounded.Close, "Fechar detalhes") }
        }
        detail.error?.let { error ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { StatusNotice(error, error = true) }
                TextButton(controller.catalog::retryDetail) { Text("Tentar novamente") }
            }
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
                            CatalogImage(movie?.movieImage ?: metadata?.cover ?: detail.item.cover, images,
                                Modifier.width(if (detail.kind == CatalogKind.SERIES) 112.dp else 180.dp).aspectRatio(2f / 3f))
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
                            if (detail.kind == CatalogKind.MOVIES) Button(controller.catalog::playMovie) {
                                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("ASSISTIR")
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
                                Text("${seasons.firstOrNull { it.first == selectedSeason }?.second.orEmpty()} · ${episodes.size} ${if (episodes.size == 1) "episódio" else "episódios"}",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.primary)
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
