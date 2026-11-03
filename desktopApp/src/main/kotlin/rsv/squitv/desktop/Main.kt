package rsv.squitv.desktop

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import rsv.squitv.core.ui.components.ExitConfirmationCard
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.launch
import rsv.squitv.desktop.data.WindowsCredentialStore
import rsv.squitv.desktop.data.XtreamClient
import rsv.squitv.shared.xtream.ServerCredentials
import rsv.squitv.shared.brand.AppBrand
import rsv.squitv.desktop.player.WindowsLivePlayer
import rsv.squitv.desktop.update.DesktopUpdateController

internal val palette = darkColorScheme(
    primary = Color(AppBrand.PRIMARY), onPrimary = Color.Black,
    secondary = Color(AppBrand.SECONDARY), onSecondary = Color.White,
    primaryContainer = Color(AppBrand.PRIMARY_VARIANT), onPrimaryContainer = Color.White,
    background = Color(AppBrand.BACKGROUND), surface = Color(AppBrand.SURFACE),
    onBackground = Color(AppBrand.TEXT), surfaceVariant = Color(AppBrand.SURFACE_VARIANT), onSurface = Color(AppBrand.TEXT),
    onSurfaceVariant = Color(AppBrand.MUTED), outline = Color(AppBrand.GLASS_BORDER),
    outlineVariant = Color(AppBrand.GLASS_WHITE), error = Color(AppBrand.ERROR), onError = Color.White)

fun main(args: Array<String>) {
    if (args.size == 2 && args[0] == "--self-check") {
        selfCheck(Path.of(args[1]))
        return
    }
    val instance = SingleInstance.acquire() ?: return
    try { runApplication() } finally { instance.close() }
}

private fun runApplication() {
    application {
        val scope = rememberCoroutineScope()
        val directory = remember {
            Path.of(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), DesktopBuildInfo.DATA_FOLDER)
        }
        val controller = remember { DesktopController(XtreamClient(),
            WindowsCredentialStore(directory, useDebugDefaults = DesktopBuildInfo.DEBUG,
                debugDefaultsFile = if (DesktopBuildInfo.DEBUG)
                    System.getProperty("squitv.debug.defaultsFile")?.let(Path::of) ?: directory.resolve("debug-login.dpapi")
                else directory.resolve("debug-login.dpapi")), scope) }
        val preferences = remember { DesktopPreferencesStore(directory, scope) }
        val updates = remember { DesktopUpdateController(directory.resolve("updates"), preferences, scope) }
        val updateState by updates.state.collectAsState()
        DisposableEffect(updates) { onDispose { updates.close() } }
        val settings by preferences.state.collectAsState()
        val preferencesLoaded by preferences.loaded.collectAsState()
        LaunchedEffect(preferencesLoaded, settings.groupLiveChannels, settings.preferredChannelQuality) {
            if (preferencesLoaded) controller.configureLiveChannels(settings.groupLiveChannels,
                rsv.squitv.domain.model.ChannelQuality.fromPreference(settings.preferredChannelQuality))
        }
        val windowState = rememberWindowState(placement = WindowPlacement.Fullscreen, width = 1120.dp, height = 630.dp)
        var windowModeRestored by remember { mutableStateOf(false) }
        var closing by remember { mutableStateOf(false) }
        LaunchedEffect(preferencesLoaded) {
            if (preferencesLoaded && !windowModeRestored) {
                windowState.placement = if (settings.fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
                windowModeRestored = true
            }
        }
        val toggleFullscreen: () -> Unit = {
            val fullscreen = windowState.placement != WindowPlacement.Fullscreen
            windowState.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
            preferences.update { it.copy(fullscreen = fullscreen) }
        }
        var showExitDialog by remember { mutableStateOf(false) }
        val closeApplication: () -> Unit = {
            if (!closing) {
                closing = true
                scope.launch {
                    try { preferences.updateAndAwait { it.copy(fullscreen = windowState.placement == WindowPlacement.Fullscreen) } }
                    finally { exitApplication() }
                }
            }
        }
        var activePlayer by remember { mutableStateOf<WindowsLivePlayer?>(null) }
        var livePlayerInput by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }
        DisposableEffect(controller) { onDispose { controller.close() } }
        Window(onCloseRequest = closeApplication, title = DesktopBuildInfo.APP_NAME,
            visible = windowModeRestored,
            icon = brandPainter(), state = windowState, undecorated = true,
            resizable = windowState.placement != WindowPlacement.Fullscreen,
            onPreviewKeyEvent = { event ->
                val current = controller.state.value
                val catalog = controller.catalog.state.value
                val playing = current.playback != null || catalog.playback != null
                val back = event.key == Key.Escape || event.key == Key.Back
                if (event.type == KeyEventType.KeyDown && event.key == Key.F11) { toggleFullscreen(); true }
                else if (showExitDialog && back) {
                    if (event.type == KeyEventType.KeyUp) showExitDialog = false
                    true
                }
                else if (showExitDialog) false
                else if (updateState.prompt && current.account != null && current.page == DesktopPage.DASHBOARD && back) {
                    if (event.type == KeyEventType.KeyUp) updates.dismissPrompt()
                    true
                }
                else if (updateState.prompt && current.account != null && current.page == DesktopPage.DASHBOARD) false
                else if (playing && livePlayerInput?.invoke(event) == true) true
                else if (back) {
                    if (event.type == KeyEventType.KeyUp) when {
                        current.playback != null -> controller.stopPlayback()
                        catalog.playback != null -> controller.catalog.stopPlayback()
                        catalog.detail != null -> controller.catalog.closeDetail()
                        current.account != null && current.page == DesktopPage.UPDATES -> controller.openSettings()
                        current.account != null && current.page != DesktopPage.DASHBOARD -> controller.home()
                        else -> showExitDialog = true
                    }
                    true
                }
                else if (event.type != KeyEventType.KeyDown) false
                else when {
                    playing && event.key == Key.Spacebar -> { activePlayer?.togglePause(); true }
                    current.playback != null && event.key == Key.PageUp -> { controller.stepChannel(-1); true }
                    current.playback != null && event.key == Key.PageDown -> { controller.stepChannel(1); true }
                    playing && event.key == Key.F -> {
                        toggleFullscreen(); true
                    }
                    else -> false
                }
            }) {
            MaintainWindowAspectRatio(window, windowState)
            MaterialTheme(colorScheme = palette, typography = desktopTypography) {
                Surface(Modifier.fillMaxSize(), color = palette.background) {
                    val state by controller.state.collectAsState()
                    LaunchedEffect(state.account != null, state.page, preferencesLoaded) {
                        if (preferencesLoaded && state.account != null && state.page == DesktopPage.DASHBOARD) updates.check(notify = true)
                    }
                    val density = LocalDensity.current
                    Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalDesktopWindow provides window, LocalDesktopUpdates provides updates,
                            LocalDensity provides Density(density.density * settings.interfaceScale, density.fontScale),
                            LocalWindowDragArea provides { modifier, content ->
                            if (windowState.placement == WindowPlacement.Fullscreen) Box(modifier) { content() }
                            else WindowDraggableArea(modifier) { content() }
                        }) {
                            DesktopScreen(state, controller, preferences, windowState.placement == WindowPlacement.Fullscreen,
                                toggleFullscreen, { activePlayer = it }, { livePlayerInput = it }, installUpdate = { updates.install(closeApplication) })
                            if (!showExitDialog && updateState.prompt && state.account != null && state.page == DesktopPage.DASHBOARD)
                                updateState.info?.let { info -> DesktopUpdateNotice(updates, info) { updates.dismissPrompt(); controller.openUpdates() } }
                        }
                        if (showExitDialog) Box(Modifier.fillMaxSize()
                            .clickable(remember { MutableInteractionSource() }, indication = null) { showExitDialog = false },
                            contentAlignment = Alignment.Center) {
                            Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = .72f)) {}
                            ExitConfirmationCard("SAIR DO APLICATIVO", "Deseja realmente fechar o Squi TV?", "SAIR", "FICAR",
                                palette.surfaceVariant, palette.primary, palette.onSurfaceVariant, palette.onSurface, palette.error,
                                MaterialTheme.typography.headlineMedium, MaterialTheme.typography.bodyMedium,
                                MaterialTheme.shapes.extraLarge, MaterialTheme.shapes.medium,
                                onDismiss = { showExitDialog = false }, onExit = closeApplication,
                                modifier = Modifier.padding(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopScreen(state: DesktopState, controller: DesktopController, preferences: DesktopPreferencesStore,
    fullscreen: Boolean = false, toggleFullscreen: () -> Unit = {},
    registerPlayer: (WindowsLivePlayer?) -> Unit = {},
    registerLiveInput: (((KeyEvent) -> Boolean)?) -> Unit = {}, installUpdate: () -> Unit = {}) {
    val images = remember(state.credentials) { DesktopImages() }
    DisposableEffect(images) { onDispose { images.close() } }
    CompositionLocalProvider(LocalDesktopImages provides images) {
        DesktopScreenContent(state, controller, preferences, fullscreen, toggleFullscreen,
            registerPlayer, registerLiveInput, installUpdate)
    }
}

@Composable
private fun DesktopScreenContent(state: DesktopState, controller: DesktopController, preferences: DesktopPreferencesStore,
    fullscreen: Boolean, toggleFullscreen: () -> Unit,
    registerPlayer: (WindowsLivePlayer?) -> Unit,
    registerLiveInput: (((KeyEvent) -> Boolean)?) -> Unit, installUpdate: () -> Unit) {
    val screens = rememberSaveableStateHolder()
    val catalog by controller.catalog.state.collectAsState()
    var savedDetailKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(catalog.detail?.kind, catalog.detail?.item?.id) {
        val next = catalog.detail?.let { "detail-${it.kind}-${it.item.id}" }
        if (savedDetailKey != next) {
            savedDetailKey?.let(screens::removeState)
            savedDetailKey = next
        }
    }
    LaunchedEffect(state.account == null) { if (state.account == null) {
        listOf("dashboard", "live", "MOVIES", "SERIES", "settings", "search").forEach(screens::removeState)
    } }
    if (state.playback != null) {
        LivePlayerScreen(state, controller, preferences, fullscreen, toggleFullscreen, registerPlayer, registerLiveInput)
        return
    }
    val vod = catalog.playback
    if (vod != null && state.credentials != null) {
        VodPlayerScreen(vod, state.credentials, controller, preferences, fullscreen, toggleFullscreen, registerPlayer, registerLiveInput)
        return
    }
    Column(Modifier.fillMaxSize()) {
        if (state.restoring) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        } else if (state.account == null && state.automaticLogin) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(32.dp))
                Spacer(Modifier.height(16.dp))
                Text("Reconectando sua conta…", color = palette.onSurfaceVariant)
                TextButton(controller::cancelConnection) { Text("Cancelar") }
            }
        } else if (state.account == null) {
            LoginForm(state, controller, Modifier.weight(1f))
        } else if (state.page == DesktopPage.DASHBOARD) {
            screens.SaveableStateProvider("dashboard") { Dashboard(state, controller, preferences, Modifier.weight(1f)) }
        } else if (state.page == DesktopPage.LIVE) {
            screens.SaveableStateProvider("live") { LiveCatalogScreen(state, controller, preferences, Modifier.weight(1f)) }
        } else if (state.page == DesktopPage.UPDATES) {
            DesktopUpdatesScreen(installUpdate)
        } else if (state.page == DesktopPage.SETTINGS) {
            screens.SaveableStateProvider("settings") { DesktopSettingsScreen(state, controller, preferences) }
        } else if (catalog.detail != null) {
            val detail = requireNotNull(catalog.detail)
            screens.SaveableStateProvider("detail-${detail.kind}-${detail.item.id}") {
                OnDemandDetailScreen(detail, controller)
            }
        } else if (state.page == DesktopPage.SEARCH) {
            screens.SaveableStateProvider("search") { DesktopSearchScreen(controller) }
        } else {
            screens.SaveableStateProvider(catalog.kind.name) { OnDemandCatalogScreen(catalog, controller, preferences) }
        }
    }
}

private fun selfCheck(report: Path) {
    val directory = Files.createTempDirectory("squitv-windows-check-")
    try {
        val store = WindowsCredentialStore(directory)
        val fixture = ServerCredentials("https://example.invalid/", "fixture", "not-a-real-password")
        store.save(fixture)
        check(store.load() == fixture)
        check(!Files.readAllBytes(directory.resolve("account.dpapi")).toString(Charsets.ISO_8859_1).contains(fixture.password))
        store.clear()
        check(store.load() == null)
        check(rsv.squitv.shared.update.VersionComparator.compareVersions("1.21", "1.21.0") == 0)
        Files.writeString(report, """{"ok":true,"variant":"${if (DesktopBuildInfo.DEBUG) "debug" else "release"}","version":"${DesktopBuildInfo.VERSION}","dataFolder":"${DesktopBuildInfo.DATA_FOLDER}","dpapi":true}""")
    } finally { Files.deleteIfExists(directory.resolve("account.dpapi")); Files.deleteIfExists(directory) }
}
