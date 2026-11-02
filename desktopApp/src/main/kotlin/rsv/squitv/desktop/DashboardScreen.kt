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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
internal fun Dashboard(state: DesktopState, controller: DesktopController, modifier: Modifier) {
    val account = state.account ?: return
    val windowDragArea = LocalWindowDragArea.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    // Only hours/minutes and date are displayed: update at the next minute boundary.
    LaunchedEffect(Unit) { while (true) {
        delay(60_000L - System.currentTimeMillis().mod(60_000L)); now = LocalDateTime.now()
    } }
    val cardFocus = remember { List(6) { FocusRequester() } }
    val headerFocus = remember { List(3) { FocusRequester() } }
    var lastFocused by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        cardFocus[lastFocused.coerceIn(0, 5)].requestFocus()
    }
    val main = remember { listOf(
        DashboardTile("TV AO VIVO", "Canais em tempo real", Icons.Rounded.Monitor, HomeCardType.LIVE_TV, palette.primary),
        DashboardTile("FILMES", "Lançamentos e clássicos", Icons.Rounded.Movie, HomeCardType.MOVIES, Color(0xFFFFAB00)),
        DashboardTile("SÉRIES", "Temporadas completas", Icons.Rounded.VideoLibrary, HomeCardType.SERIES, palette.secondary)) }
    val secondary = remember { listOf(
        DashboardTile("MULTI-VIEW", "", Icons.Rounded.GridView, HomeCardType.MULTI_VIEW, palette.secondary),
        DashboardTile("DOWNLOADS", "", Icons.Rounded.Download, HomeCardType.DOWNLOADS, Color(0xFF00E676)),
        DashboardTile("FAVORITOS", "", Icons.Rounded.Favorite, HomeCardType.FAVORITES, Color(0xFF2979FF))) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 600.dp
        val viewportHeight = maxHeight
        val viewport = ViewportSizing.viewportScale(maxWidth.value, maxHeight.value)
        val textScale = ViewportSizing.textScale(viewport, LocalDensity.current.fontScale)
        val itemSpacing = 16.dp * viewport
        fun cardModifier(index: Int): Modifier = Modifier.focusRequester(cardFocus[index])
            .onFocusChanged { if (it.isFocused) lastFocused = index }
            .focusProperties {
                left = if (index % 3 > 0) cardFocus[index - 1] else FocusRequester.Cancel
                right = if (index % 3 < 2) cardFocus[index + 1] else FocusRequester.Cancel
                up = if (index >= 3) cardFocus[index - 3] else headerFocus[if (narrow && index == 1) 0 else index]
                down = if (index < 3) cardFocus[index + 3] else FocusRequester.Cancel
            }
        PortalBackdrop(palette.background, palette.surfaceVariant, palette.primary, showAtmosphere = true) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
                .padding(horizontal = (if (narrow) 16.dp else 48.dp) * viewport, vertical = 32.dp * viewport),
                verticalArrangement = Arrangement.SpaceBetween) {
                val profile: @Composable (Modifier) -> Unit = { profileModifier ->
                    windowDragArea(profileModifier) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size((if (narrow) 48.dp else 64.dp) * viewport), shape = CircleShape,
                            color = palette.primary.copy(alpha = .1f), border = BorderStroke(2.dp, palette.primary.copy(alpha = .3f))) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Person, null, Modifier.size((if (narrow) 32.dp else 40.dp) * viewport), tint = palette.primary)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("OLÁ, ${account.username.uppercase(Locale.ROOT)}",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize =
                                    maxOf(MaterialTheme.typography.displayLarge.fontSize.value * textScale, 10f).sp),
                                fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("EXPIRA EM: " + (account.expiresAt?.let {
                                Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            } ?: "Não informado"), color = palette.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    }
                }
                val actions: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardAction(Icons.Rounded.Search, "Buscar", modifier = Modifier.focusRequester(headerFocus[0])
                            .focusProperties { down = cardFocus[0] })
                        if (!narrow) DashboardAction(Icons.Rounded.Notifications, "Notificações",
                            modifier = Modifier.focusRequester(headerFocus[1]).focusProperties { down = cardFocus[1] })
                        DashboardAction(Icons.Rounded.Settings, "Configurações", controller::openSettings,
                            Modifier.focusRequester(headerFocus[2]).focusProperties { down = cardFocus[2] })
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.displayLarge.copy(fontSize =
                                    maxOf(MaterialTheme.typography.displayLarge.fontSize.value * textScale, 10f).sp),
                                fontWeight = FontWeight.Black)
                            Text(now.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM", Locale.forLanguageTag("pt-BR"))),
                                color = palette.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(itemSpacing)) {
                    main.forEachIndexed { index, tile -> DashboardCard(tile, true,
                        Modifier.weight(1f).height(280.dp * viewport).then(cardModifier(index)), viewport, textScale,
                        when (tile.type) {
                            HomeCardType.LIVE_TV -> controller::openLive
                            HomeCardType.MOVIES -> ({ controller.openCatalog(CatalogKind.MOVIES) })
                            HomeCardType.SERIES -> ({ controller.openCatalog(CatalogKind.SERIES) })
                            else -> null
                        }) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(itemSpacing)) {
                    secondary.forEachIndexed { index, tile -> DashboardCard(tile, false,
                        Modifier.weight(1f).height(150.dp * viewport).then(cardModifier(index + 3)), viewport, textScale) }
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
