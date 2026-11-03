package rsv.squitv.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.core.ui.components.settings.CommonSettingsGridCard
import rsv.squitv.core.ui.components.player.PlayerSettingsOption
import rsv.squitv.domain.model.ChannelQuality
import rsv.squitv.shared.catalog.CategoryCustomization
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private enum class DesktopSetting(val label: String, val icon: ImageVector) {
    ACCOUNT("Conta", Icons.Rounded.ManageAccounts),
    SIGN_OUT("Sair da conta", Icons.AutoMirrored.Rounded.Logout),
    NAV_CUSTOMIZATION("Personalização das Seções", Icons.Rounded.DashboardCustomize),
    CATALOG_CATEGORIES("Organizar categorias", Icons.Rounded.Category),
    CATALOG_MODE("Categorias do catálogo", Icons.Rounded.FilterList),
    BACKUP("Backup e Restauração", Icons.Rounded.SettingsBackupRestore),
    ZOOM("Zoom da Interface", Icons.Rounded.ZoomIn),
    AUTO_PLAY("Auto-reprodução de Episódios", Icons.AutoMirrored.Rounded.PlaylistPlay),
    LIVE_CHANNELS("Canais ao vivo", Icons.Rounded.Tune),
    SYNC_CATALOG("Atualizar categorias ao vivo", Icons.Rounded.Sync),
    UPDATES("Atualizações", Icons.Rounded.SystemUpdate),
    DNS("Testador de DNS", Icons.Rounded.Dns),
    CLEAR_CACHE("Limpar cache de catálogos", Icons.Rounded.Brush)
}

/** Android SettingsScreen grid style; every displayed option has a Windows implementation. */
@Composable
internal fun DesktopSettingsScreen(state: DesktopState, controller: DesktopController, store: DesktopPreferencesStore) {
    val preferences by store.state.collectAsState()
    val catalogState by controller.catalog.state.collectAsState()
    val scope = rememberCoroutineScope()
    val accountScope = remember(state.credentials) { liveCategoryScope(state.credentials) }
    val error by store.error.collectAsState()
    val loaded by store.loaded.collectAsState()
    var selected by rememberSaveable { mutableStateOf<DesktopSetting?>(null) }
    var customizationKind by rememberSaveable { mutableStateOf<CatalogKind?>(null) }
    val customizationCategories = customizationKind?.let { catalogState.browse[it]?.categories }.orEmpty()
        .let { if (customizationKind == null) state.categories else it }
    val categoryScroll = key(customizationKind) { rememberLazyListState() }
    val categoryFocus = remember(accountScope, customizationKind) { mutableMapOf<String, FocusRequester>() }
    var movingCategory by remember { mutableStateOf(false) }
    var confirmCategoryReset by remember(accountScope, customizationKind) { mutableStateOf(false) }
    LaunchedEffect(selected, customizationKind, state.credentials) {
        val kind = customizationKind
        if (selected == DesktopSetting.CATALOG_CATEGORIES && kind != null)
            state.credentials?.let { controller.catalog.loadCategoryOptions(kind, it) }
    }
    fun moveCategory(id: String, offset: Int = 0, position: Int? = null) {
        if (movingCategory) return
        val key = accountScope ?: return
        val targetKind = customizationKind
        val available = customizationCategories
        movingCategory = true
        scope.launch {
            try {
                val saved = store.updateAndAwait { current ->
                    val next = CategoryCustomization.move(current.categoryOrder(key, targetKind),
                        available.map { it.id }, id, offset, position) ?: return@updateAndAwait current
                    current.withCategoryOrder(key, targetKind, next)
                }
                if (saved && selected == DesktopSetting.CATALOG_CATEGORIES && customizationKind == targetKind) {
                    val ordered = CategoryCustomization.apply(available, store.state.value.categoryOrder(key, targetKind)) { it.id }
                    val index = ordered.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        categoryScroll.scrollToItem(index)
                        withFrameNanos { }
                        runCatching { categoryFocus[id]?.requestFocus() }
                    }
                }
            } finally { movingCategory = false }
        }
    }
    val categories = remember {
        val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
        DesktopSetting.entries.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesktopPageHeading("CONFIGURAÇÕES", "Gerencie sua experiência premium")
        error?.let { StatusNotice(it, error = true) }
        state.notice?.let { StatusNotice(it, error = false) }
        if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyVerticalGrid(GridCells.Adaptive(165.dp), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(categories, key = { it.name }) { category ->
                val interaction = rememberVisualInteraction()
                val base = if (category == DesktopSetting.SIGN_OUT) palette.error else palette.primary
                CommonSettingsGridCard(
                    label = category.label,
                    icon = category.icon,
                    focused = interaction.emphasized,
                    baseColor = base,
                    surfaceColor = Color(0xFF0D1322).copy(alpha = .82f),
                    textColor = palette.onSurface,
                    labelStyle = MaterialTheme.typography.labelMedium,
                    interactionSource = interaction.source,
                    onClick = {
                        if (loaded) {
                            when (category) {
                                DesktopSetting.SIGN_OUT -> controller.disconnect()
                                DesktopSetting.UPDATES -> controller.openUpdates()
                                DesktopSetting.SYNC_CATALOG -> {
                                    controller.syncCatalog()
                                    selected = category
                                }
                                else -> selected = category
                            }
                        }
                    }
                )
            }
        }

    }
    selected?.let { category ->
        AlertDialog(onDismissRequest = { selected = null; confirmCategoryReset = false },
            title = { Text(category.label) }, confirmButton = { TextButton({ selected = null; confirmCategoryReset = false }) { Text("Concluir") } },
            text = {
                val contentModifier = Modifier.widthIn(min = 320.dp).heightIn(max = 420.dp)
                Column(if (category == DesktopSetting.CATALOG_CATEGORIES) contentModifier
                    else contentModifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (category) {
                        DesktopSetting.ACCOUNT -> {
                            state.account?.let { account ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = palette.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.AccountCircle,
                                            contentDescription = null,
                                            tint = palette.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                account.username.uppercase(Locale.ROOT),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.onSurface
                                            )
                                            Text(
                                                "STATUS: ${account.status.uppercase(Locale.ROOT)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = palette.primary
                                            )
                                        }
                                    }
                                }
                                AccountLine("Usuário", account.username)
                                AccountLine("Validade", account.expiresAt?.let {
                                    Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                                } ?: "Não informada")
                                val connectionsText = listOfNotNull(account.activeConnections?.toString(), account.maxConnections?.toString()).joinToString(" / ")
                                if (connectionsText.isNotBlank()) {
                                    AccountLine("Conexões (Ativas / Máximo)", connectionsText)
                                }
                                account.timezone?.let { AccountLine("Fuso do Servidor", it) }
                            } ?: Text("Nenhuma informação de conta carregada.", color = palette.onSurfaceVariant)
                        }
                        DesktopSetting.NAV_CUSTOMIZATION -> {
                            Text("Oculte ou exiba seções na área principal do aplicativo:", style = MaterialTheme.typography.bodyMedium)
                            val sections = listOf(
                                "LIVE_TV" to "TV Ao Vivo",
                                "MOVIES" to "Filmes",
                                "SERIES" to "Séries",
                                "MULTI_VIEW" to "Multi-View",
                                "DOWNLOADS" to "Downloads",
                                "FAVORITES" to "Favoritos"
                            )
                            sections.forEach { (key, label) ->
                                val visible = !preferences.hiddenSections.contains(key)
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        store.update { p ->
                                            val nextHidden = if (visible) p.hiddenSections + key else p.hiddenSections - key
                                            p.copy(hiddenSections = nextHidden)
                                        }
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = if (visible) palette.primary else palette.onSurfaceVariant)
                                    Switch(
                                        checked = visible,
                                        onCheckedChange = { isVisible ->
                                            store.update { p ->
                                                val nextHidden = if (!isVisible) p.hiddenSections + key else p.hiddenSections - key
                                                p.copy(hiddenSections = nextHidden)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        DesktopSetting.AUTO_PLAY -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Reproduzir o próximo episódio automaticamente", Modifier.weight(1f))
                                Switch(preferences.autoPlayEpisodes, { enabled -> store.update { it.copy(autoPlayEpisodes = enabled) } })
                            }
                        }
                        DesktopSetting.LIVE_CHANNELS -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Agrupar versões do mesmo canal", Modifier.weight(1f))
                                Switch(preferences.groupLiveChannels, { grouped ->
                                    store.update { it.copy(groupLiveChannels = grouped) }
                                })
                            }
                            Text("Qualidade preferida", style = MaterialTheme.typography.titleSmall)
                            ChannelQuality.entries.forEach { quality ->
                                PlayerSettingsOption(quality.desktopLabel(),
                                    selected = quality == ChannelQuality.fromPreference(preferences.preferredChannelQuality),
                                    primary = palette.primary, text = palette.onSurface, secondary = palette.onSurfaceVariant,
                                    onClick = { store.update { it.copy(preferredChannelQuality = quality.name) } })
                            }
                            Text("Seleciona um endereço do provedor. Automático prioriza a maior resolução identificada; não mede a conexão.",
                                style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                        }
                        DesktopSetting.ZOOM -> {
                            Text("Tamanho dos elementos: ${(preferences.interfaceScale * 100).toInt()}%")
                            var scale by remember(preferences.interfaceScale) { mutableFloatStateOf(preferences.interfaceScale) }
                            Slider(scale, { scale = it }, valueRange = .8f..1.25f, steps = 8,
                                onValueChangeFinished = { store.update { it.copy(interfaceScale = scale) } })
                            TextButton({ store.update { it.copy(interfaceScale = 1f) } }) { Text("Restaurar 100%") }
                        }
                        DesktopSetting.CLEAR_CACHE -> {
                            Text("Limpar as listagens mantidas em memória e o índice de busca? As contas e configurações serão preservadas.", style = MaterialTheme.typography.bodyMedium)
                            var cleared by remember { mutableStateOf(false) }
                            Button(onClick = {
                                controller.clearCache()
                                cleared = true
                            }, enabled = !cleared) {
                                Text(if (cleared) "Cache Limpo!" else "Limpar Cache Agora")
                            }
                        }
                        DesktopSetting.SYNC_CATALOG -> {
                            Text("Atualiza as categorias ao vivo do servidor. As listas de filmes e séries continuam sob demanda.",
                                style = MaterialTheme.typography.bodyMedium)
                            when {
                                state.refreshingCatalog -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                state.catalogSyncError != null -> Text(state.catalogSyncError, color = palette.error)
                                state.notice != null -> Text(state.notice, color = palette.onSurfaceVariant)
                            }
                            Button(controller::syncCatalog, enabled = !state.refreshingCatalog) {
                                Text(if (state.refreshingCatalog) "Atualizando…" else "Atualizar agora")
                            }
                        }
                        DesktopSetting.BACKUP -> {
                            Text("Exporte ou restaure o arquivo de preferências e configurações do aplicativo:", style = MaterialTheme.typography.bodyMedium)
                            var backupNotice by remember { mutableStateOf<String?>(null) }
                            var backupBusy by remember { mutableStateOf(false) }
                            Text("Arquivo local: %LOCALAPPDATA%/${DesktopBuildInfo.DATA_FOLDER}/squitv-backup.json. Inclui preferências, organização de categorias e favoritos de canais, filmes e séries por conta. Não inclui credenciais, catálogos, histórico ou backup Android.",
                                style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(enabled = !backupBusy, onClick = {
                                    backupBusy = true
                                    scope.launch {
                                        try {
                                            val ok = controller.exportBackup(preferences)
                                            backupNotice = if (ok) "Preferências exportadas." else "Não foi possível exportar as preferências."
                                        } finally { backupBusy = false }
                                    }
                                }) { Text("Exportar") }
                                OutlinedButton(enabled = !backupBusy, onClick = {
                                    backupBusy = true
                                    scope.launch {
                                        try {
                                            val restored = controller.importBackup()
                                            val applied = restored != null && store.updateAndAwait { restored }
                                            backupNotice = if (applied) "Preferências restauradas."
                                                else "Não foi possível restaurar. Confira se o backup existe e é compatível."
                                        } finally { backupBusy = false }
                                    }
                                }) { Text("Restaurar") }
                            }
                            backupNotice?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        DesktopSetting.CATALOG_MODE -> {
                            Text("Escolha separadamente para Filmes e Séries. Gêneros aparecem primeiro em A–Z; conteúdos sem gênero ficam nas categorias do servidor.",
                                style = MaterialTheme.typography.bodyMedium)
                            CatalogKind.entries.forEach { kind ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(kind.title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                    CatalogModeSelector(preferences.catalogMode(accountScope.orEmpty(), kind), accountScope != null) { mode ->
                                        accountScope?.let { key -> store.update { it.withCatalogMode(key, kind, mode) } }
                                    }
                                }
                            }
                        }
                        DesktopSetting.CATALOG_CATEGORIES -> {
                            val targetKind = customizationKind
                            val key = accountScope.orEmpty()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(null to "Ao vivo", CatalogKind.MOVIES to "Filmes", CatalogKind.SERIES to "Séries").forEach { (kind, label) ->
                                    FilterChip(targetKind == kind, { customizationKind = kind }, { Text(label) }, enabled = !movingCategory)
                                }
                            }
                            Text("Organize a ordem e escolha quais categorias do servidor exibir.", style = MaterialTheme.typography.bodyMedium)
                            error?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
                            val categories = remember(customizationCategories, preferences.categoryOrder(key, targetKind)) {
                                CategoryCustomization.apply(customizationCategories, preferences.categoryOrder(key, targetKind)) { it.id }
                            }
                            val options = targetKind?.let { catalogState.browse[it] }
                            val loading = if (targetKind == null) state.busy || state.refreshingCatalog
                                else options == null || options.loadingCategories
                            val optionsError = if (targetKind == null) state.catalogSyncError else options?.error
                            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            optionsError?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
                            val hidden = preferences.hiddenCategories(key, targetKind)
                            Text("Exibindo ${categories.count { it.id !in hidden }} de ${categories.size} categorias",
                                style = MaterialTheme.typography.labelSmall, color = palette.primary)
                            if (confirmCategoryReset) {
                                Text("Restaurar a ordem do servidor e mostrar todas as categorias desta conta nesta seção?",
                                    style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton({ confirmCategoryReset = false }) { Text("Cancelar") }
                                    TextButton({
                                        val key = accountScope ?: return@TextButton
                                        movingCategory = true
                                        scope.launch {
                                            try {
                                                if (store.updateAndAwait { it.withCategoryOrder(key, targetKind, emptyList())
                                                        .withHiddenCategories(key, targetKind, emptySet()) })
                                                    confirmCategoryReset = false
                                            } finally { movingCategory = false }
                                        }
                                    }, enabled = !movingCategory) { Text("Restaurar") }
                                }
                            } else Row {
                                TextButton({ store.update {
                                    val key = accountScope ?: return@update it
                                    it.withHiddenCategories(key, targetKind, emptySet())
                                } }, enabled = hidden.isNotEmpty() && !movingCategory) { Text("Mostrar todas") }
                                TextButton({ confirmCategoryReset = true }, enabled = !movingCategory) { Text("Restaurar padrão") }
                            }
                            if (categories.isEmpty() && !loading) {
                                Text("Nenhuma categoria carregada no momento.", style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                                TextButton({
                                    if (targetKind == null) controller.syncCatalog()
                                    else state.credentials?.let { controller.catalog.loadCategoryOptions(targetKind, it, force = true) }
                                }) { Text("Tentar novamente") }
                            } else {
                                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), state = categoryScroll) {
                                    itemsIndexed(categories, key = { _, category -> category.id }) { index, cat ->
                                        val hidden = preferences.hiddenCategories(key, targetKind).contains(cat.id)
                                        val toggleFocus = categoryFocus.getOrPut(cat.id) { FocusRequester() }
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                store.update { p ->
                                                    val key = accountScope ?: return@update p
                                                    val old = p.hiddenCategories(key, targetKind)
                                                    val next = if (cat.id in old) old - cat.id else old + cat.id
                                                    p.withHiddenCategories(key, targetKind, next)
                                                }
                                            }.padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text("#${index + 1}", style = MaterialTheme.typography.labelSmall, color = palette.primary)
                                                Text(cat.name, style = MaterialTheme.typography.bodyMedium,
                                                    color = if (hidden) palette.onSurfaceVariant else palette.onSurface, maxLines = 2,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            }
                                            IconButton({ moveCategory(cat.id, position = 0) }, enabled = index > 0 && !movingCategory) {
                                                Icon(Icons.Rounded.VerticalAlignTop, "Mover ${cat.name} para o topo", Modifier.size(18.dp))
                                            }
                                            IconButton({ moveCategory(cat.id, offset = -1) }, enabled = index > 0 && !movingCategory) {
                                                Icon(Icons.Rounded.KeyboardArrowUp, "Subir ${cat.name}", Modifier.size(18.dp))
                                            }
                                            IconButton({ moveCategory(cat.id, offset = 1) }, enabled = index < categories.lastIndex && !movingCategory) {
                                                Icon(Icons.Rounded.KeyboardArrowDown, "Descer ${cat.name}", Modifier.size(18.dp))
                                            }
                                            Switch(checked = !hidden, modifier = Modifier.focusRequester(toggleFocus), onCheckedChange = { visible ->
                                                store.update { p ->
                                                    val key = accountScope ?: return@update p
                                                    val old = p.hiddenCategories(key, targetKind)
                                                    val next = if (!visible) old + cat.id else old - cat.id
                                                    p.withHiddenCategories(key, targetKind, next)
                                                }
                                            })
                                        }
                                    }
                                }
                            }
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
