package rsv.squitv.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DesktopSetting(val label: String, val icon: ImageVector) {
    UPDATES("Atualizações", Icons.Rounded.SystemUpdate),
    ACCOUNT("Conta", Icons.Rounded.ManageAccounts),
    AUTO_PLAY("Auto-reprodução de Episódios", Icons.AutoMirrored.Rounded.PlaylistPlay),
    DNS("Testador de DNS", Icons.Rounded.Dns),
    ZOOM("Zoom da Interface", Icons.Rounded.ZoomIn),
    SIGN_OUT("Sair da conta", Icons.AutoMirrored.Rounded.Logout)
}

/** Android SettingsScreen grid style; every displayed option has a Windows implementation. */
@Composable
internal fun DesktopSettingsScreen(state: DesktopState, controller: DesktopController, store: DesktopPreferencesStore) {
    val preferences by store.state.collectAsState()
    val error by store.error.collectAsState()
    val loaded by store.loaded.collectAsState()
    var selected by rememberSaveable { mutableStateOf<DesktopSetting?>(null) }
    val categories = remember {
        val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
        DesktopSetting.entries.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesktopPageHeading("CONFIGURAÇÕES", "Gerencie sua experiência premium")
        error?.let { StatusNotice(it, error = true) }
        if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyVerticalGrid(GridCells.Adaptive(165.dp), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(categories, key = { it.name }) { category ->
                val interaction = rememberVisualInteraction()
                val base = if (category == DesktopSetting.SIGN_OUT) palette.error else palette.primary
                Surface(onClick = {
                    when (category) {
                        DesktopSetting.SIGN_OUT -> controller.disconnect()
                        DesktopSetting.UPDATES -> controller.openUpdates()
                        else -> selected = category
                    }
                }, enabled = loaded, interactionSource = interaction.source,
                    modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(28.dp),
                    color = if (interaction.emphasized) base.copy(alpha = .18f) else Color(0xFF0D1322).copy(alpha = .82f),
                    border = BorderStroke(if (interaction.emphasized) 2.dp else 1.dp,
                        if (interaction.emphasized) base else palette.outline.copy(alpha = .22f))) {
                    Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(48.dp).background(if (interaction.emphasized) base.copy(alpha = .25f)
                            else palette.primary.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(category.icon, null, Modifier.size(32.dp),
                                tint = if (interaction.emphasized) base else palette.onSurface)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(category.label.uppercase(Locale.ROOT), fontSize = 11.sp, letterSpacing = 1.sp,
                            fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2,
                            color = if (interaction.emphasized) base else palette.onSurface)
                    }
                }
            }
        }
    }
    selected?.let { category ->
        AlertDialog(onDismissRequest = { selected = null },
            title = { Text(category.label) }, confirmButton = { TextButton({ selected = null }) { Text("Concluir") } },
            text = {
                Column(Modifier.widthIn(min = 320.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (category) {
                        DesktopSetting.ACCOUNT -> {
                            state.account?.let { account ->
                                AccountLine("Usuário", account.username)
                                AccountLine("Status", account.status)
                                AccountLine("Validade", account.expiresAt?.let {
                                    Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                                } ?: "Não informada")
                                account.activeConnections?.let { AccountLine("Conexões ativas", it.toString()) }
                                account.maxConnections?.let { AccountLine("Limite de conexões", it.toString()) }
                                account.timezone?.let { AccountLine("Fuso do servidor", it) }
                            }
                        }
                        DesktopSetting.AUTO_PLAY -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Reproduzir o próximo episódio automaticamente", Modifier.weight(1f))
                                Switch(preferences.autoPlayEpisodes, { enabled -> store.update { it.copy(autoPlayEpisodes = enabled) } })
                            }
                        }
                        DesktopSetting.ZOOM -> {
                            Text("Tamanho dos elementos: ${(preferences.interfaceScale * 100).toInt()}%")
                            var scale by remember(preferences.interfaceScale) { mutableFloatStateOf(preferences.interfaceScale) }
                            Slider(scale, { scale = it }, valueRange = .8f..1.25f, steps = 8,
                                onValueChangeFinished = { store.update { it.copy(interfaceScale = scale) } })
                            TextButton({ store.update { it.copy(interfaceScale = 1f) } }) { Text("Restaurar 100%") }
                        }
                        DesktopSetting.DNS -> {
                            controller.availableDns.forEach { address ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(java.net.URI(address).host, style = MaterialTheme.typography.bodySmall)
                                    val latency = state.dnsStatus[address]
                                    Text(when { latency == null -> "—"; latency < 0 -> "Indisponível"; else -> "$latency ms" },
                                        color = if (latency != null && latency >= 0) palette.primary else palette.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Button(controller::testDns, enabled = !state.testingDns) {
                                Text(if (state.testingDns) "Testando…" else "Testar novamente")
                            }
                        }
                        DesktopSetting.SIGN_OUT, DesktopSetting.UPDATES -> Unit
                    }
                }
            })
    }
}

@Composable
private fun AccountLine(label: String, value: String) {
    Column {
        Text(label.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall, color = palette.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
