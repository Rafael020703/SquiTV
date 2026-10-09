package rsv.squitv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.settings.*
import rsv.squitv.core.ui.components.states.LoadingState
import rsv.squitv.core.ui.theme.*
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.ui.viewmodel.settings.SettingsViewModel
import rsv.squitv.util.AppVersionProvider
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.delay

/**
 * CONFIGURAÇÕES - REESTRUTURAÇÃO VISUAL PÁGINA ÚNICA
 *
 * Grade responsiva horizontal com componentes em ordem alfabética estrita,
 * sem sidebar, aproveitando a largura útil total e alinhada ao visual do Dashboard.
 */

enum class SettingsCategory(val labelRes: Int, val icon: ImageVector) {
    ACCOUNT(R.string.account_label, Icons.Rounded.ManageAccounts),
    SIGN_OUT(R.string.logout_label, Icons.AutoMirrored.Rounded.Logout),
    LANGUAGE(R.string.language_label, Icons.Rounded.Language),
    THEME(R.string.oled_theme_label, Icons.Rounded.DarkMode),
    ZOOM(R.string.ui_zoom_label, Icons.Rounded.ZoomIn),
    PLAYER_ENGINE(R.string.video_quality_section, Icons.Rounded.SettingsInputComponent),
    BUFFER(R.string.buffer_strategy_label, Icons.Rounded.Memory),
    AUTO_PLAY(R.string.auto_play_label, Icons.AutoMirrored.Rounded.PlaylistPlay),
    BACKGROUND_PLAYBACK(R.string.background_playback_label, Icons.Rounded.Headphones),
    DIAGNOSTICS(R.string.show_diagnostics_label, Icons.Rounded.Analytics),
    PARENTAL(R.string.parental_control_label, Icons.Rounded.Lock),
    HIDE_LOCKED(R.string.hide_blocked_label, Icons.Rounded.VisibilityOff),
    UPDATE(R.string.force_sync_button, Icons.Rounded.Sync),
    APP_UPDATES(R.string.app_updates_title, Icons.Rounded.SystemUpdate),
    DNS_TESTER(R.string.dns_tester_title, Icons.Rounded.Dns),
    CLEAR_CACHE(R.string.clear_cache_label, Icons.Rounded.Brush),
    CLEAR_CATALOG(R.string.reset_database_label, Icons.Rounded.DeleteSweep)
}

@UnstableApi
@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToDnsTester: () -> Unit = {},
    onNavigateToUpdates: () -> Unit = {}
) {
    val settings by settingsViewModel.settings.collectAsState()
    val dbStats by mainViewModel.dbStats.collectAsState()
    val credentials = mainViewModel.credentials
    val windowInfo = rememberWindowInfo()
    val tokens = AppDesignSystem
    var detailCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    val contentFocusRequester = remember { FocusRequester() }

    // Ordenação alfabética estrita do texto visível na interface
    val categoryNames = SettingsCategory.entries.associateWith { stringResource(it.labelRes) }
    val sortedCategories = remember(categoryNames) {
        SettingsCategory.entries.sortedBy { categoryNames[it]?.uppercase() ?: "" }
    }

    BackHandler(enabled = detailCategory != null || showResetDialog) {
        if (detailCategory != null) detailCategory = null
        else if (showResetDialog) showResetDialog = false
    }

    LaunchedEffect(Unit) {
        mainViewModel.refreshStats()
        delay(300)
        try { contentFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    PortalBackground(showAtmosphere = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // --- HEADER DA PÁGINA ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_nav_title).uppercase(),
                        style = tokens.typography.headline.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.Black,
                        color = tokens.colors.textPrimary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "GERENCIE SUA EXPERIÊNCIA PREMIUM",
                        style = tokens.typography.body.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.primary,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- SEÇÕES CATEGORIZADAS DE CONFIGURAÇÕES ---
            val settingsGroups = remember {
                listOf(
                    "CONTA E PREFERÊNCIAS" to listOf(SettingsCategory.ACCOUNT, SettingsCategory.SIGN_OUT, SettingsCategory.LANGUAGE, SettingsCategory.THEME, SettingsCategory.ZOOM),
                    "REPRODUÇÃO" to listOf(SettingsCategory.PLAYER_ENGINE, SettingsCategory.BUFFER, SettingsCategory.AUTO_PLAY, SettingsCategory.BACKGROUND_PLAYBACK),
                    "SEGURANÇA E CATÁLOGO" to listOf(SettingsCategory.PARENTAL, SettingsCategory.HIDE_LOCKED, SettingsCategory.CLEAR_CATALOG),
                    "ATUALIZAÇÃO E MANUTENÇÃO" to listOf(SettingsCategory.UPDATE, SettingsCategory.APP_UPDATES, SettingsCategory.CLEAR_CACHE),
                    "DIAGNÓSTICO AVANÇADO" to listOf(SettingsCategory.DIAGNOSTICS, SettingsCategory.DNS_TESTER)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                settingsGroups.forEach { (groupTitle, categories) ->
                    item(key = groupTitle) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = groupTitle,
                                style = tokens.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Black),
                                color = tokens.colors.primary,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                itemsIndexed(categories, key = { _, item -> item.name }) { index, category ->
                                    val cardWidth = if (windowInfo.screenWidth < 600.dp) 130.dp else 165.dp
                                    Box(modifier = Modifier.width(cardWidth)) {
                                        SettingsGridCard(
                                            label = stringResource(category.labelRes),
                                            icon = category.icon,
                                            isDestructive = category == SettingsCategory.SIGN_OUT || category == SettingsCategory.CLEAR_CATALOG,
                                            modifier = if (groupTitle == "CONTA E PREFERÊNCIAS" && index == 0) Modifier.focusRequester(contentFocusRequester) else Modifier,
                                            onClick = {
                                                when (category) {
                                                    SettingsCategory.ACCOUNT -> onNavigateToAccount()
                                                    SettingsCategory.SIGN_OUT -> mainViewModel.logout()
                                                    SettingsCategory.UPDATE -> mainViewModel.loadData(force = true)
                                                    SettingsCategory.APP_UPDATES -> onNavigateToUpdates()
                                                    SettingsCategory.DNS_TESTER -> onNavigateToDnsTester()
                                                    SettingsCategory.CLEAR_CACHE -> mainViewModel.clearCache()
                                                    SettingsCategory.CLEAR_CATALOG -> showResetDialog = true
                                                    else -> detailCategory = category
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- RODAPÉ SECUNDÁRIO DISCRETO ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = tokens.colors.surfaceElevated.copy(alpha = 0.5f),
                shape = tokens.shapes.medium,
                border = BorderStroke(1.dp, tokens.colors.border.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = LocalContext.current
                    val appVersion = remember(context) { AppVersionProvider.getFormattedVersionName(context).uppercase() }
                    Text(
                        text = "VERSÃO $appVersion PREMIUM",
                        style = tokens.typography.caption.copy(fontSize = 11.sp),
                        color = tokens.colors.textSecondary.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "BIBLIOTECA: ${dbStats.first} CANAIS | ${dbStats.second} FILMES | ${dbStats.third} SÉRIES",
                        style = tokens.typography.caption.copy(fontSize = 11.sp),
                        color = tokens.colors.textSecondary.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }

    // DIÁLOGO DE CONFIRMAÇÃO PARA "REDEFINIR BASE DE DADOS"
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = tokens.colors.backgroundSecondary,
            shape = tokens.shapes.extraLarge,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = tokens.colors.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "REDEFINIR BASE DE DADOS",
                        style = tokens.typography.headline.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Black,
                        color = tokens.colors.error
                    )
                }
            },
            text = {
                Text(
                    text = "Deseja realmente redefinir a base de dados do aplicativo? Todos os canais, filmes e séries sincronizados localmente serão limpos e resincronizados.",
                    style = tokens.typography.body.copy(fontSize = 13.sp),
                    color = tokens.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        mainViewModel.clearCatalogData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.error)
                ) {
                    Text("REDEFINIR", fontWeight = FontWeight.Black, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCELAR", color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // DIÁLOGOS DE CATEGORIA / DETALHES
    if (detailCategory != null) {
        val cat = detailCategory!!
        AlertDialog(
            onDismissRequest = { detailCategory = null },
            containerColor = tokens.colors.backgroundSecondary,
            shape = tokens.shapes.extraLarge,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(cat.icon, null, tint = tokens.colors.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(cat.labelRes).uppercase(),
                        style = tokens.typography.headline.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Black,
                        color = tokens.colors.textPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (cat) {
                        SettingsCategory.ACCOUNT -> {
                            SettingsActionItem(
                                label = "CONTA ATIVA: ${credentials?.username ?: "NENHUM"}",
                                icon = Icons.Rounded.AccountCircle,
                                onClick = onNavigateToAccount
                            )
                            SettingsActionItem(
                                label = "GERENCIAR PERFIS E CONEXÕES",
                                icon = Icons.Rounded.People,
                                onClick = onNavigateToAccount
                            )
                        }
                        SettingsCategory.LANGUAGE -> {
                            val codes = mapOf(
                                "auto" to stringResource(R.string.lang_auto),
                                "pt" to stringResource(R.string.lang_portuguese),
                                "en" to stringResource(R.string.lang_english),
                                "es" to stringResource(R.string.lang_spanish)
                            )
                            codes.forEach { (code, name) ->
                                SettingsActionItem(
                                    label = name,
                                    onClick = { settingsViewModel.updateLanguage(code); detailCategory = null },
                                    trailingText = if (settings.language == code || (code == "auto" && settings.language.isBlank())) "ATIVO" else null
                                )
                            }
                        }
                        SettingsCategory.THEME -> {
                            SettingsToggle(
                                label = "TEMA OLED BLACK",
                                checked = settings.useOledTheme,
                                description = "Fundo preto puro para economia de bateria e contraste infinito.",
                                onCheckedChange = { settingsViewModel.updateUseOledTheme(it) }
                            )
                        }
                        SettingsCategory.ZOOM -> {
                            Text("TAMANHO DA INTERFACE", color = tokens.colors.primary, style = tokens.typography.label, fontWeight = FontWeight.Black)
                            val options = listOf(0.8f to "PEQUENO", 1.0f to "PADRÃO", 1.2f to "GRANDE", 1.5f to "EXTRA")
                            options.forEach { (zoom, label) ->
                                SettingsActionItem(
                                    label = label,
                                    onClick = { settingsViewModel.updateUiZoom(zoom) },
                                    trailingText = if (settings.uiZoom == zoom) "ATIVO" else null
                                )
                            }
                        }
                        SettingsCategory.PLAYER_ENGINE -> {
                            Text("MOTOR DE REPRODUÇÃO", color = tokens.colors.primary, style = tokens.typography.label, fontWeight = FontWeight.Black)
                            listOf("EXO" to "EXOPLAYER (RECOMENDADO)", "VLC" to "VLC ENGINE (EXTERNO)").forEach { (id, label) ->
                                SettingsActionItem(
                                    label = label,
                                    onClick = { settingsViewModel.updatePlayerEngine(id) },
                                    trailingText = if (settings.playerEngine == id) "ATIVO" else null
                                )
                            }
                        }
                        SettingsCategory.BUFFER -> {
                            Text("ESTRATÉGIA DE CARREGAMENTO", color = tokens.colors.primary, style = tokens.typography.label, fontWeight = FontWeight.Black)
                            listOf("Stable" to "ESTÁVEL (EQUILIBRADO)", "Fast" to "RÁPIDO (MENOR LATÊNCIA)", "Safe" to "SEGURO (MAIOR CACHE)").forEach { (id, label) ->
                                SettingsActionItem(
                                    label = label,
                                    onClick = { settingsViewModel.updateBufferStrategy(id) },
                                    trailingText = if (settings.bufferStrategy == id) "ATIVO" else null
                                )
                            }
                        }
                        SettingsCategory.AUTO_PLAY -> {
                            SettingsToggle(
                                label = "AUTO-REPRODUÇÃO",
                                checked = settings.autoPlayEnabled,
                                description = "Iniciar próximo episódio ou canal automaticamente.",
                                onCheckedChange = { settingsViewModel.updateAutoPlay(it) }
                            )
                        }
                        SettingsCategory.BACKGROUND_PLAYBACK -> {
                            SettingsToggle(
                                label = stringResource(R.string.background_playback_label).uppercase(),
                                checked = settings.backgroundPlaybackEnabled,
                                description = stringResource(R.string.background_playback_desc),
                                onCheckedChange = { settingsViewModel.updateBackgroundPlaybackEnabled(it) }
                            )
                        }
                        SettingsCategory.DIAGNOSTICS -> {
                            SettingsToggle(
                                label = "ESTATÍSTICAS DE REDE",
                                checked = settings.showDiagnostics,
                                description = "Exibir velocidade e buffer durante a reprodução.",
                                onCheckedChange = { settingsViewModel.updateShowDiagnostics(it) }
                            )
                        }
                        SettingsCategory.PARENTAL -> {
                            var pinInput by remember { mutableStateOf(settings.appPin ?: "") }
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { if (it.length <= 4) pinInput = it },
                                label = { Text("PIN DE 4 DÍGITOS") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = tokens.shapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = tokens.colors.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(Modifier.height(12.dp))
                            AppButton(
                                text = "SALVAR NOVO PIN",
                                onClick = { settingsViewModel.updatePin(pinInput.ifEmpty { null }); detailCategory = null },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        SettingsCategory.HIDE_LOCKED -> {
                            SettingsToggle(
                                label = "OCULTAR BLOQUEADOS",
                                checked = settings.hideBlockedCategories,
                                description = "Categorias protegidas por PIN não aparecerão no catálogo.",
                                onCheckedChange = { settingsViewModel.updateHideBlockedCategories(it) }
                            )
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailCategory = null }) {
                    Text("CONCLUÍDO", color = tokens.colors.primary, fontWeight = FontWeight.Black)
                }
            }
        )
    }
}
