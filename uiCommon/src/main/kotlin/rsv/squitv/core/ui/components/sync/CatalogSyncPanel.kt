package rsv.squitv.core.ui.components.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rsv.squitv.core.domain.state.AppSyncStatus

data class SyncStage(val label: String, val status: AppSyncStatus, val text: String)

@Composable
fun CatalogSyncPanel(title: String, subtitle: String, stages: List<SyncStage>, running: Boolean,
    error: String?, cancelled: Boolean = false, onRetry: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null, onContinue: (() -> Unit)? = null) {
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(running, error, cancelled, onContinue != null) {
        if (!running && (onRetry != null || onContinue != null)) {
            withFrameNanos { }
            try { actionFocus.requestFocus() } catch (_: IllegalStateException) { }
        }
    }
    val active = stages.filter { it.status != AppSyncStatus.Skipped }
    val done = active.count { it.status is AppSyncStatus.Done || it.status == AppSyncStatus.Success ||
        it.status == AppSyncStatus.OfflineMode }
    val fraction = if (active.isEmpty()) 0f else done.toFloat() / active.size
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val compactHeight = maxHeight < 600.dp
        val columns = if (maxWidth >= 700.dp) 2 else 1
        Column(Modifier.widthIn(max = 1040.dp).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(if (compactHeight) 16.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 8.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudSync, null, Modifier.size(if (compactHeight) 36.dp else 48.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            stages.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { stage -> SyncStageCard(stage, Modifier.weight(1f)) }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text("Etapas concluídas: $done/${active.size}", style = MaterialTheme.typography.bodySmall)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            else if (cancelled) Text("Sincronização cancelada. Você pode tentar novamente.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (running && onCancel != null) OutlinedButton(onClick = onCancel) { Text("Cancelar") }
                if (!running && onRetry != null) Button(onClick = onRetry,
                    modifier = Modifier.focusRequester(actionFocus)) { Text("Tentar novamente") }
                if (!running && onContinue != null) OutlinedButton(onClick = onContinue,
                    modifier = if (onRetry == null) Modifier.focusRequester(actionFocus) else Modifier) {
                    Text(if (error != null || cancelled) "Usar o catálogo disponível" else "Continuar")
                }
            }
        }
    }
}

@Composable
fun SyncStageCard(stage: SyncStage, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val loading = stage.status == AppSyncStatus.Fetching || stage.status == AppSyncStatus.Syncing
    val complete = stage.status is AppSyncStatus.Done || stage.status == AppSyncStatus.Success ||
        stage.status == AppSyncStatus.OfflineMode
    val failed = stage.status is AppSyncStatus.Error
    val tint = when { failed -> colors.error; complete -> colors.secondary; loading -> colors.primary; else -> colors.onSurfaceVariant }
    Surface(modifier, shape = MaterialTheme.shapes.medium,
        color = if (loading) colors.primary.copy(alpha = .08f) else colors.surfaceVariant.copy(alpha = .45f),
        border = BorderStroke(1.dp, if (loading) colors.primary.copy(alpha = .4f) else colors.outlineVariant)) {
        Row(Modifier.heightIn(min = 72.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(when { complete -> Icons.Rounded.CheckCircle; failed -> Icons.Rounded.Error
                loading -> Icons.Rounded.CloudSync; else -> Icons.Rounded.RadioButtonUnchecked },
                null, Modifier.size(28.dp), tint = tint)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stage.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(stage.text, color = tint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
