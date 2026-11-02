package rsv.squitv.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import rsv.squitv.shared.xtream.OnDemandPlayback
import rsv.squitv.shared.brand.AppBrand
import java.text.Collator
import java.util.Locale

/** Android MoviesScreen/SeriesScreen layout: header, provider categories, poster grid. */
@Composable
internal fun OnDemandCatalogScreen(state: OnDemandState, controller: DesktopController) {
    val browse = state.current
    val kind = state.kind
    var search by rememberSaveable(kind, browse.category?.id) { mutableStateOf("") }
    var alphabetical by rememberSaveable(kind) { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val sidebar = androidx.compose.foundation.lazy.rememberLazyListState()
    val images = remember { ChannelLogos(384, 2 * 1024 * 1024) }
    DisposableEffect(images) { onDispose { images.close() } }
    val items = remember(browse.items, search, alphabetical) {
        val filtered = if (search.isBlank()) browse.items else browse.items.filter { it.name.contains(search.trim(), true) }
        if (!alphabetical) filtered else {
            val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
            filtered.sortedWith { a, b -> collator.compare(a.name, b.name) }
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DesktopPageHeading(kind.title, browse.category?.name ?: "Categorias do servidor") {
            FilterChip(alphabetical, { alphabetical = !alphabetical }, { Text("A–Z") },
                leadingIcon = { Icon(Icons.Rounded.SortByAlpha, null, Modifier.size(18.dp)) })
            OutlinedTextField(search, { search = it }, singleLine = true,
                placeholder = { Text("Buscar nesta categoria") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.width(260.dp), shape = RoundedCornerShape(16.dp))
        }
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
                            items(browse.categories, key = { it.id }) { category ->
                                val selected = browse.category?.id == category.id
                                val interaction = rememberVisualInteraction()
                                Surface(onClick = { controller.catalog.selectCategory(category) },
                                    interactionSource = interaction.source, modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected || interaction.emphasized) palette.primary.copy(alpha = .12f) else Color.Transparent,
                                    border = if (interaction.emphasized) BorderStroke(2.dp, palette.primary) else null) {
                                    Text(category.name, Modifier.padding(14.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        color = if (selected) palette.primary else palette.onSurface,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall)
                                }
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
                    browse.loading || browse.loadingCategories -> CatalogStateMessage("Carregando catálogo…", loading = true)
                    browse.error != null -> CatalogStateMessage(browse.error) {
                        browse.category?.let(controller.catalog::selectCategory) ?: controller.catalog.refreshCategories()
                    }
                    items.isEmpty() -> CatalogStateMessage(if (search.isBlank()) "Nenhum conteúdo disponível" else "Nenhum resultado encontrado")
                    else -> {
                        LazyVerticalGrid(GridCells.Adaptive(140.dp), state = grid,
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp), contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(items, key = { it.id }) { item -> CatalogPoster(item, images) { controller.catalog.openDetail(item) } }
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
    actions: @Composable RowScope.() -> Unit = {}) {
    val drag = LocalWindowDragArea.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        drag(Modifier.weight(1f)) {
            Column {
                Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black,
                    color = palette.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

@Composable
internal fun CatalogPoster(item: CatalogItem, images: ChannelLogos, modifier: Modifier = Modifier, open: () -> Unit) {
    val interaction = rememberVisualInteraction()
    Surface(onClick = open, interactionSource = interaction.source, modifier = modifier, color = Color.Transparent,
        shape = RoundedCornerShape(AppBrand.CARD_CORNER_DP.dp),
        border = if (interaction.emphasized) BorderStroke(2.dp, palette.primary) else null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(palette.surfaceVariant)) {
                CatalogImage(item.cover, images, Modifier.fillMaxSize())
                item.rating?.takeIf { it.isNotBlank() && it != "0" }?.let {
                    Surface(Modifier.align(Alignment.TopStart).padding(6.dp), color = Color.Black.copy(alpha = .75f),
                        shape = RoundedCornerShape(6.dp)) {
                        Row(Modifier.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null, Modifier.size(12.dp), tint = Color(0xFFFFAB00))
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(item.name.uppercase(Locale.ROOT), Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp, start = 3.dp, end = 3.dp),
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (interaction.emphasized) palette.primary else palette.onSurface)
        }
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
internal fun CatalogStateMessage(message: String, loading: Boolean = false, retry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 400.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            Text(message, color = palette.onSurfaceVariant, textAlign = TextAlign.Center)
            retry?.let { Button(it) { Text("Tentar novamente") } }
        }
    }
}

/** Android details hierarchy: poster, metadata/synopsis and play; seasons with real episodes. */
@Composable
internal fun OnDemandDetailScreen(detail: CatalogDetail, controller: DesktopController) {
    val images = remember { ChannelLogos(512, 2 * 1024 * 1024) }
    DisposableEffect(images) { onDispose { images.close() } }
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
                                    val interaction = rememberVisualInteraction()
                                    Surface(onClick = {
                                        season = number
                                        scope.launch { scroll.scrollToItem(1) }
                                    }, modifier = Modifier.fillMaxWidth(), interactionSource = interaction.source,
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected || interaction.emphasized) palette.primary.copy(alpha = .12f) else Color.Transparent,
                                        border = if (interaction.emphasized) BorderStroke(2.dp, palette.primary) else null) {
                                        Text(name, Modifier.padding(16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) palette.primary else palette.onSurface)
                                    }
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
                            if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = palette.primary,
                                style = MaterialTheme.typography.bodySmall)
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
                            items(episodes, key = { "episode-${it.id}" }) { episode ->
                                val interaction = rememberVisualInteraction()
                                Surface(onClick = { controller.catalog.playEpisode(requireNotNull(selectedSeason), episode) },
                                    interactionSource = interaction.source, shape = RoundedCornerShape(14.dp),
                                    color = if (interaction.emphasized) palette.primary.copy(alpha = .12f) else palette.surface) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        episode.info?.movieImage?.takeIf(String::isNotBlank)?.let { image ->
                                            Surface(shape = RoundedCornerShape(8.dp), color = palette.surfaceVariant) {
                                                CatalogImage(image, images, Modifier.width(112.dp).aspectRatio(16f / 9f))
                                            }
                                        }
                                        Icon(Icons.Rounded.PlayCircle, null, tint = palette.primary)
                                        Column(Modifier.weight(1f)) {
                                            Text(episode.title ?: "Episódio ${episode.episodeNum.orEmpty()}", fontWeight = FontWeight.Bold)
                                            episode.info?.plot?.takeIf(String::isNotBlank)?.let {
                                                Text(it, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        episode.info?.duration?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
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
