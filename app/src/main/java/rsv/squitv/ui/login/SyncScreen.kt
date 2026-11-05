package rsv.squitv.ui.login

import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import rsv.squitv.R
import rsv.squitv.core.domain.state.AppState
import rsv.squitv.core.domain.state.AppSyncProgress
import rsv.squitv.core.domain.state.AppSyncStatus
import rsv.squitv.core.ui.components.sync.CatalogSyncPanel
import rsv.squitv.core.ui.components.sync.SyncStage
import rsv.squitv.core.ui.components.sync.SyncStageCard
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.MainViewModel

@OptIn(UnstableApi::class)
@Composable
fun SyncScreen(viewModel: MainViewModel, onSyncComplete: () -> Unit) {
    val progress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val appState by viewModel.appState.collectAsStateWithLifecycle()
    val contentReady by viewModel.isContentReady.collectAsStateWithLifecycle()
    var requested by remember { mutableStateOf(false) }
    LaunchedEffect(appState) {
        if (appState is AppState.SyncRequired && !requested) {
            requested = true
            // Completion from an earlier login never replaces the new entry's request.
            viewModel.loadData()
        }
    }
    val current = if (!requested && appState is AppState.SyncRequired) AppSyncProgress(isRunning = true) else progress
    val labels = listOf(R.string.sync_account, R.string.sync_live, R.string.sync_movies, R.string.sync_series, R.string.sync_epg)
    val statuses = listOf(current.accountStatus, current.liveStatus, current.vodStatus, current.seriesStatus, current.epgStatus)
    val stages = labels.zip(statuses).map { (label, status) ->
        SyncStage(stringResource(label), status, syncStatusText(status))
    }
    // MainNavigation owns automatic Ready navigation. This screen only exposes explicit continuation.
    PortalBackground {
        CatalogSyncPanel(title = stringResource(R.string.sync_title), subtitle = stringResource(R.string.sync_subtitle),
            stages = stages, running = current.isRunning, error = current.error, cancelled = current.isCancelled,
            onRetry = if (current.isComplete && (current.error != null || current.isCancelled))
                ({ viewModel.loadData(force = true) }) else null,
            onContinue = if (current.isComplete && contentReady) onSyncComplete else null)
    }
}

@Composable
private fun syncStatusText(status: AppSyncStatus): String = when (status) {
    AppSyncStatus.Skipped -> stringResource(R.string.sync_skipped)
    AppSyncStatus.Pending -> stringResource(R.string.status_pending_sync)
    AppSyncStatus.Fetching -> stringResource(R.string.status_fetching)
    AppSyncStatus.Syncing -> stringResource(R.string.status_syncing)
    AppSyncStatus.Success -> stringResource(R.string.status_success)
    AppSyncStatus.OfflineMode -> "Catálogo anterior preservado"
    is AppSyncStatus.Done -> stringResource(R.string.status_done_format, status.count)
    is AppSyncStatus.Error -> stringResource(R.string.status_error_format, status.message)
}

@Composable
fun SyncItem(label: String, status: AppSyncStatus) {
    SyncStageCard(SyncStage(label, status, syncStatusText(status)), Modifier.fillMaxWidth())
}
