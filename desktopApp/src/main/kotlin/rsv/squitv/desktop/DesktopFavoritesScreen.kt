package rsv.squitv.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import rsv.squitv.ui.content.components.rememberContentGridState

@Composable
internal fun DesktopFavoritesScreen(controller: DesktopController, store: DesktopPreferencesStore) {
    val settings by store.state.collectAsState()
    val loaded by store.loaded.collectAsState()
    val error by store.error.collectAsState()
    val state by controller.favorites.state.collectAsState()
    val account = controller.state.value.credentials ?: return
    val accountKey = liveCategoryScope(account) ?: return
    var type by rememberSaveable(accountKey) { mutableStateOf(DesktopSearchType.ALL) }
    var query by rememberSaveable(accountKey) { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val images = rememberDesktopImages(DesktopImageKind.POSTERS)
    LaunchedEffect(account, settings, loaded) { if (loaded) controller.favorites.open(account, settings) }
    DisposableEffect(controller) { onDispose { controller.favorites.leave() } }
    val items = remember(state.results, type, query) { state.results.filter {
        (type == DesktopSearchType.ALL || it.type == type) && it.name.contains(query.trim(), true)
    } }
    val memory = rememberContentGridState("$accountKey/FAVORITES/$type/${query.trim()}")
    val initialFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    val focusIndex = items.indexOfFirst { it.key == memory.focusedItem }.takeIf { it >= 0 }
        ?: memory.focusedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    LaunchedEffect(items.map { it.key }) {
        if (keyboard && !editing && items.isNotEmpty()) {
            if (memory.scroll.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) memory.scroll.scrollToItem(focusIndex)
            withFrameNanos { }
            runCatching { initialFocus.requestFocus() }
        } else if (keyboard && !editing && !state.loading) runCatching { filterFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesktopPageHeading("FAVORITOS", "${items.size} itens · canais, filmes e séries") {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Buscar nos favoritos") },
                singleLine = true, modifier = Modifier.width(250.dp).onFocusChanged { editing = it.hasFocus })
            TextButton({ controller.favorites.open(account, settings, force = true) }, enabled = loaded && !state.loading) { Text("Atualizar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopSearchType.entries.forEach { choice -> FilterChip(type == choice, { type = choice }, { Text(choice.label) },
                modifier = if (choice == DesktopSearchType.ALL) Modifier.focusRequester(filterFocus) else Modifier) }
        }
        error?.let { StatusNotice(it, error = true) }
        state.errors.values.forEach { StatusNotice(it, error = true) }
        if (state.loading || !loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) CatalogStateMessage(if (state.loading || !loaded) "Carregando favoritos…"
                else if (state.errors.isNotEmpty()) "Não foi possível carregar todos os favoritos. Use Atualizar."
                else "Nenhum favorito corresponde a esta seleção.", loading = state.loading || !loaded)
            else {
                LazyVerticalGrid(GridCells.Adaptive(155.dp), state = memory.scroll,
                    modifier = Modifier.fillMaxSize().padding(end = 16.dp), contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                        val modifier = (if (index == focusIndex) Modifier.focusRequester(initialFocus) else Modifier)
                            .onFocusChanged { if (it.hasFocus) { memory.focusedItem = item.key; memory.focusedIndex = index; memory.hasInteracted = true } }
                        val open = { controller.openFavoriteResult(item, items) }
                        val channel = item.channel
                        if (channel != null) DesktopChannelCard(channel, images, modifier,
                            isFavorite = channel.isFavorite(settings.favoriteLiveChannelsByAccount[accountKey].orEmpty()),
                            favoriteEnabled = loaded, onToggleFavorite = { store.update {
                                it.toggleLiveFavorite(accountKey, channel.id, channel.versions.map { it.id })
                            } }, play = open)
                        else item.catalogItem?.let { catalogItem ->
                            val kind = if (item.type == DesktopSearchType.SERIES) CatalogKind.SERIES else CatalogKind.MOVIES
                            CatalogPoster(catalogItem, images, modifier,
                                isFavorite = settings.isCatalogFavorite(accountKey, kind, catalogItem.id), favoriteEnabled = loaded,
                                onToggleFavorite = { store.update { it.toggleCatalogFavorite(accountKey, kind, catalogItem.id) } }, open = open)
                        }
                    }
                }
                if (memory.scroll.canScrollBackward || memory.scroll.canScrollForward)
                    VerticalScrollbar(rememberScrollbarAdapter(memory.scroll), Modifier.align(Alignment.CenterEnd), style = desktopScrollbarStyle())
            }
        }
    }
}
