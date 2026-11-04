package rsv.squitv.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.semantics
import rsv.squitv.core.ui.components.updates.*
import rsv.squitv.ui.updates.releaseHighlights
import rsv.squitv.desktop.update.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

internal val LocalDesktopUpdates = staticCompositionLocalOf<DesktopUpdateController?> { null }

@Composable
internal fun DesktopUpdatesScreen(install: () -> Unit) {
    val updater = LocalDesktopUpdates.current ?: return
    val state by updater.state.collectAsState()
    LaunchedEffect(Unit) { updater.check() }
    LaunchedEffect(state.prompt) { if (state.prompt) updater.dismissPrompt() }
    val (title, description) = when (state.phase) {
        UpdatePhase.DISABLED -> "Versão de desenvolvimento" to "As atualizações de produção estão disponíveis somente na versão release."
        UpdatePhase.IDLE -> "Atualizações do aplicativo" to "Verifique se há uma nova versão do Squi TV."
        UpdatePhase.CHECKING -> "Verificando atualizações" to "Buscando uma versão compatível com este Windows."
        UpdatePhase.CURRENT -> "Tudo atualizado" to "Você está usando a versão mais recente disponível para este Windows."
        UpdatePhase.AVAILABLE -> "Atualização disponível" to "Veja as novidades e baixe quando quiser."
        UpdatePhase.DOWNLOADING -> "Baixando atualização" to "O instalador será verificado antes de ficar disponível."
        UpdatePhase.READY -> "Pronto para instalar" to "Ao continuar, o instalador do Windows será aberto e o Squi TV será fechado."
        UpdatePhase.INSTALLING -> "Abrindo o instalador" to "Continue a atualização na janela do instalador do Windows."
        UpdatePhase.ERROR -> "Não foi possível atualizar" to "Confira a conexão e tente novamente."
    }
    val icon = when (state.phase) {
        UpdatePhase.DISABLED -> Icons.Rounded.Code
        UpdatePhase.CURRENT -> Icons.Rounded.CheckCircle
        UpdatePhase.ERROR -> Icons.Rounded.ErrorOutline
        UpdatePhase.DOWNLOADING -> Icons.Rounded.Download
        else -> Icons.Rounded.SystemUpdate
    }
    val primaryFocus = remember { FocusRequester() }
    val summaryFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val hasError = state.phase == UpdatePhase.ERROR
    var errorFocused by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase, state.error) {
        if (hasError) scroll.scrollTo(0)
        if (state.phase !in setOf(UpdatePhase.DISABLED, UpdatePhase.INSTALLING)) {
            withFrameNanos { }
            if (hasError) errorFocus.requestFocus() else primaryFocus.requestFocus()
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesktopPageHeading("ATUALIZAÇÕES", "Mantenha o Squi TV atualizado")
        if (hasError) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            UpdateStatusPanel(title, state.error ?: description, icon,
                palette.surface, palette.outline, palette.onSurface, palette.onSurfaceVariant, palette.error,
                MaterialTheme.typography.titleLarge, MaterialTheme.typography.bodyLarge,
                Modifier.widthIn(max = 720.dp).border(2.dp, if (errorFocused) palette.primary else Color.Transparent, RoundedCornerShape(24.dp))
                    .focusRequester(errorFocus).focusProperties { down = summaryFocus }
                    .onFocusChanged { errorFocused = it.isFocused }.semantics(mergeDescendants = true) {}.focusable()) {}
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                UpdateStatusPanel(if (hasError) "Dados da atualização" else title, if (hasError) "Confira os dados e tente novamente abaixo." else description, icon,
                    palette.surface, palette.outline, palette.onSurface, palette.onSurfaceVariant,
                    if (state.phase == UpdatePhase.ERROR) palette.error else palette.primary,
                    MaterialTheme.typography.titleLarge, MaterialTheme.typography.bodyLarge) {
                    Box(Modifier.focusRequester(summaryFocus).focusProperties {
                        up = if (hasError) errorFocus else FocusRequester.Default
                        down = if (state.phase !in setOf(UpdatePhase.DISABLED, UpdatePhase.INSTALLING)) primaryFocus else FocusRequester.Default
                    }.onFocusChanged { if (it.isFocused) scope.launch { scroll.scrollTo(0) } }
                        .semantics(mergeDescendants = true) {}.focusable()) {
                    UpdateVersionPanel("Versão instalada", DesktopBuildInfo.VERSION, "Versão disponível",
                        state.info?.artifact?.versionName?.let { "v$it" }, palette.background, palette.onSurface,
                        palette.onSurfaceVariant, palette.primary, MaterialTheme.typography.titleLarge, MaterialTheme.typography.labelSmall)
                    }
                    state.info?.let { info ->
                        val date = remember(info.publishedAt) {
                            runCatching { java.time.Instant.parse(info.publishedAt).atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate().format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)) }.getOrNull()
                        }
                        Text(listOfNotNull("Windows ${info.artifact.architecture}", megabytes(info.artifact.sizeBytes), date).joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val active = if (state.phase in setOf(UpdatePhase.READY, UpdatePhase.INSTALLING)) 2 else 0
                            listOf("Baixar", "Verificar", "Instalar").forEachIndexed { index, label ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val tint = if (index <= active) palette.primary else palette.onSurfaceVariant
                                    Box(Modifier.size(28.dp).background(tint.copy(alpha = .12f), androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center) {
                                        if (index < active) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = tint)
                                        else Text("${index + 1}", color = tint, style = MaterialTheme.typography.labelLarge)
                                    }
                                    Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (state.phase in setOf(UpdatePhase.CHECKING, UpdatePhase.INSTALLING)) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.phase == UpdatePhase.DOWNLOADING) {
                        val total = state.info?.artifact?.sizeBytes ?: 1
                        LinearProgressIndicator(progress = { (state.bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${(state.bytes.toDouble() / total * 100).toInt().coerceIn(0, 100)}%")
                            Text("${megabytes(state.bytes)} / ${megabytes(total)}", color = palette.onSurfaceVariant)
                        }
                    }
                    val action = Modifier.fillMaxWidth().focusRequester(primaryFocus).focusProperties { up = summaryFocus }
                    when (state.phase) {
                        UpdatePhase.DOWNLOADING, UpdatePhase.CHECKING -> DesktopUpdateAction("Cancelar", updater::cancel, action, primary = false)
                        UpdatePhase.AVAILABLE -> {
                            DesktopUpdateAction("Baixar atualização", updater::download, action, icon = Icons.Rounded.Download)
                            DesktopUpdateAction("Não lembrar esta versão", { updater.dismissPrompt(ignore = true) }, primary = false, icon = Icons.Rounded.NotificationsOff)
                        }
                        UpdatePhase.READY -> {
                            DesktopUpdateAction("Instalar e fechar o app", install, action, icon = Icons.Rounded.SystemUpdate)
                            DesktopUpdateAction("Baixar novamente", updater::download, primary = false, icon = Icons.Rounded.Refresh)
                        }
                        UpdatePhase.ERROR -> {
                            if (state.info != null) {
                                DesktopUpdateAction("Tentar baixar novamente", updater::download, action, icon = Icons.Rounded.Refresh)
                                DesktopUpdateAction("Verificar novamente", { updater.check(force = true) }, primary = false)
                            } else DesktopUpdateAction("Tentar novamente", { updater.check(force = true) }, action, icon = Icons.Rounded.Refresh)
                        }
                        UpdatePhase.IDLE, UpdatePhase.CURRENT -> DesktopUpdateAction("Verificar atualizações", { updater.check(force = true) }, action, icon = Icons.Rounded.Refresh)
                        else -> Unit
                    }
                }
                if (state.phase !in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.CHECKING, UpdatePhase.INSTALLING)) state.info?.let { info ->
                    val lines = remember(info.notes) { releaseHighlights(info.notes) }
                    var expanded by rememberSaveable(info.artifact.versionName) { mutableStateOf(false) }
                    Surface(shape = RoundedCornerShape(24.dp), color = palette.surface, border = BorderStroke(1.dp, palette.outline)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("O que mudou", style = MaterialTheme.typography.titleLarge)
                            if (lines.isEmpty()) Text("Esta versão não incluiu notas de atualização.", color = palette.onSurfaceVariant)
                            (if (expanded) lines else lines.take(4)).forEach { line ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("•", color = palette.primary)
                                    Text(line, Modifier.weight(1f), color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (lines.size > 4) DesktopUpdateAction(if (expanded) "Mostrar menos" else "Mostrar mais", { expanded = !expanded }, primary = false)
                        }
                    }
                }
                if (state.phase == UpdatePhase.AVAILABLE) DesktopUpdateAction("Verificar novamente", { updater.check(force = true) }, primary = false, icon = Icons.Rounded.Refresh)
            }
        }
    }
}

@Composable
private fun DesktopUpdateAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
    primary: Boolean = true, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Button(onClick, modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) palette.primary else palette.surfaceVariant,
            contentColor = if (primary) palette.background else palette.onSurface),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun DesktopUpdateNotice(updater: DesktopUpdateController, info: WindowsUpdate, open: () -> Unit) {
    val window = LocalDesktopWindow.current
    var active by remember { mutableStateOf(window?.isActive ?: true) }
    DisposableEffect(window) {
        val listener = object : WindowAdapter() {
            override fun windowGainedFocus(e: WindowEvent) { active = true }
            override fun windowLostFocus(e: WindowEvent) { active = false }
        }
        window?.addWindowFocusListener(listener)
        onDispose { window?.removeWindowFocusListener(listener) }
    }
    var remaining by rememberSaveable(info.artifact.versionName) { mutableIntStateOf(10) }
    LaunchedEffect(info.artifact.versionName, active) {
        if (active) { while (remaining > 0) { delay(1000); remaining-- }; updater.dismissPrompt() }
    }
    val view = remember { FocusRequester() }; val later = remember { FocusRequester() }; val ignore = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; view.requestFocus() }
    fun focus(current: FocusRequester, next: FocusRequester, previous: FocusRequester) = Modifier.focusRequester(current).focusProperties {
        this.next = next; this.previous = previous; right = next; left = previous; down = next; up = previous
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f))
        .clickable(remember { MutableInteractionSource() }, indication = null) { updater.dismissPrompt() },
        contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth().heightIn(max = maxHeight * .85f)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(24.dp), color = palette.surface, border = BorderStroke(1.dp, palette.outline)) {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                UpdateNoticeContent("Atualização disponível", "Versão ${info.artifact.versionName} pronta",
                    "Uma nova versão do Squi TV está disponível. Veja as novidades e atualize quando quiser.",
                    "Fecha em ${remaining}s", "Não lembrar esta versão", remaining, palette.primary, palette.onSurface, palette.onSurfaceVariant,
                    MaterialTheme.typography.titleLarge, MaterialTheme.typography.labelLarge,
                    MaterialTheme.typography.bodyLarge, MaterialTheme.typography.labelSmall,
                    onIgnore = { updater.dismissPrompt(ignore = true) }, ignoreModifier = focus(ignore, view, later)) {
                    Button(open, Modifier.weight(1f).then(focus(view, later, ignore))) { Text("Ver atualização") }
                    OutlinedButton({ updater.dismissPrompt() }, Modifier.weight(1f).then(focus(later, ignore, view))) { Text("Depois") }
                }
            }
        }
    }
}

private fun megabytes(bytes: Long): String = "%.1f MB".format(bytes / (1024.0 * 1024.0))
