package rsv.squitv.core.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import rsv.squitv.shared.catalog.PreparationProgress

@Composable
fun PreparationProgressSummary(total: Int, done: Int, remainingMs: Long?, paused: Boolean,
    failures: Int, primary: Color, secondary: Color, modifier: Modifier = Modifier) {
    val percent = PreparationProgress.percent(total, done)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$percent% · $done/$total preparados", style = MaterialTheme.typography.labelMedium, color = primary)
        LinearProgressIndicator(progress = { if (total > 0) done.toFloat().div(total).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth(), color = primary)
        Text(when {
            total > 0 && done >= total -> "Preparação concluída"
            paused -> "Estimativa suspensa durante a pausa"
            failures > 0 -> "Estimativa indisponível enquanto há falhas pendentes"
            remainingMs != null -> "Tempo restante estimado: ~ ${PreparationProgress.duration(remainingMs)}"
            else -> "Calculando tempo restante com base no progresso…"
        }, style = MaterialTheme.typography.bodySmall, color = secondary)
    }
}
