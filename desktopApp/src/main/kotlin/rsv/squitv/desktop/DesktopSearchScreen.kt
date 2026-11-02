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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** SearchScreen Android: title input, four content filters and a poster/logo grid. */
@Composable
internal fun DesktopSearchScreen(controller: DesktopController) {
    val search = controller.search
    val state by search.state.collectAsState()
    val grid = rememberLazyGridState()
    val inputFocus = remember { FocusRequester() }
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    var restoringFocus by remember { mutableStateOf(lastFocused != null) }
    val images = remember { ChannelLogos(384, 2 * 1024 * 1024) }
    DisposableEffect(images) { onDispose { images.close() } }
    LaunchedEffect(Unit) {
        search.resume()
        withFrameNanos { }
        if (lastFocused == null) inputFocus.requestFocus()
    }
    LaunchedEffect(state.query, state.type) {
        // On re-entry retain the saved grid; only changed input starts from the first result.
        if (lastFocused == null) grid.scrollToItem(0)
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
                            if (item.channel != null) SearchChannelCard(item, images, modifier, open)
                            else item.catalogItem?.let { CatalogPoster(it, images, modifier, open) }
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

@Composable
private fun SearchChannelCard(item: DesktopSearchItem, images: ChannelLogos, modifier: Modifier, open: () -> Unit) {
    val interaction = rememberVisualInteraction()
    Surface(onClick = open, interactionSource = interaction.source, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), color = palette.surface,
        border = if (interaction.emphasized) BorderStroke(2.dp, palette.primary) else BorderStroke(1.dp, Color.White.copy(alpha = .06f))) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                ChannelLogo(item.channel?.iconUrl, images, Modifier.fillMaxSize().padding(16.dp))
            }
            Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = if (interaction.emphasized) palette.primary else palette.onSurface)
        }
    }
}
