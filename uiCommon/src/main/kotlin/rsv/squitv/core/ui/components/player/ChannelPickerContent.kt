package rsv.squitv.core.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rsv.squitv.shared.catalog.LiveCatalogView
import rsv.squitv.shared.xtream.LiveChannel

@Stable
class ChannelPickerState {
    var categoryId by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    fun back(): Boolean = when {
        query.isNotEmpty() -> { query = ""; true }
        categoryId != null -> { categoryId = null; true }
        else -> false
    }
}

@Composable
fun ChannelPickerContent(view: LiveCatalogView, state: ChannelPickerState, loading: Boolean,
    error: String?, retry: () -> Unit, close: () -> Unit, choose: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier) {
    val selected = view.categories.firstOrNull { it.id == state.categoryId }
    LaunchedEffect(view.categories, loading) {
        if (!loading && selected == null && state.categoryId != null) state.categoryId = null
    }
    val query = state.query.trim()
    val categories = remember(view.categories, query) { view.categories.filter { query.isEmpty() || it.name.contains(query, true) } }
    val channels = remember(view, state.categoryId, query) {
        val source = state.categoryId?.let(view::inCategory) ?: if (query.isNotEmpty()) view.channels else emptyList()
        source.filter { channel -> query.isEmpty() || channel.name.contains(query, true) || channel.versions.any { it.name.contains(query, true) } }
    }
    val list = rememberLazyListState()
    val firstRow = remember { FocusRequester() }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    var editing by remember { mutableStateOf(false) }
    val firstKey = if (state.categoryId == null) categories.firstOrNull()?.id else channels.firstOrNull()?.stableKey
    LaunchedEffect(state.categoryId, query) { list.scrollToItem(0) }
    LaunchedEffect(state.categoryId, firstKey, loading) {
        if (keyboard && !editing && query.isEmpty() && !loading && firstKey != null) {
            withFrameNanos { }
            runCatching { firstRow.requestFocus() }
        }
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(selected?.name ?: "ESCOLHER CATEGORIA", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(close) { Icon(Icons.Rounded.Close, "Fechar seleção") }
        }
        OutlinedTextField(state.query, { state.query = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { editing = it.hasFocus },
            placeholder = { Text(if (selected == null) "Buscar categoria ou canal" else "Buscar canal nesta categoria") })
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(retry) { Text("Tentar novamente") } }
        LazyColumn(Modifier.weight(1f), state = list) {
            if (state.categoryId == null) items(categories, key = { "category:${it.id}" }) { category ->
                ListItem(headlineContent = { Text(category.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Text(category.count.toString()) },
                    modifier = (if (category == categories.firstOrNull()) Modifier.focusRequester(firstRow) else Modifier).clickable {
                        state.categoryId = category.id; state.query = ""
                    })
            }
            items(channels, key = { "channel:${it.stableKey}" }) { channel ->
                ListItem(headlineContent = { Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { if (channel.versions.isNotEmpty()) Text("${channel.versions.size} versões · endereço conforme qualidade preferida") },
                    modifier = (if (state.categoryId != null && channel == channels.firstOrNull()) Modifier.focusRequester(firstRow) else Modifier)
                        .clickable { choose(channel) })
            }
        }
        if (!loading && error == null && channels.isEmpty() && (state.categoryId != null || categories.isEmpty()))
            Text("Nenhum conteúdo nesta seleção.", style = MaterialTheme.typography.bodySmall)
    }
}
