package rsv.squitv.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** SearchScreen Android: title input, four content filters and a poster/logo grid. */
@Composable
internal fun DesktopSearchScreen(controller: DesktopController, store: DesktopPreferencesStore) {
    val search = controller.search
    val state by search.state.collectAsState()
    val desktop by controller.state.collectAsState()
    val preferences by store.state.collectAsState()
    val preferencesLoaded by store.loaded.collectAsState()
    val preferencesError by store.error.collectAsState()
    val accountScope = remember(desktop.credentials) { liveCategoryScope(desktop.credentials) }
    val favoriteIds = preferences.favoriteLiveChannelsByAccount[accountScope].orEmpty()
    val grid = rememberLazyGridState()
    val inputFocus = remember { FocusRequester() }
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    var restoringFocus by remember { mutableStateOf(lastFocused != null) }
    val images = rememberDesktopImages(DesktopImageKind.POSTERS)
    LaunchedEffect(Unit) {
        search.resume()
        withFrameNanos { }
        if (lastFocused == null) inputFocus.requestFocus()
    }
    LaunchedEffect(state.query, state.type) {
        // On re-entry retain the saved grid; only changed input starts from the first result.
        if (lastFocused == null) grid.scrollToItem(0)
    }
    LaunchedEffect(state.loading, state.results, restoringFocus) {
        if (restoringFocus && !state.loading) {
            val index = state.results.indexOfFirst { it.key == lastFocused }
            if (index < 0) {
                // Grouping may replace an individual provider ID with a stable family key.
                lastFocused = null
                restoringFocus = false
                withFrameNanos { }
                inputFocus.requestFocus()
            } else if (grid.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                grid.scrollToItem(index)
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DesktopPageHeading("BUSCAR", "Canais, filmes e séries")
        OutlinedTextField(state.query, {
            lastFocused = null; restoringFocus = false; search.query(it)
        }, modifier = Modifier.fillMaxWidth().focusRequester(inputFocus), singleLine = true,
            placeholder = { Text("O que você quer assistir?") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton({
                    lastFocused = null; restoringFocus = false; search.query(""); inputFocus.requestFocus()
                }) { Icon(Icons.Rounded.Close, "Limpar busca") }
            }, shape = RoundedCornerShape(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DesktopSearchType.entries.forEach { type ->
                FilterChip(state.type == type, {
                    lastFocused = null; restoringFocus = false; search.filter(type)
                }, { Text(type.label) })
            }
            Spacer(Modifier.weight(1f))
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            if (state.results.isNotEmpty()) Text(
                if (state.totalMatches > state.results.size) "${state.results.size} de ${state.totalMatches} · refine a busca"
                else "${state.totalMatches} resultados",
                style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
        }
        preferencesError?.let { StatusNotice(it, error = true) }
        if (state.errors.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { StatusNotice(state.errors.values.joinToString(" "), error = true) }
                TextButton(search::retry, enabled = !state.loading) { Text("Tentar novamente") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.results.isEmpty()) {
                CatalogStateMessage(when {
                    state.query.isBlank() -> "Encontre o que assistir nos seus catálogos"
                    state.loading -> "Buscando nos catálogos…"
                    state.errors.isNotEmpty() -> "A busca não pôde ser concluída"
                    else -> "Nenhum resultado encontrado"
                }, loading = state.loading)
            } else {
                LazyVerticalGrid(GridCells.Adaptive(if (state.type == DesktopSearchType.LIVE) 175.dp else 150.dp),
                    state = grid, modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                    contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(state.results, key = { it.key }) { item ->
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(restoringFocus) {
                            if (restoringFocus && lastFocused == item.key) {
                                withFrameNanos { }
                                focus.requestFocus()
                                restoringFocus = false
                            }
                        }
                        val modifier = Modifier.focusRequester(focus).onFocusChanged {
                            if (it.isFocused) lastFocused = item.key
                        }
                        Column {
                            val open = { lastFocused = item.key; controller.openSearchResult(item) }
                            if (item.channel != null) DesktopChannelCard(item.channel, images, modifier,
                                isFavorite = item.channel.isFavorite(favoriteIds), favoriteEnabled = preferencesLoaded && accountScope != null,
                                onToggleFavorite = { accountScope?.let { account -> store.update {
                                    it.toggleLiveFavorite(account, item.channel.id, item.channel.versions.map { it.id })
                                } } }, play = open)
                            else item.catalogItem?.let { catalogItem ->
                                val kind = when (item.type) {
                                    DesktopSearchType.MOVIES -> CatalogKind.MOVIES
                                    DesktopSearchType.SERIES -> CatalogKind.SERIES
                                    else -> null
                                }
                                CatalogPoster(catalogItem, images, modifier,
                                    isFavorite = kind != null && preferences.isCatalogFavorite(accountScope, kind, catalogItem.id),
                                    favoriteEnabled = preferencesLoaded && accountScope != null && kind != null,
                                    onToggleFavorite = kind?.let { selectedKind -> {
                                        accountScope?.let { account -> store.update { it.toggleCatalogFavorite(account, selectedKind, catalogItem.id) } }
                                    } }, open = open)
                            }
                            if (state.type == DesktopSearchType.ALL) Text(item.type.label,
                                Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
                                color = palette.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (grid.canScrollBackward || grid.canScrollForward)
                    VerticalScrollbar(rememberScrollbarAdapter(grid), Modifier.align(Alignment.CenterEnd),
                        style = desktopScrollbarStyle())
            }
        }
    }
}
