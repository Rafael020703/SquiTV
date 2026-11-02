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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rsv.squitv.shared.brand.AppBrand
import rsv.squitv.shared.xtream.LiveChannel
import java.util.Locale

/** Desktop adaptation of Android AppHeader, AppSidebar and ChannelCard; same data/actions. */
@Composable
internal fun LiveCatalogScreen(state: DesktopState, controller: DesktopController, modifier: Modifier) {
    val logos = remember { ChannelLogos() }
    DisposableEffect(logos) { onDispose { logos.close() } }
    var search by rememberSaveable(state.selectedCategory?.id) { mutableStateOf("") }
    val categoriesState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val channels = remember(state.channels, search) {
        if (search.isBlank()) state.channels else state.channels.filter { it.name.contains(search.trim(), ignoreCase = true) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 920.dp && state.selectedCategory != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CatalogHeading(state.selectedCategory.name, Modifier.weight(1f))
                    }
                    CatalogSearch(search, { search = it }, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    CatalogHeading(state.selectedCategory?.name, Modifier.weight(1f))
                    if (state.selectedCategory != null)
                        CatalogSearch(search, { search = it }, Modifier.width(300.dp))
                }
            }
        }
        state.notice?.let { StatusNotice(it) }
        state.error?.let { StatusNotice(it, error = true) }
        BoxWithConstraints(Modifier.weight(1f)) {
        val sidebarWidth = if (maxWidth < 1000.dp) 220.dp else AppBrand.SIDEBAR_WIDTH_DP.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Surface(Modifier.width(sidebarWidth).fillMaxHeight(), shape = RoundedCornerShape(18.dp),
                color = palette.surface.copy(alpha = .55f), border = BorderStroke(1.dp, palette.outline.copy(alpha = .2f))) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("CATEGORIAS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = palette.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(if (state.busy) "…" else state.categories.size.toString(),
                            style = MaterialTheme.typography.labelSmall, color = palette.primary)
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!state.busy && state.categories.isEmpty()) {
                        Text("Nenhuma categoria disponível.", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp))
                        TextButton(onClick = {
                            state.credentials?.let { controller.connect(it.baseUrl, it.username, it.password, state.savedCredentials != null) }
                        }) { Text("Tentar novamente") }
                    }
                    Box(Modifier.weight(1f)) {
                    LazyColumn(state = categoriesState, modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(state.categories, key = { it.id }) { category ->
                            val selected = category.id == state.selectedCategory?.id
                            val interaction = rememberVisualInteraction()
                            Surface(onClick = { controller.loadChannels(category) }, modifier = Modifier.fillMaxWidth(),
                                interactionSource = interaction.source,
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected || interaction.emphasized) palette.primary.copy(alpha = .12f) else Color.Transparent,
                                border = when {
                                    interaction.emphasized -> BorderStroke(2.dp, palette.primary)
                                    selected -> BorderStroke(1.dp, palette.primary.copy(alpha = .45f))
                                    else -> null
                                }) {
                                Text(category.name, modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) palette.primary else palette.onSurface,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
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
                    state.loadingChannels -> CatalogMessage("Carregando canais…", loading = true, modifier = Modifier.weight(1f))
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
                            items(channels, key = { it.id }) { channel ->
                                DesktopChannelCard(channel, logos) { controller.play(channel, channels) }
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
    Column {
        Text("AO VIVO", color = palette.primary, fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineLarge)
        Text((category ?: "Categorias").uppercase(Locale.ROOT), color = palette.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    }
}

@Composable
private fun CatalogSearch(search: String, onSearch: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(search, onSearch,
        placeholder = { Text("Buscar nesta categoria", style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)) },
        trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }) {
            Icon(Icons.Rounded.Close, "Limpar busca", Modifier.size(18.dp))
        } }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = palette.outline.copy(alpha = .4f)))
}

@Composable
private fun DesktopChannelCard(channel: LiveChannel, logos: ChannelLogos, play: () -> Unit) {
    val interaction = rememberVisualInteraction()
    Surface(onClick = play, modifier = Modifier.fillMaxWidth().aspectRatio(AppBrand.CHANNEL_ASPECT_RATIO),
        interactionSource = interaction.source, shape = RoundedCornerShape(AppBrand.CARD_CORNER_DP.dp), color = palette.surface,
        border = if (interaction.emphasized) BorderStroke(AppBrand.FOCUS_BORDER_DP.dp, palette.primary)
            else BorderStroke(1.dp, palette.outline.copy(alpha = .22f))) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF141A26), Color(0xFF090B11))))) {
            Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center) {
                ChannelLogo(channel.iconUrl, logos, Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))
            Text(channel.name.uppercase(Locale.ROOT), modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
        }
    }
}

@Composable
private fun CatalogMessage(message: String, loading: Boolean = false, error: Boolean = false,
    modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
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
            if (onRetry != null) Button(onClick = onRetry) { Text("Tentar novamente") }
        }
    }
}
