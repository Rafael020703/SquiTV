package rsv.squitv.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import rsv.squitv.R
import rsv.squitv.core.domain.state.AppSyncProgress
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.buttons.AppSecondaryButton
import rsv.squitv.core.ui.components.cards.CategoryCard
import rsv.squitv.core.ui.components.cards.CinematicActionCard
import rsv.squitv.core.ui.components.cards.HomeCardType
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.theme.*
import rsv.squitv.core.ui.components.common.DigitalClock
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.data.model.UserInfo
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.domain.model.SessionStatus
import rsv.squitv.domain.model.UserProfile
import rsv.squitv.core.ui.components.common.DigitalDate
import rsv.squitv.util.DeviceType
import rsv.squitv.util.WindowInfo
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@UnstableApi
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToCategory: (String, String?) -> Unit,
    onNavigateToMultiView: () -> Unit = {},
    onNavigateToEpgGrid: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToUpdates: () -> Unit = {}
) {
    val windowInfo = rememberWindowInfo()
    val accountInfo by viewModel.accountInfo.collectAsStateWithLifecycle()
    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val progress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val isContentReady by viewModel.isContentReady.collectAsStateWithLifecycle()
    val newlyAdded by viewModel.newlyAdded.collectAsStateWithLifecycle()
    val watchProgress by libraryViewModel.watchProgress.collectAsStateWithLifecycle()
    val availableUpdatePrompt by viewModel.availableUpdatePrompt.collectAsStateWithLifecycle()
    val customDisplayName by viewModel.customDisplayName.collectAsStateWithLifecycle()
    val hiddenSections by viewModel.hiddenSections.collectAsStateWithLifecycle()
    val credentials = viewModel.credentials

    LaunchedEffect(Unit) {
        viewModel.checkUpdatesForDashboard()
    }

    val expDateFormatted = remember(accountInfo) {
        accountInfo?.expDate?.let { 
            try {
                val timestamp = it.toLong()
                if (timestamp <= 0 || timestamp > 4_000_000_000_000L) "UNLIMITED"
                else {
                    val date = if (it.length <= 10) Date(timestamp * 1000) else Date(timestamp)
                    SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(date)
                }
            } catch (_: Exception) { "N/A" }
        } ?: "N/A"
    }

    val dashboardActions = DashboardActions(
        onNavigateToCategory = onNavigateToCategory,
        onNavigateToMultiView = onNavigateToMultiView,
        onNavigateToEpgGrid = onNavigateToEpgGrid,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToAccount = onNavigateToAccount,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToDownloads = onNavigateToDownloads,
        onRefresh = { viewModel.loadData(force = true) }
    )

    PortalBackground(showAtmosphere = true) {
        DashboardContent(
            windowInfo = windowInfo,
            sessionStatus = sessionStatus,
            syncProgress = progress,
            isContentReady = isContentReady,
            newlyAdded = newlyAdded,
            watchProgress = watchProgress,
            activeProfile = activeProfile,
            accountInfo = accountInfo,
            credentials = credentials,
            customDisplayName = customDisplayName,
            hiddenSections = hiddenSections,
            expDate = expDateFormatted,
            actions = dashboardActions
        )

        if (availableUpdatePrompt != null) {
            val updateInfo = availableUpdatePrompt!!
            DashboardUpdateModal(
                updateInfo = updateInfo,
                onUpdate = {
                    viewModel.dismissUpdateModal()
                    onNavigateToUpdates()
                },
                onIgnore = {
                    viewModel.ignoreUpdateVersion(updateInfo.versionName)
                },
                onDismiss = {
                    viewModel.dismissUpdateModal()
                }
            )
        }
    }
}

data class DashboardActions(
    val onNavigateToCategory: (String, String?) -> Unit,
    val onNavigateToMultiView: () -> Unit,
    val onNavigateToEpgGrid: () -> Unit,
    val onNavigateToSearch: () -> Unit,
    val onNavigateToAccount: () -> Unit,
    val onNavigateToSettings: () -> Unit,
    val onNavigateToDownloads: () -> Unit,
    val onRefresh: () -> Unit
)

@UnstableApi
@Composable
fun DashboardContent(
    windowInfo: WindowInfo,
    sessionStatus: SessionStatus,
    syncProgress: AppSyncProgress,
    isContentReady: Boolean,
    newlyAdded: Map<ContentType, List<IptvItem>>,
    watchProgress: Map<Int, Float>,
    activeProfile: UserProfile?,
    accountInfo: UserInfo?,
    credentials: XtreamCredentials?,
    customDisplayName: String? = null,
    hiddenSections: Set<String> = emptySet(),
    expDate: String,
    actions: DashboardActions
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    val firstItemFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    
    // Use responsive metrics instead of fixed screen width checks
    val isNarrow = responsive.widthDp < 600.dp
    
    val horizontalPadding = responsive.horizontalPadding
    val verticalPadding = responsive.verticalPadding
    val itemSpacing = responsive.dp(16.dp)
    
    val titleSize = responsive.sp(tokens.typography.display.fontSize)
    val subtitleSize = responsive.sp(tokens.typography.body.fontSize)
    
    val mainCardHeight = responsive.dp(280.dp)
    val secondaryCardHeight = responsive.dp(150.dp)

    LaunchedEffect(Unit) {
        delay(500)
        try { firstItemFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- CINEMATIC HEADER (Unified & Responsive) ---
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left Side: User Profile (Informational only)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(responsive.dp(if (isNarrow) 48.dp else 64.dp)),
                        shape = CircleShape,
                        color = tokens.colors.primary.copy(alpha = 0.1f),
                        border = BorderStroke(responsive.dp(2.dp), tokens.colors.primary.copy(alpha = 0.3f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = tokens.colors.primary,
                                modifier = Modifier.size(responsive.dp(if (isNarrow) 32.dp else 40.dp))
                            )
                        }
                    }
                    Spacer(Modifier.width(responsive.dp(tokens.spacing.large)))
                    Column {
                        val nameToDisplay = customDisplayName?.takeIf { it.isNotBlank() } ?: accountInfo?.username ?: credentials?.username ?: activeProfile?.name ?: "USUÁRIO"
                        Text(
                            text = "OLÁ, ${nameToDisplay.uppercase()}",
                            style = tokens.typography.display.copy(fontSize = titleSize),
                            fontWeight = FontWeight.Black,
                            color = tokens.colors.textPrimary
                        )
                        Text(
                            text = "PLANO PREMIUM • EXPIRA EM: $expDate",
                            style = tokens.typography.caption.copy(fontSize = subtitleSize * 0.8f),
                            color = tokens.colors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Right Side: Quick Actions & Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.large))
                ) {
                    if (!syncProgress.isComplete && !isNarrow && !isContentReady) {
                        SyncIndicator()
                    }
                    
                    AppIconButton(
                        icon = Icons.Rounded.Search, 
                        onClick = actions.onNavigateToSearch,
                        modifier = Modifier.size(responsive.dp(48.dp))
                    )
                    if (!isNarrow) {
                        AppIconButton(
                            icon = Icons.Rounded.Notifications, 
                            onClick = { /* Notifications */ },
                            modifier = Modifier.size(responsive.dp(48.dp))
                        )
                    }
                    AppIconButton(
                        icon = Icons.Rounded.Settings, 
                        onClick = actions.onNavigateToSettings,
                        modifier = Modifier.size(responsive.dp(48.dp))
                    )
                    
                    Spacer(Modifier.width(responsive.dp(tokens.spacing.medium)))
                    
                    DigitalClock(
                        textStyle = tokens.typography.headline.copy(fontSize = titleSize, fontWeight = FontWeight.Black),
                        format = "HH:mm"
                    )
                }
            }

            if (sessionStatus == SessionStatus.OFFLINE) {
                OfflineBanner()
            }
        }

        // --- LINE 1: MAIN CATEGORIES (Adaptive Flow/Row) ---
        val allMainCards = listOf(
            Triple("LIVE_TV", stringResource(R.string.live_tv_title), stringResource(R.string.live_tv_desc) to ThemeLive),
            Triple("MOVIES", stringResource(R.string.movies_title), stringResource(R.string.movies_desc) to ThemeMovies),
            Triple("SERIES", stringResource(R.string.series_title), stringResource(R.string.series_desc) to ThemeSeries)
        )
        val mainCards = allMainCards.filter { (key, _, _) -> key !in hiddenSections }

        if (mainCards.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                mainCards.forEachIndexed { index, (key, title, descColor) ->
                    val (desc, color) = descColor
                    CinematicActionCard(
                        title = title,
                        description = if (isNarrow) "" else desc,
                        icon = when(key) {
                            "LIVE_TV" -> Icons.Rounded.Monitor
                            "MOVIES" -> Icons.Rounded.Movie
                            else -> Icons.Rounded.VideoLibrary
                        },
                        cardType = when(key) {
                            "LIVE_TV" -> HomeCardType.LIVE_TV
                            "MOVIES" -> HomeCardType.MOVIES
                            else -> HomeCardType.SERIES
                        },
                        themeColor = color,
                        isLarge = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(mainCardHeight)
                            .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
                        onClick = { 
                            when(key) {
                                "LIVE_TV" -> actions.onNavigateToCategory("live", null)
                                "MOVIES" -> actions.onNavigateToCategory("movie", null)
                                else -> actions.onNavigateToCategory("series", null)
                            }
                        }
                    )
                }
            }
        }

        // --- LINE 2: SECONDARY TOOLS (Adaptive Grid) ---
        val downloadsLabel = stringResource(R.string.downloads_label)
        val favoritesLabel = stringResource(R.string.favorites_label)

        val allSecondaryCards = listOf(
            Triple("EPG", "EPG", Icons.AutoMirrored.Rounded.ListAlt to ThemeEPG),
            Triple("MULTI_VIEW", "MULTI-VIEW", Icons.Rounded.GridView to ThemeMultiView),
            Triple("DOWNLOADS", downloadsLabel, Icons.Rounded.Download to ThemeDownloads),
            Triple("FAVORITES", favoritesLabel, Icons.Rounded.Favorite to ThemeFavorites)
        )
        val secondaryCards = allSecondaryCards.filter { (key, _, _) -> key !in hiddenSections }

        if (secondaryCards.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                secondaryCards.forEachIndexed { index, (key, title, iconColor) ->
                    val (icon, color) = iconColor
                    CinematicActionCard(
                        title = title,
                        description = "",
                        icon = icon,
                        cardType = when(key) {
                            "EPG" -> HomeCardType.EPG
                            "MULTI_VIEW" -> HomeCardType.MULTI_VIEW
                            "DOWNLOADS" -> HomeCardType.DOWNLOADS
                            else -> HomeCardType.FAVORITES
                        },
                        themeColor = color,
                        modifier = Modifier
                            .weight(1f)
                            .height(secondaryCardHeight)
                            .then(if (mainCards.isEmpty() && index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
                        onClick = {
                            when(key) {
                                "EPG" -> actions.onNavigateToEpgGrid()
                                "MULTI_VIEW" -> actions.onNavigateToMultiView()
                                "DOWNLOADS" -> actions.onNavigateToDownloads()
                                else -> actions.onNavigateToCategory("favorites", null)
                            }
                        }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(responsive.dp(tokens.spacing.small)))
    }
}

@Composable
fun DashboardUpdateModal(
    updateInfo: rsv.squitv.domain.model.AppUpdateInfo,
    onUpdate: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit
) {
    rsv.squitv.ui.updates.UpdatePrompt(updateInfo, onUpdate, onIgnore, onDismiss)
}
