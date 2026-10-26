package rsv.squitv.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import rsv.squitv.R
import rsv.squitv.core.domain.state.AppSyncProgress
import rsv.squitv.core.domain.state.AppSyncStatus
import rsv.squitv.core.domain.state.AppState
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.states.BrandedLoadingScreen
import rsv.squitv.core.ui.components.states.ErrorState
import rsv.squitv.core.ui.theme.*
import rsv.squitv.domain.model.SessionStatus
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.util.rememberWindowInfo
import timber.log.Timber
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun SyncScreen(
    viewModel: MainViewModel,
    onSyncComplete: () -> Unit
) {
    val progress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
    val appState by viewModel.appState.collectAsStateWithLifecycle()
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    
    val errorFocusRequester = remember { FocusRequester() }
    val continueFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        Timber.d("SyncScreen: ENTERED. progress.isComplete=${progress.isComplete}")
        if (!progress.isComplete) {
            viewModel.loadData()
        }
    }

    // Auto-advance if app state becomes Ready while we are here
    LaunchedEffect(appState) {
        if (appState is AppState.Ready) {
            Timber.i("SyncScreen: AppState is READY. Auto-triggering navigation.")
            onSyncComplete()
        }
    }

    LaunchedEffect(progress.error) {
        if (progress.error != null) {
            delay(500)
            try { errorFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(progress.isComplete) {
        if (progress.isComplete) {
            delay(500)
            try { continueFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val isAuthValid = sessionStatus == SessionStatus.VALID ||
                     sessionStatus == SessionStatus.OFFLINE

    if (!isAuthValid && !progress.isComplete) {
        BrandedLoadingScreen(message = stringResource(R.string.msg_connecting))
        return
    }

    PortalBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(responsive.dp(tokens.spacing.giant)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (progress.error != null) {
                ErrorState(
                    message = progress.error ?: stringResource(R.string.unknown),
                    onRetry = { viewModel.loadData() },
                    modifier = Modifier.focusRequester(errorFocusRequester)
                )
            } else {
                // Header with Logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = responsive.dp(tokens.spacing.huge))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(responsive.dp(80.dp)),
                        tint = tokens.colors.primary
                    )
                    Spacer(modifier = Modifier.width(responsive.dp(tokens.spacing.large)))
                    Column {
                        Text(
                            text = stringResource(R.string.sync_title).uppercase(),
                            style = tokens.typography.display.copy(fontSize = responsive.sp(tokens.typography.display.fontSize)),
                            color = tokens.colors.textPrimary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = responsive.sp(6.sp)
                        )
                        Text(
                            text = stringResource(R.string.sync_subtitle).uppercase(),
                            style = tokens.typography.body.copy(fontSize = responsive.sp(tokens.typography.body.fontSize)),
                            color = tokens.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = responsive.sp(1.sp)
                        )
                    }
                }

                // Status Grid
                val columns = if (responsive.widthDp > 1000.dp) 4 else if (responsive.widthDp > 600.dp) 2 else 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxWidth(if (responsive.widthDp > 800.dp) 0.85f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.large)),
                    verticalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.medium)),
                    contentPadding = PaddingValues(responsive.dp(tokens.spacing.medium))
                ) {
                    item { SyncItem(stringResource(R.string.sync_account), progress.accountStatus) }
                    item { SyncItem(stringResource(R.string.sync_live), progress.liveStatus) }
                    item { SyncItem(stringResource(R.string.sync_movies), progress.vodStatus) }
                    item { SyncItem(stringResource(R.string.sync_series), progress.seriesStatus) }
                    item { SyncItem(stringResource(R.string.sync_epg), progress.epgStatus) }
                }

                Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.huge)))
                
                // Overall Progress Indicator
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(if (responsive.widthDp > 800.dp) 0.6f else 0.9f)
                        .height(responsive.dp(8.dp))
                        .clip(CircleShape),
                    color = tokens.colors.primary,
                    trackColor = Color.White.copy(alpha = 0.05f)
                )

                if (progress.isComplete) {
                    Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.extraLarge)))
                    AppButton(
                        text = stringResource(R.string.continue_anyway_button).uppercase(),
                        onClick = onSyncComplete,
                        modifier = Modifier
                            .width(responsive.dp(300.dp))
                            .focusRequester(continueFocusRequester)
                    )
                }
            }
        }
    }
}

@Composable
fun SyncItem(label: String, status: AppSyncStatus) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    val isCompleted = status is AppSyncStatus.Done || status is AppSyncStatus.Success || status is AppSyncStatus.OfflineMode
    val isLoading = status is AppSyncStatus.Syncing || status is AppSyncStatus.Fetching
    val isError = status is AppSyncStatus.Error
    
    val statusText = when (status) {
        is AppSyncStatus.Skipped -> stringResource(R.string.sync_skipped)
        is AppSyncStatus.Pending -> stringResource(R.string.status_pending_sync)
        is AppSyncStatus.Fetching -> stringResource(R.string.status_fetching)
        is AppSyncStatus.Syncing -> stringResource(R.string.status_syncing)
        is AppSyncStatus.Success -> stringResource(R.string.status_success)
        is AppSyncStatus.OfflineMode -> "OFFLINE MODE"
        is AppSyncStatus.Done -> stringResource(R.string.status_done_format, status.count)
        is AppSyncStatus.Error -> stringResource(R.string.status_error_format, status.message)
    }

    Surface(
        color = if (isLoading) tokens.colors.primary.copy(alpha = 0.08f) else tokens.colors.surface.copy(alpha = 0.15f),
        shape = tokens.shapes.medium,
        border = if (isLoading) BorderStroke(responsive.dp(1.5.dp), tokens.colors.primary.copy(alpha = 0.4f)) else BorderStroke(responsive.dp(1.dp), Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(responsive.dp(tokens.spacing.large)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    isCompleted -> Icons.Rounded.CheckCircle
                    isLoading -> Icons.Rounded.CloudSync
                    isError -> Icons.Rounded.Error
                    else -> Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = when {
                    isCompleted -> tokens.colors.secondary
                    isError -> tokens.colors.error
                    isLoading -> tokens.colors.primary
                    else -> tokens.colors.textSecondary.copy(alpha = 0.3f)
                },
                modifier = Modifier.size(responsive.dp(32.dp))
            )
            
            Spacer(modifier = Modifier.width(responsive.dp(tokens.spacing.medium)))
            
            Column {
                Text(
                    text = label.uppercase(),
                    style = tokens.typography.title.copy(fontSize = responsive.sp(tokens.typography.title.fontSize)),
                    color = if (isLoading) tokens.colors.primary else tokens.colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = responsive.sp(0.5.sp)
                )
                Text(
                    text = statusText.uppercase(),
                    style = tokens.typography.label.copy(fontSize = responsive.sp(tokens.typography.label.fontSize)),
                    color = when {
                        isCompleted -> tokens.colors.secondary.copy(alpha = 0.8f)
                        isError -> tokens.colors.error.copy(alpha = 0.8f)
                        else -> tokens.colors.textSecondary.copy(alpha = 0.5f)
                    },
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
