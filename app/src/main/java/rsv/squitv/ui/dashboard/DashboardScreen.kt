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
import androidx.compose.ui.draw.clip
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
                        Text(
                            text = "OLÁ, ${accountInfo?.username?.uppercase() ?: credentials?.username?.uppercase() ?: activeProfile?.name?.uppercase() ?: "USUÁRIO"}",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            val mainCards = listOf(
                Triple(stringResource(R.string.live_tv_title), stringResource(R.string.live_tv_desc), ThemeLive),
                Triple(stringResource(R.string.movies_title), stringResource(R.string.movies_desc), ThemeMovies),
                Triple(stringResource(R.string.series_title), stringResource(R.string.series_desc), ThemeSeries)
            )
            
            mainCards.forEachIndexed { index, (title, desc, color) ->
                CinematicActionCard(
                    title = title,
                    description = if (isNarrow) "" else desc,
                    icon = when(index) {
                        0 -> Icons.Rounded.Monitor
                        1 -> Icons.Rounded.Movie
                        else -> Icons.Rounded.VideoLibrary
                    },
                    cardType = when(index) {
                        0 -> HomeCardType.LIVE_TV
                        1 -> HomeCardType.MOVIES
                        else -> HomeCardType.SERIES
                    },
                    themeColor = color,
                    isLarge = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(mainCardHeight)
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
                    onClick = { 
                        when(index) {
                            0 -> actions.onNavigateToCategory("live", null)
                            1 -> actions.onNavigateToCategory("movie", null)
                            else -> actions.onNavigateToCategory("series", null)
                        }
                    }
                )
            }
        }

        // --- LINE 2: SECONDARY TOOLS (Adaptive Grid) ---
    val downloadsLabel = stringResource(R.string.downloads_label)
    val favoritesLabel = stringResource(R.string.favorites_label)

    val secondaryCards = listOf(
        Triple("EPG", Icons.AutoMirrored.Rounded.ListAlt, ThemeEPG),
        Triple("MULTI-VIEW", Icons.Rounded.GridView, ThemeMultiView),
        Triple(downloadsLabel, Icons.Rounded.Download, ThemeDownloads),
        Triple(favoritesLabel, Icons.Rounded.Favorite, ThemeFavorites)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        secondaryCards.forEach { (title, icon, color) ->
            CinematicActionCard(
                title = title,
                description = "",
                icon = icon,
                cardType = when(title) {
                    "EPG" -> HomeCardType.EPG
                    "MULTI-VIEW" -> HomeCardType.MULTI_VIEW
                    downloadsLabel -> HomeCardType.DOWNLOADS
                    else -> HomeCardType.FAVORITES
                },
                themeColor = color,
                modifier = Modifier
                    .weight(1f)
                    .height(secondaryCardHeight),
                onClick = {
                    when(title) {
                        "EPG" -> actions.onNavigateToEpgGrid()
                        "MULTI-VIEW" -> actions.onNavigateToMultiView()
                        downloadsLabel -> actions.onNavigateToDownloads()
                        else -> actions.onNavigateToCategory("favorites", null)
                    }
                }
            )
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
    val tokens = AppDesignSystem
    val updateButtonFocusRequester = remember { FocusRequester() }
    var timeLeftSeconds by remember(updateInfo.versionName) { mutableIntStateOf(5) }

    LaunchedEffect(updateInfo.versionName) {
        delay(300)
        try { updateButtonFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(updateInfo.versionName) {
        while (timeLeftSeconds > 0) {
            delay(1000)
            timeLeftSeconds--
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1322).copy(alpha = 0.95f),
        shape = tokens.shapes.extraLarge,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = tokens.colors.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "NOVA ATUALIZAÇÃO DISPONÍVEL!",
                    style = tokens.typography.headline.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Black,
                    color = tokens.colors.textPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = tokens.colors.primary.copy(alpha = 0.15f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, tokens.colors.primary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "VERSÃO ${updateInfo.versionName.uppercase()}",
                        style = tokens.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Black),
                        color = tokens.colors.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "NOVIDADES DESSA VERSÃO:",
                    style = tokens.typography.label.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textSecondary
                )

                // CHANGELOG BOX
                Surface(
                    color = tokens.colors.surfaceElevated.copy(alpha = 0.5f),
                    shape = tokens.shapes.medium,
                    border = BorderStroke(1.dp, tokens.colors.border.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                ) {
                    val rawChangelog = updateInfo.changelog.trim()
                    val displayChangelog = if (rawChangelog.isBlank()) {
                        "Detalhes das alterações não fornecidos."
                    } else {
                        rawChangelog
                    }

                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = displayChangelog,
                            style = tokens.typography.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = tokens.colors.textPrimary
                        )
                    }
                }

                // TIMER PROGRESS INDICATOR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { timeLeftSeconds / 5f },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = tokens.colors.primary,
                        trackColor = tokens.colors.textSecondary.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Fechando em ${timeLeftSeconds}s",
                        style = tokens.typography.caption.copy(fontSize = 10.sp),
                        color = tokens.colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            rsv.squitv.core.ui.components.buttons.AppButton(
                text = "ATUALIZAR",
                onClick = onUpdate,
                useGradient = true,
                icon = Icons.Rounded.SystemUpdate,
                modifier = Modifier
                    .width(150.dp)
                    .height(48.dp)
                    .focusRequester(updateButtonFocusRequester)
            )
        },
        dismissButton = {
            TextButton(
                onClick = onIgnore,
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "IGNORAR",
                    color = tokens.colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    )
}
