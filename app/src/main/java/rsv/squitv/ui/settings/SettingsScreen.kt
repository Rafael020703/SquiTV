package rsv.squitv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
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
import rsv.squitv.core.ui.components.AccountSyncSettingsDialog
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.settings.*
import rsv.squitv.core.ui.components.states.LoadingState
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.model.SettingsBackupCodec
import rsv.squitv.shared.account.FavoriteCloudSession
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.ui.viewmodel.settings.SettingsViewModel
import rsv.squitv.util.AppVersionProvider
import rsv.squitv.util.rememberWindowInfo
import rsv.squitv.util.supportsMediaPip
import rsv.squitv.ui.login.SyncItem
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
    NAV_CUSTOMIZATION(R.string.nav_customization_label, Icons.Rounded.DashboardCustomize),
    CATALOG_CATEGORIES(R.string.catalog_categories_title, Icons.Rounded.Category),
    BACKUP(R.string.settings_backup_title, Icons.Rounded.SettingsBackupRestore),
    LANGUAGE(R.string.language_label, Icons.Rounded.Language),
    ZOOM(R.string.ui_zoom_label, Icons.Rounded.ZoomIn),
    CHANNEL_QUALITY(R.string.channels_and_quality, Icons.Rounded.HighQuality),
    AUTO_PLAY(R.string.auto_play_label, Icons.AutoMirrored.Rounded.PlaylistPlay),
    BACKGROUND_AND_PIP(R.string.background_and_pip_label, Icons.Rounded.PictureInPictureAlt),
    UPDATE(R.string.force_sync_button, Icons.Rounded.Sync),
    ACCOUNT_SYNC(R.string.account_sync_now, Icons.Rounded.CloudSync),
    CREDITS(R.string.metadata_credits, Icons.Rounded.Info),
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
    onNavigateToUpdates: () -> Unit = {},
    onNavigateToContentCustomization: () -> Unit = {}
) {
    val settings by settingsViewModel.settings.collectAsState()
    val syncProgress by mainViewModel.syncProgress.collectAsStateWithLifecycle()
    val pipSupported = LocalContext.current.supportsMediaPip()
    val dbStats by mainViewModel.dbStats.collectAsState()
    val credentials = mainViewModel.credentials
    val windowInfo = rememberWindowInfo()
    val tokens = AppDesignSystem
    var detailCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showAccountSync by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    val contentFocusRequester = remember { FocusRequester() }

    val sortedCategories = alphabeticalSettingsCategories()

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

            SettingsOptionsGrid(
                categories = sortedCategories,
                minimumCardWidth = if (windowInfo.screenWidth < 600.dp) 130.dp else 165.dp,
                modifier = Modifier.fillMaxWidth().weight(1f),
                focusRequester = contentFocusRequester,
                onSelect = { category ->
                    when (category) {
                        SettingsCategory.ACCOUNT -> onNavigateToAccount()
                        SettingsCategory.NAV_CUSTOMIZATION -> onNavigateToContentCustomization()
                        SettingsCategory.BACKUP -> showBackupDialog = true
                        SettingsCategory.ACCOUNT_SYNC -> showAccountSync = true
                        SettingsCategory.SIGN_OUT -> mainViewModel.logout()
                        SettingsCategory.UPDATE -> {
                            if (!syncProgress.isRunning) mainViewModel.loadData(force = true)
                            showSyncDialog = true
                        }
                        SettingsCategory.APP_UPDATES -> onNavigateToUpdates()
                        SettingsCategory.DNS_TESTER -> onNavigateToDnsTester()
                        SettingsCategory.CLEAR_CACHE -> mainViewModel.clearCache()
                        SettingsCategory.CLEAR_CATALOG -> showResetDialog = true
                        else -> detailCategory = category
                    }
                }
            )

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

    if (showBackupDialog) {
        SettingsBackupDialog(onDismiss = { showBackupDialog = false })
    }

    if (showAccountSync) {
        val session by FavoriteCloudSession.state.collectAsStateWithLifecycle()
        val account = settings.credentials?.let(SettingsBackupCodec::accountKey)
            ?.let(SettingsBackupCodec::fingerprint)
        val active = session?.takeIf { it.localAccount == account }
        val profileId = settings.activeProfileId
        val profileName = settings.profiles.firstOrNull { it.id == profileId }?.name ?: "Perfil principal"
        val local = remember(active, profileId) {
            active?.let { libraryViewModel.favoriteCloudStore(it.localAccount, profileId, profileName, it.client.scopeId) }
        }
        AccountSyncSettingsDialog(active?.client, local, onClose = { showAccountSync = false })
    }

    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = { Text(stringResource(R.string.catalog_sync_title)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (syncProgress.isRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
                    SyncItem(stringResource(R.string.sync_account), syncProgress.accountStatus)
                    SyncItem(stringResource(R.string.sync_live), syncProgress.liveStatus)
                    SyncItem(stringResource(R.string.sync_movies), syncProgress.vodStatus)
                    SyncItem(stringResource(R.string.sync_series), syncProgress.seriesStatus)
                    SyncItem(stringResource(R.string.sync_epg), syncProgress.epgStatus)
                    if (syncProgress.isComplete) {
                        Text(
                            when {
                                syncProgress.isCancelled -> stringResource(R.string.sync_cancelled)
                                syncProgress.error != null -> stringResource(R.string.sync_partial_failure)
                                else -> stringResource(R.string.catalog_sync_success)
                            },
                            fontWeight = FontWeight.Bold
                        )
                        syncProgress.error?.let { Text(it, color = tokens.colors.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSyncDialog = false }) { Text(stringResource(R.string.close)) }
            },
            dismissButton = {
                if (syncProgress.isComplete && (syncProgress.error != null || syncProgress.isCancelled)) {
                    TextButton(onClick = { mainViewModel.loadData(force = true) }) {
                        Text(stringResource(R.string.retry_sync_button))
                    }
                }
            }
        )
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
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (cat) {
                        SettingsCategory.CREDITS -> rsv.squitv.core.ui.components.EditorialCredits()
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
                        SettingsCategory.NAV_CUSTOMIZATION -> {
                            val hiddenSections by mainViewModel.hiddenSections.collectAsState()
                            val sections = listOf(
                                "LIVE_TV" to "TV Ao Vivo",
                                "MOVIES" to "Filmes",
                                "SERIES" to "Séries",
                                "MULTI_VIEW" to "Multi-View",
                                "DOWNLOADS" to "Downloads",
                                "FAVORITES" to "Favoritos"
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Oculte seções que você não utiliza na tela inicial.",
                                    style = tokens.typography.caption,
                                    color = tokens.colors.textSecondary
                                )
                                sections.forEach { (key, title) ->
                                    val isVisible = key !in hiddenSections
                                    SettingsToggle(
                                        label = title,
                                        checked = isVisible,
                                        onCheckedChange = { _ -> mainViewModel.toggleSectionVisibility(key) }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { mainViewModel.restoreDefaultSections() },
                                    colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.primary.copy(alpha = 0.2f), contentColor = tokens.colors.primary),
                                    shape = tokens.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("RESTAURAR PADRÃO", fontWeight = FontWeight.Black)
                                }
                            }
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
                        SettingsCategory.CHANNEL_QUALITY -> {
                            ChannelQualitySettings(settings.groupLiveChannels, settings.preferredChannelQuality,
                                settingsViewModel::updateGroupLiveChannels, settingsViewModel::updatePreferredChannelQuality)
                        }
                        SettingsCategory.AUTO_PLAY -> {
                            SettingsToggle(
                                label = "AUTO-REPRODUÇÃO",
                                checked = settings.autoPlayEnabled,
                                description = "Iniciar próximo episódio automaticamente.",
                                onCheckedChange = { settingsViewModel.updateAutoPlay(it) }
                            )
                        }
                        SettingsCategory.BACKGROUND_AND_PIP -> {
                            SettingsToggle(
                                label = stringResource(R.string.background_audio_label),
                                checked = settings.backgroundPlaybackEnabled,
                                description = stringResource(R.string.background_audio_desc),
                                onCheckedChange = { settingsViewModel.updateBackgroundPlaybackEnabled(it) }
                            )
                            Spacer(Modifier.height(8.dp))
                            if (pipSupported) {
                                SettingsToggle(
                                    label = stringResource(R.string.pip_auto_minimize_label),
                                    checked = settings.pipAutoEnterEnabled,
                                    description = stringResource(R.string.pip_auto_minimize_desc),
                                    onCheckedChange = { settingsViewModel.updatePipAutoEnterEnabled(it) }
                                )
                            } else {
                                Surface(
                                    color = tokens.colors.surfaceElevated,
                                    shape = tokens.shapes.medium,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.pip_unsupported_dialog_msg),
                                        style = tokens.typography.caption,
                                        color = tokens.colors.textSecondary,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                        SettingsCategory.CATALOG_CATEGORIES -> {
                            val accountKey = settings.credentials?.let { rsv.squitv.data.model.SettingsBackupCodec.accountKey(it) } ?: ""
                            val movieMode = settings.getMovieCategoryMode(accountKey, settings.activeProfileId)
                            val seriesMode = settings.getSeriesCategoryMode(accountKey, settings.activeProfileId)
                            val prepState by mainViewModel.prepState.collectAsStateWithLifecycle()

                            Text("MODO DAS CATEGORIAS", color = tokens.colors.primary, style = tokens.typography.label, fontWeight = FontWeight.Black)

                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.movie_category_mode_label), style = tokens.typography.body, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                AppButton(
                                    text = stringResource(R.string.category_mode_provider),
                                    onClick = { mainViewModel.updateMovieCategoryMode(rsv.squitv.domain.model.CategoryMode.PROVIDER) },
                                    enabled = movieMode != rsv.squitv.domain.model.CategoryMode.PROVIDER
                                )
                                AppButton(
                                    text = stringResource(R.string.category_mode_genre),
                                    onClick = { mainViewModel.updateMovieCategoryMode(rsv.squitv.domain.model.CategoryMode.GENRE) },
                                    enabled = movieMode != rsv.squitv.domain.model.CategoryMode.GENRE
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(stringResource(R.string.series_category_mode_label), style = tokens.typography.body, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                AppButton(
                                    text = stringResource(R.string.category_mode_provider),
                                    onClick = { mainViewModel.updateSeriesCategoryMode(rsv.squitv.domain.model.CategoryMode.PROVIDER) },
                                    enabled = seriesMode != rsv.squitv.domain.model.CategoryMode.PROVIDER
                                )
                                AppButton(
                                    text = stringResource(R.string.category_mode_genre),
                                    onClick = { mainViewModel.updateSeriesCategoryMode(rsv.squitv.domain.model.CategoryMode.GENRE) },
                                    enabled = seriesMode != rsv.squitv.domain.model.CategoryMode.GENRE
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(stringResource(R.string.catalog_preparing_title).uppercase(), color = tokens.colors.primary, style = tokens.typography.label, fontWeight = FontWeight.Black)
                            rsv.squitv.core.ui.components.common.PreparationProgressSummary(
                                prepState.totalItems, prepState.processedItems, prepState.estimatedRemainingMs, prepState.isPaused,
                                prepState.failedItems, tokens.colors.primary, tokens.colors.textSecondary)
                            prepState.error?.let { Text(it, color = tokens.colors.error, style = tokens.typography.caption) }
                            Text("Prioridade atual: ${if (prepState.priorityType == "SERIES") "Séries" else "Filmes"}", style = tokens.typography.body, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Filmes: ${prepState.vodProcessed} / ${prepState.vodTotal} (com gênero: ${prepState.vodWithGenre})", style = tokens.typography.caption, color = tokens.colors.textSecondary)
                            Text("Séries: ${prepState.seriesProcessed} / ${prepState.seriesTotal} (com gênero: ${prepState.seriesWithGenre})", style = tokens.typography.caption, color = tokens.colors.textSecondary)

                            Spacer(Modifier.height(8.dp))

                            Text("VELOCIDADE DE PREPARAÇÃO", style = tokens.typography.caption, fontWeight = FontWeight.Black, color = tokens.colors.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                AppButton(
                                    text = "Lento",
                                    onClick = { mainViewModel.setPreparationSpeed(rsv.squitv.domain.model.PreparationSpeed.SLOW) },
                                    enabled = prepState.speed != rsv.squitv.domain.model.PreparationSpeed.SLOW
                                )
                                AppButton(
                                    text = "Normal",
                                    onClick = { mainViewModel.setPreparationSpeed(rsv.squitv.domain.model.PreparationSpeed.NORMAL) },
                                    enabled = prepState.speed != rsv.squitv.domain.model.PreparationSpeed.NORMAL
                                )
                                AppButton(
                                    text = "Rápido",
                                    onClick = { mainViewModel.setPreparationSpeed(rsv.squitv.domain.model.PreparationSpeed.FAST) },
                                    enabled = prepState.speed != rsv.squitv.domain.model.PreparationSpeed.FAST
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                if (prepState.isRunning) {
                                    AppButton(text = stringResource(R.string.catalog_prep_pause_action), onClick = { mainViewModel.pausePrep() })
                                } else if (prepState.isPaused) {
                                    AppButton(text = stringResource(R.string.catalog_prep_resume_action), onClick = { mainViewModel.resumePrep() })
                                }
                                if (prepState.failedItems > 0) {
                                    AppButton(text = stringResource(R.string.catalog_prep_retry_action), onClick = { mainViewModel.retryPrepFailed() })
                                }
                            }
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

@Composable
internal fun alphabeticalSettingsCategories(): List<SettingsCategory> {
    val names = SettingsCategory.entries.associateWith { stringResource(it.labelRes) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return remember(names, locale) {
        val collator = java.text.Collator.getInstance(locale).apply { strength = java.text.Collator.PRIMARY }
        SettingsCategory.entries.sortedWith { a, b -> collator.compare(names[a], names[b]) }
    }
}

@Composable
internal fun SettingsOptionsGrid(
    categories: List<SettingsCategory>,
    minimumCardWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onSelect: (SettingsCategory) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minimumCardWidth),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(categories, key = { _, category -> category.name }) { index, category ->
            SettingsGridCard(
                label = stringResource(category.labelRes),
                icon = category.icon,
                isDestructive = category == SettingsCategory.SIGN_OUT || category == SettingsCategory.CLEAR_CATALOG,
                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                onClick = { onSelect(category) }
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Configurações alfabéticas", widthDp = 960, heightDp = 540, locale = "pt")
@Composable
fun SettingsAlphabeticalPreview() {
    MeusCanaisTheme {
        SettingsOptionsGrid(
            alphabeticalSettingsCategories(), 165.dp,
            modifier = Modifier.fillMaxSize().background(AppDesignSystem.colors.background),
            onSelect = {}
        )
    }
}
