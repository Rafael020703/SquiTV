package rsv.squitv.core.ui.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rsv.squitv.shared.network.AppDns
import rsv.squitv.shared.network.AppDnsMode
import rsv.squitv.shared.network.AppDnsState

@Composable
fun AppDnsPanel() {
    val state by AppDns.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { AppDns.measure() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val compact = maxWidth < 600.dp
        Column(Modifier.widthIn(max = 960.dp).fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("DNS DO APLICATIVO", style = MaterialTheme.typography.headlineSmall)
            Text("Google e Cloudflare via DNS sobre HTTPS, somente no Squi TV. Não altera o DNS do aparelho ou de outros aplicativos.",
                style = MaterialTheme.typography.bodyMedium)
            val active = if (state.mode == AppDnsMode.SYSTEM) "DNS do aplicativo desativado"
                else "Ativo: ${state.mode.label}"
            val provider = if (state.mode == AppDnsMode.AUTO)
                " · ${state.fastest?.label ?: if (state.measuring) "medindo" else "tentará os dois provedores"}" else ""
            Text(active + provider, style = MaterialTheme.typography.titleMedium)
            if (state.measuring || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val choose: (AppDnsMode) -> Unit = { mode -> scope.launch { AppDns.select(mode) } }
            if (compact) {
                AppDnsMode.entries.forEach { mode -> AppDnsChoice(mode, state, choose, Modifier.fillMaxWidth()) }
            } else {
                AppDnsMode.entries.chunked(2).forEach { modes ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        modes.forEach { mode -> AppDnsChoice(mode, state, choose, Modifier.weight(1f)) }
                    }
                }
            }
            TextButton(enabled = !state.measuring && !state.saving,
                onClick = { scope.launch { AppDns.measure() } }) { Text("Medir novamente") }
            Text("O tempo mede o DNS nesta conexão, não o servidor IPTV. A escolha vale para novas conexões, sem interromper uma reprodução aberta. Nomes locais continuam no DNS do aparelho.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AppDnsChoice(mode: AppDnsMode, state: AppDnsState,
    choose: (AppDnsMode) -> Unit, modifier: Modifier) {
    val detail = when (mode) {
        AppDnsMode.SYSTEM -> "Desliga o DNS próprio para comparar a conexão com o DNS normal do aparelho."
        AppDnsMode.AUTO -> "Escolhe o menor tempo medido e tenta o outro provedor se falhar."
        else -> if (mode !in state.milliseconds) "Ainda não medido" else
            state.milliseconds[mode]?.let { "$it ms · média de duas consultas" } ?: "Não respondeu à medição"
    }
    OutlinedButton(enabled = !state.saving, modifier = modifier.heightIn(min = 88.dp)
        .semantics { selected = state.mode == mode }, onClick = { choose(mode) }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text((if (state.mode == mode) "✓ " else "") + mode.label)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
