package rsv.squitv.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal fun DesktopLiveNotifications(diagnostics: LivePlaybackDiagnostics) {
    val entries by diagnostics.entries.collectAsState()
    val storageError by diagnostics.error.collectAsState()
    var actionNotice by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LaunchedEffect(diagnostics) { diagnostics.prune() }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesktopPageHeading("NOTIFICAÇÕES", "Diagnóstico de reprodução ao vivo") {
            OutlinedButton(onClick = {
                val report = buildString {
                    appendLine("Squi TV — diagnóstico de reprodução ao vivo (Windows)")
                    entries.forEach { appendLine(it.display()) }
                }
                actionNotice = runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(report), null)
                    "Diagnóstico copiado."
                }.getOrElse { "Não foi possível copiar o diagnóstico." }
            }, enabled = entries.isNotEmpty()) { Text("Copiar diagnóstico") }
            TextButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty()) { Text("Limpar") }
        }
        Text("Até 200 eventos das últimas 72 horas. O diagnóstico fica neste PC e não inclui endereços de transmissão ou credenciais.",
            style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        storageError?.let { StatusNotice(it, error = true) }
        actionNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.primary) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (entries.isEmpty()) {
                CatalogStateMessage("Nenhum evento registrado. Abra um canal ao vivo e volte aqui para ver o diagnóstico.")
            } else {
                LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(entries.asReversed()) { entry ->
                        Surface(color = palette.surface) {
                            Text(entry.display(), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (scroll.canScrollBackward || scroll.canScrollForward) {
                    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd),
                        style = desktopScrollbarStyle())
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        modifier = Modifier.directionalNavigation(),
        title = { Text("Limpar diagnóstico?") },
        text = { Text("Remove os eventos de reprodução ao vivo salvos neste aplicativo.") },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        confirmButton = {
            TextButton(onClick = {
                diagnostics.clear()
                actionNotice = null
                confirmClear = false
            }) { Text("Limpar") }
        }
    )
}
