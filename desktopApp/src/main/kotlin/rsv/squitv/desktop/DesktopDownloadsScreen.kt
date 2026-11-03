package rsv.squitv.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rsv.squitv.core.ui.components.downloads.DownloadItemContent
import rsv.squitv.shared.downloads.DownloadPresentation

@Composable
internal fun DesktopDownloadsScreen(controller: DesktopController) {
    val downloads = controller.downloads ?: return
    val desktop by controller.state.collectAsState()
    val data by downloads.state.collectAsState()
    val progress by downloads.progress.collectAsState()
    val error by downloads.error.collectAsState()
    val notice by downloads.notice.collectAsState()
    val loaded by downloads.loaded.collectAsState()
    val account = liveCategoryScope(desktop.credentials ?: desktop.savedCredentials)
    val entries = remember(data, account) { data.entries.filter { it.account == account }.sortedByDescending { it.createdAt } }
    var deleting by remember(account) { mutableStateOf<DesktopDownload?>(null) }
    val scroll = rememberLazyListState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesktopPageHeading("DOWNLOADS", "Filmes e episódios salvos neste computador")
        Text("Um arquivo por vez. Temporadas entram na fila. Fechar o app, cancelar a fila ou sair da conta interrompe os pendentes; Reiniciar começa do zero.",
            style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        error?.let { StatusNotice(it, error = true) }
        notice?.let { StatusNotice(it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!loaded) CatalogStateMessage("Carregando downloads…", loading = true)
            else if (entries.isEmpty()) CatalogStateMessage("Nenhum conteúdo salvo. Use Baixar no filme, no episódio ou na temporada.")
            else {
                LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(entries, key = { it.key }) { entry ->
                        val active = progress?.takeIf { it.key == entry.key }
                        val bytes = active?.bytes ?: entry.bytes
                        val total = active?.total ?: entry.total
                        val complete = entry.status == DesktopDownloadStatus.COMPLETED
                        val label = when {
                            active != null -> "Baixando"
                            complete -> "Concluído"
                            entry.status == DesktopDownloadStatus.QUEUED -> "Na fila"
                            entry.status == DesktopDownloadStatus.FAILED -> "Falhou"
                            else -> "Interrompido"
                        }
                        val sizes = DownloadPresentation.fileSize(bytes) + if (total > 0 && !complete) " / ${DownloadPresentation.fileSize(total)}" else ""
                        val interaction = rememberVisualInteraction()
                        Surface(onClick = { if (complete) controller.playDownload(entry.key) }, enabled = complete,
                            interactionSource = interaction.source, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp), color = palette.surface,
                            border = BorderStroke(1.dp, if (interaction.emphasized) palette.primary else palette.outline)) {
                            DownloadItemContent(entry.title.uppercase(), "$label · $sizes",
                                MaterialTheme.typography.titleMedium, MaterialTheme.typography.bodySmall,
                                palette.onSurface, palette.onSurfaceVariant, 16.dp, 20.dp,
                                thumbnail = {
                                    Surface(Modifier.width(120.dp).aspectRatio(1.77f), shape = RoundedCornerShape(8.dp),
                                        color = palette.surfaceVariant) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (active != null && total > 0) CircularProgressIndicator(
                                                progress = { (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) }, Modifier.size(36.dp))
                                            else if (active != null) CircularProgressIndicator(Modifier.size(36.dp))
                                            else Icon(if (complete) Icons.Rounded.PlayArrow else Icons.Rounded.Download,
                                                null, Modifier.size(32.dp), tint = palette.primary)
                                        }
                                    }
                                }, actions = {
                                    if (active != null) IconButton(downloads::interrupt) { Icon(Icons.Rounded.Close, "Cancelar fila de downloads") }
                                    else if (entry.status == DesktopDownloadStatus.QUEUED) Text("Na fila", style = MaterialTheme.typography.labelSmall)
                                    else if (complete) IconButton({ controller.playDownload(entry.key) }) { Icon(Icons.Rounded.PlayArrow, "Reproduzir arquivo salvo") }
                                    else TextButton({ controller.retryDownload(entry.key) }, enabled = progress == null) { Text("Reiniciar") }
                                    IconButton({ deleting = entry }) { Icon(Icons.Rounded.Delete, "Excluir download", tint = palette.error) }
                                })
                        }
                    }
                }
                if (scroll.canScrollBackward || scroll.canScrollForward)
                    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd), style = desktopScrollbarStyle())
            }
        }
    }
    deleting?.let { entry ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Excluir download?") },
            modifier = Modifier.directionalNavigation(),
            text = { Text("O arquivo salvo de “${entry.title}” será removido deste computador.") },
            confirmButton = { TextButton({ downloads.remove(entry.key); deleting = null }) { Text("Excluir") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Manter") } })
    }
}
