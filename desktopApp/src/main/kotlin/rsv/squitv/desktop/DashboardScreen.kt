package rsv.squitv.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import rsv.squitv.core.ui.components.cards.CinematicCardContent
import rsv.squitv.core.ui.components.common.PortalBackdrop
import rsv.squitv.shared.brand.ViewportSizing
import rsv.squitv.core.ui.components.cards.HomeCardType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class DashboardTile(val title: String, val description: String, val icon: ImageVector,
    val type: HomeCardType, val color: Color)

@Composable
private fun desktopProfilePhoto(avatar: String?): androidx.compose.ui.graphics.ImageBitmap? = remember(avatar) {
    val bytes = rsv.squitv.shared.account.ProfileAvatar.photoBytes(avatar)
    val asset = rsv.squitv.shared.account.ProfileAvatar.animalPhotoName(avatar)
    runCatching {
        val stream = bytes?.inputStream() ?: asset?.let {
            DesktopController::class.java.getResourceAsStream("/profile-avatars/$it")
        }
        stream?.use { javax.imageio.ImageIO.read(it)?.toComposeImageBitmap() }
    }.getOrNull()
}

@Composable
internal fun Dashboard(state: DesktopState, controller: DesktopController, store: DesktopPreferencesStore, modifier: Modifier) {
    val preferences by store.state.collectAsState()
    val account = state.account ?: return
    val session by rsv.squitv.shared.account.FavoriteCloudSession.state.collectAsState()
    val cloud = session?.takeIf { it.localAccount == liveCategoryScope(state.credentials) }
    var profileAppearance by remember(cloud) { mutableStateOf<rsv.squitv.shared.account.CloudProfile?>(null) }
    var cloudProfiles by remember(cloud) { mutableStateOf<List<rsv.squitv.shared.account.CloudProfile>>(emptyList()) }
    var showProfiles by remember(cloud) { mutableStateOf(false) }
    var editing by remember(cloud) { mutableStateOf(false) }
    var editAppearance by remember(cloud) { mutableStateOf<rsv.squitv.shared.account.CloudProfile?>(null) }
    var busyProfiles by remember(cloud) { mutableStateOf(false) }
    var profilesError by remember(cloud) { mutableStateOf<String?>(null) }
    val profileScope = remember(cloud, preferences.selectedProfileScopes) {
        cloud?.let { store.dataScope(it.localAccount, it.client.scopeId) }
    }
    val profileForPhoto = profileAppearance
    val activePhoto = key(cloud, profileScope, profileForPhoto?.id, profileForPhoto?.revision) {
        produceState<androidx.compose.ui.graphics.ImageBitmap?>(null) {
            if (cloud == null || profileForPhoto?.hasPhoto != true) return@produceState
            try {
                val bytes = cloud.client.profilePhoto(profileForPhoto.id) ?: return@produceState
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    javax.imageio.stream.MemoryCacheImageInputStream(bytes.inputStream()).use { input ->
                        val readers = javax.imageio.ImageIO.getImageReaders(input)
                        if (!readers.hasNext()) return@use null
                        val reader = readers.next()
                        try {
                            reader.input = input
                            if (reader.getWidth(0) != 1080 || reader.getHeight(0) != 1080) return@use null
                            val params = reader.defaultReadParam.apply { setSourceSubsampling(2, 2, 0, 0) }
                            reader.read(0, params).toComposeImageBitmap()
                        } finally { reader.dispose() }
                    }
                }
            } catch (failure: kotlinx.coroutines.CancellationException) {
                throw failure
            } catch (_: Exception) { /* Keep the locally available avatar. */ }
        }.value
    }
    val profileActions = rememberCoroutineScope()
    suspend fun reloadProfiles() {
        if (cloud == null) return
        val currentScope = store.dataScope(cloud.localAccount, cloud.client.scopeId)
        val local = store.favoriteCloudStore(currentScope, cloud.client.scopeId) { _, _ -> null }
        val id = local.checkpoint()?.profile
        cloudProfiles = cloud.client.profiles()
        profileAppearance = cloudProfiles.firstOrNull { it.id == id }
    }
    LaunchedEffect(cloud, profileScope) {
        if (cloud == null) return@LaunchedEffect
        while (true) {
            try {
                reloadProfiles()
            } catch (failure: kotlinx.coroutines.CancellationException) {
                throw failure
            } catch (_: Exception) { /* Keep the last confirmed appearance while offline. */ }
            delay(60_000)
        }
    }
    if (showProfiles && cloud != null) {
        rsv.squitv.core.ui.components.ProfilePickerDialog(
            cloudProfiles.map { rsv.squitv.core.ui.components.ProfileCardEntry(it.id, it.name, it.avatar) },
            profileAppearance?.id, busyProfiles, profilesError,
            onSelect = { id ->
                val candidate = cloudProfiles.firstOrNull { it.id == id }
                if (candidate != null && !busyProfiles) profileActions.launch {
                    if (candidate.id == profileAppearance?.id) { showProfiles = false; return@launch }
                    busyProfiles = true
                    profilesError = null
                    try {
                        controller.awaitWatchSave()
                        store.selectProfile(cloud, candidate, requireNotNull(controller.watchHistory))
                        profileAppearance = candidate
                        showProfiles = false
                    } catch (failure: kotlinx.coroutines.CancellationException) {
                        throw failure
                    } catch (failure: Exception) {
                        profilesError = failure.message ?: "Não foi possível trocar de perfil."
                    } finally { busyProfiles = false }
                }
            },
            onEdit = { id -> editAppearance = cloudProfiles.firstOrNull { it.id == id }; editing = true; showProfiles = false },
            onCreate = { editAppearance = null; editing = true; showProfiles = false },
            onDismiss = { showProfiles = false },
            photo = { desktopProfilePhoto(it) },
        )
    }
    if (editing && cloud != null) {
        val profile = editAppearance
        rsv.squitv.core.ui.components.ProfileAppearanceDialog(profile?.name.orEmpty(), profile?.avatar,
            onDismiss = { editing = false; showProfiles = true },
            photo = { desktopProfilePhoto(it) },
            onSave = { name, avatar ->
                val updated = if (profile == null) cloud.client.createProfile(name, avatar)
                    else cloud.client.personalizeProfile(profile, name, avatar)
                cloudProfiles = (cloudProfiles.filterNot { it.id == updated.id } + updated).sortedBy { it.name.lowercase() }
                if (profileAppearance?.id == updated.id) profileAppearance = updated
            })
    }
    val displayName = rsv.squitv.shared.account.AccountPresentation.displayName(
        state.portalDisplayName ?: preferences.displayNamesByAccount[liveCategoryScope(state.credentials)]
            .takeIf { state.portalLinkId == null }, state.credentials?.username ?: account.username)
    val windowDragArea = LocalWindowDragArea.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    // Match Android's HH:mm clock without a second date row.
    LaunchedEffect(Unit) { while (true) {
        delay(60_000L - System.currentTimeMillis().mod(60_000L)); now = LocalDateTime.now()
    } }

    val allMain = remember { listOf(
        DashboardTile("TV AO VIVO", "Canais em tempo real", Icons.Rounded.Monitor, HomeCardType.LIVE_TV, palette.primary),
        DashboardTile("FILMES", "Lançamentos e clássicos", Icons.Rounded.Movie, HomeCardType.MOVIES, Color(0xFFFFAB00)),
        DashboardTile("SÉRIES", "Temporadas completas", Icons.Rounded.VideoLibrary, HomeCardType.SERIES, palette.secondary)) }
    val allSecondary = remember { listOf(
        DashboardTile("MULTI-VIEW", "", Icons.Rounded.GridView, HomeCardType.MULTI_VIEW, palette.secondary),
        DashboardTile("DOWNLOADS", "", Icons.Rounded.Download, HomeCardType.DOWNLOADS, Color(0xFF00E676)),
        DashboardTile("FAVORITOS", "", Icons.Rounded.Favorite, HomeCardType.FAVORITES, Color(0xFF2979FF))) }

    fun isSectionVisible(tile: DashboardTile): Boolean {
        val key = when (tile.type) {
            HomeCardType.LIVE_TV -> "LIVE_TV"
            HomeCardType.MOVIES -> "MOVIES"
            HomeCardType.SERIES -> "SERIES"
            HomeCardType.MULTI_VIEW -> "MULTI_VIEW"
            HomeCardType.DOWNLOADS -> "DOWNLOADS"
            HomeCardType.FAVORITES -> "FAVORITES"
            else -> ""
        }
        return !preferences.hiddenSections.contains(key)
    }

    val main = remember(preferences.hiddenSections) { allMain.filter { isSectionVisible(it) } }
    val secondary = remember(preferences.hiddenSections) { allSecondary.filter { isSectionVisible(it) } }

    val visibleTilesCount = main.size + secondary.size
    val cardFocus = remember(visibleTilesCount) { List(visibleTilesCount) { FocusRequester() } }
    val headerFocus = remember { List(3) { FocusRequester() } }
    var lastFocused by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(main, secondary) {
        withFrameNanos { }
        if (cardFocus.isNotEmpty()) cardFocus[lastFocused.coerceIn(0, cardFocus.lastIndex)].requestFocus()
        else headerFocus[0].requestFocus()
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 600.dp
        val viewportHeight = maxHeight
        val viewport = ViewportSizing.viewportScale(maxWidth.value, maxHeight.value)
        val textScale = ViewportSizing.textScale(viewport, LocalDensity.current.fontScale)
        val itemSpacing = 16.dp * viewport

        PortalBackdrop(palette.background, palette.surfaceVariant, palette.primary, showAtmosphere = true) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
                .padding(horizontal = (if (narrow) 16.dp else 48.dp) * viewport,
                    vertical = (if (narrow) 16.dp else 32.dp) * viewport),
                verticalArrangement = Arrangement.SpaceBetween) {
                val profile: @Composable (Modifier) -> Unit = { profileModifier ->
                    windowDragArea(profileModifier) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        rsv.squitv.core.ui.components.ProfileAvatarBadge(profileAppearance?.avatar,
                            profileAppearance?.name ?: displayName, Modifier.size((if (narrow) 48.dp else 64.dp) * viewport),
                            activePhoto ?: desktopProfilePhoto(profileAppearance?.avatar)) {
                            if (cloud == null) controller.openSettings() else {
                                showProfiles = true
                                profileActions.launch {
                                    busyProfiles = true
                                    profilesError = null
                                    try { reloadProfiles() }
                                    catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
                                    catch (failure: Exception) { profilesError = failure.message ?: "Não foi possível carregar os perfis." }
                                    finally { busyProfiles = false }
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Olá, $displayName",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize =
                                    maxOf(MaterialTheme.typography.displayLarge.fontSize.value * textScale, 10f).sp),
                                fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            profileAppearance?.let { Text("Perfil: ${it.name}", style = MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Text("EXPIRA EM: " + (account.expiresAt?.let {
                                Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            } ?: "Não informado"), color = palette.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    }
                }
                val firstCardFocus = cardFocus.firstOrNull() ?: headerFocus[0]
                val actions: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardAction(Icons.Rounded.Search, "Buscar", controller::openSearch, modifier = Modifier.focusRequester(headerFocus[0])
                            .focusProperties { down = firstCardFocus })
                        DashboardAction(Icons.Rounded.Info, "Diagnóstico ao vivo", controller::openNotifications,
                            modifier = Modifier.focusRequester(headerFocus[1]).focusProperties { down = cardFocus.getOrElse(1) { firstCardFocus } })
                        DashboardAction(Icons.Rounded.Settings, "Configurações", controller::openSettings,
                            Modifier.focusRequester(headerFocus[2]).focusProperties { down = cardFocus.getOrElse(2) { firstCardFocus } })
                        Spacer(Modifier.width(12.dp))
                        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.displayLarge.copy(fontSize =
                                maxOf(MaterialTheme.typography.displayLarge.fontSize.value * textScale, 10f).sp),
                            fontWeight = FontWeight.Black)
                    }
                }
                if (narrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        profile(Modifier.fillMaxWidth())
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { actions() }
                    }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    profile(Modifier.weight(1f)); Spacer(Modifier.width(20.dp)); actions()
                }
                if (main.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(itemSpacing)) {
                        main.forEachIndexed { index, tile ->
                            val focusIndex = index
                            DashboardCard(if (narrow) tile.copy(description = "") else tile, true,
                                Modifier.weight(1f).height(280.dp * viewport).then(
                                    Modifier.focusRequester(cardFocus[focusIndex])
                                        .onFocusChanged { if (it.isFocused) lastFocused = focusIndex }
                                        .focusProperties {
                                            left = if (focusIndex > 0) cardFocus[focusIndex - 1] else FocusRequester.Cancel
                                            right = if (focusIndex < main.size - 1) cardFocus[focusIndex + 1] else FocusRequester.Cancel
                                            up = headerFocus[focusIndex.coerceIn(0, 2)]
                                            down = if (secondary.isNotEmpty()) cardFocus[(main.size + focusIndex).coerceAtMost(visibleTilesCount - 1)]
                                                else FocusRequester.Cancel
                                        }
                                ), viewport, textScale,
                                when (tile.type) {
                                    HomeCardType.LIVE_TV -> controller::openLive
                                    HomeCardType.MOVIES -> ({ controller.openCatalog(CatalogKind.MOVIES) })
                                    HomeCardType.SERIES -> ({ controller.openCatalog(CatalogKind.SERIES) })
                                    else -> null
                                })
                        }
                    }
                }
                if (secondary.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(itemSpacing)) {
                        secondary.forEachIndexed { index, tile ->
                            val focusIndex = main.size + index
                            DashboardCard(tile, false,
                                Modifier.weight(1f).height(150.dp * viewport).then(
                                    Modifier.focusRequester(cardFocus[focusIndex])
                                        .onFocusChanged { if (it.isFocused) lastFocused = focusIndex }
                                        .focusProperties {
                                            left = if (index > 0) cardFocus[focusIndex - 1] else FocusRequester.Cancel
                                            right = if (index < secondary.size - 1) cardFocus[focusIndex + 1] else FocusRequester.Cancel
                                            up = if (main.isNotEmpty()) cardFocus[index.coerceAtMost(main.size - 1)] else headerFocus[if (narrow && index == 1) 0 else index.coerceIn(0, 2)]
                                            down = FocusRequester.Cancel
                                        }
                                ), viewport, textScale, when (tile.type) {
                                    HomeCardType.MULTI_VIEW -> controller::openMultiView
                                    HomeCardType.FAVORITES -> controller::openFavorites
                                    HomeCardType.DOWNLOADS -> controller::openDownloads
                                    else -> null
                                })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp * viewport))
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.channelError?.let { StatusNotice(it, error = true) }
                state.notice?.let { StatusNotice(it) }
            }
        }
    }
}

@Composable
private fun DashboardAction(icon: ImageVector, label: String, action: (() -> Unit)? = null,
    modifier: Modifier = Modifier) {
    val interaction = rememberVisualInteraction()
    IconButton(onClick = action ?: {}, interactionSource = interaction.source,
        modifier = modifier.size(48.dp).background(if (interaction.emphasized) palette.onSurface.copy(alpha = .1f)
            else Color.Transparent, CircleShape).then(if (action == null) Modifier.semantics {
                stateDescription = "Visual; função ainda não disponível no Windows"
            } else Modifier)) {
        Icon(icon, label, Modifier.size(24.dp), tint = if (interaction.emphasized) palette.primary else palette.onSurface)
    }
}

@Composable
private fun DashboardCard(tile: DashboardTile, large: Boolean, modifier: Modifier,
    viewport: Float, textScale: Float, onClick: (() -> Unit)? = null) {
    val interaction = rememberVisualInteraction()
    val focused = interaction.emphasized
    val scale by animateFloatAsState(if (focused) { if (large) 1.04f else 1.06f } else 1f)
    val alpha by animateFloatAsState(if (focused) 1f else .85f)
    Surface(onClick = onClick ?: {}, interactionSource = interaction.source,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (onClick == null) Modifier.semantics {
                stateDescription = "Visual; função ainda não disponível no Windows"
            } else Modifier), shape = RoundedCornerShape(16.dp), color = Color(0xFF0F131C),
        border = if (focused) BorderStroke(3.dp, tile.color) else null) {
        CinematicCardContent(tile.title, tile.description, tile.icon, tile.type, tile.color, focused, alpha,
            palette.onSurface, palette.onSurfaceVariant,
            if (large) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge,
            MaterialTheme.typography.bodyLarge, RoundedCornerShape(16.dp), RoundedCornerShape(12.dp),
            24.dp * viewport, 16.dp * viewport, (if (large) 56.dp else 44.dp) * viewport,
            (if (large) 32.dp else 24.dp) * viewport, (if (large) 44.dp else 36.dp) * viewport,
            (if (large) 28.dp else 22.dp) * viewport,
            maxOf((if (large) 28f else 18f) * textScale, 10f).sp, maxOf(16f * textScale, 10f).sp)
    }
}
