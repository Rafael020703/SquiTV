package rsv.squitv.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rsv.squitv.domain.model.CategoryMode
import rsv.squitv.domain.model.PreparationSpeed

@Composable
internal fun CatalogModeSelector(mode: CategoryMode, enabled: Boolean = true, choose: (CategoryMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton({ expanded = true }, enabled = enabled) {
            Text(if (mode == CategoryMode.PROVIDER) "Servidor" else "Filtros")
            Icon(Icons.Rounded.ArrowDropDown, "Modo de categorias", Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            CategoryMode.entries.forEach { value -> DropdownMenuItem(
                text = { Text(if (value == CategoryMode.PROVIDER) "Categorias do servidor" else "Filtros por gênero") },
                onClick = { expanded = false; choose(value) },
                trailingIcon = { RadioButton(mode == value, null) }) }
        }
    }
}

/** First choice lives in the existing surface; no new native window can change fullscreen. */
@Composable
internal fun CatalogFirstChoice(account: String, kind: CatalogKind, store: DesktopPreferencesStore) {
    var selected by remember(account, kind) { mutableStateOf(CategoryMode.PROVIDER) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save(mode: CategoryMode) {
        if (saving) return
        saving = true
        scope.launch {
            try { store.updateAndAwait { it.withCatalogMode(account, kind, mode) } }
            finally { saving = false }
        }
    }
    Surface(color = palette.surfaceVariant, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, palette.primary)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Como organizar ${if (kind == CatalogKind.MOVIES) "os filmes" else "as séries"}?", style = MaterialTheme.typography.titleMedium)
            Text("Você pode alterar depois nas Configurações. Conteúdos sem gênero permanecem nas categorias do servidor.",
                style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected == CategoryMode.PROVIDER, { selected = CategoryMode.PROVIDER }, { Text("Categorias do servidor") }, enabled = !saving)
                FilterChip(selected == CategoryMode.GENRE, { selected = CategoryMode.GENRE }, { Text("Filtros por gênero") }, enabled = !saving)
                Button({ save(selected) }, enabled = !saving) { Text(if (saving) "Salvando…" else "Confirmar") }
                TextButton({ save(CategoryMode.PROVIDER) }, enabled = !saving) { Text("Usar padrão") }
            }
        }
    }
}

@Composable
internal fun CatalogPreparationPanel(state: DesktopPreparationState, preparation: DesktopCatalogPreparation, refresh: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(color = palette.surface.copy(alpha = .7f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val percent = rsv.squitv.shared.catalog.PreparationProgress.percent(state.total, state.done)
                Text(if (state.serverManaged) "Catálogo no servidor · $percent%" else if (state.running) "Preparando catálogo · $percent%" else "Catálogo · $percent% · ${state.done}/${state.total} preparados",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
                TextButton({ expanded = !expanded }) { Text(if (expanded) "Recolher" else "Detalhes") }
            }
            state.error?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
            if (expanded) {
                rsv.squitv.core.ui.components.common.PreparationProgressSummary(
                    state.total, state.done, state.estimatedRemainingMs, state.paused,
                    state.failed, palette.primary, palette.onSurfaceVariant)
                Text(if (state.serverManaged) "Gêneros disponíveis neste dispositivo: ${state.withGenre} · Falhas no servidor: ${state.failed}"
                    else "Com gênero: ${state.withGenre} · Sem gênero: ${state.noData} · Falhas: ${state.failed}",
                    style = MaterialTheme.typography.bodySmall)
                state.message?.let { Text(it, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!state.serverManaged) TextButton({ preparation.setPaused(!state.paused) }) { Text(if (state.paused) "Retomar" else "Pausar") }
                    if (!state.serverManaged) PreparationSpeed.entries.forEach { speed ->
                        FilterChip(state.speed == speed, { preparation.setSpeed(speed) }, { Text(when (speed) {
                            PreparationSpeed.SLOW -> "Lento"; PreparationSpeed.NORMAL -> "Normal"; PreparationSpeed.FAST -> "Rápido"
                        }) })
                    }
                    if (!state.serverManaged) TextButton(preparation::retryFailures, enabled = state.failed > 0) { Text("Tentar falhas novamente") }
                    TextButton(refresh) { Text("Atualizar listas") }
                }
            }
        }
    }
}
