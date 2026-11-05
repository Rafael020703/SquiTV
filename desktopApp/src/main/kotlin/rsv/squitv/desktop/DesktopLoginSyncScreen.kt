package rsv.squitv.desktop

import androidx.compose.runtime.Composable
import rsv.squitv.core.domain.state.AppSyncProgress
import rsv.squitv.core.domain.state.AppSyncStatus
import rsv.squitv.core.ui.components.sync.CatalogSyncPanel
import rsv.squitv.core.ui.components.sync.SyncStage

@Composable
internal fun DesktopLoginSyncScreen(progress: AppSyncProgress, controller: DesktopController) {
    val stages = listOf("Conta" to progress.accountStatus, "Categorias de TV ao vivo" to progress.liveStatus,
        "Categorias de filmes" to progress.vodStatus, "Categorias de séries" to progress.seriesStatus)
        .map { (label, status) -> SyncStage(label, status, when (status) {
            AppSyncStatus.Pending -> "Aguardando"
            AppSyncStatus.Fetching, AppSyncStatus.Syncing -> "Carregando do servidor…"
            AppSyncStatus.Success -> "Conta conectada"
            is AppSyncStatus.Done -> "${status.count} categorias carregadas"
            is AppSyncStatus.Error -> status.message
            AppSyncStatus.OfflineMode -> "Dados anteriores preservados"
            AppSyncStatus.Skipped -> "Não solicitado"
        }) }
    CatalogSyncPanel("SINCRONIZANDO CATÁLOGO",
        "Preparando as categorias. Os títulos e detalhes são carregados ao abrir cada catálogo.",
        stages, progress.isRunning, progress.error, progress.isCancelled,
        onRetry = if (!progress.isRunning) controller::retryLoginSync else null,
        onCancel = controller::cancelConnection,
        onContinue = if (!progress.isRunning && listOf(progress.liveStatus, progress.vodStatus, progress.seriesStatus)
                .any { it is AppSyncStatus.Done }) controller::continueLoginSync else null)
}
