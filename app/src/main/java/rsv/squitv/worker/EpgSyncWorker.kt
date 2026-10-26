package rsv.squitv.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import rsv.squitv.domain.usecase.SyncDataUseCase
import timber.log.Timber

@HiltWorker
class EpgSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val epgRepository: EpgRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("Iniciando EpgSyncWorker")
        val creds = settingsRepository.settingsFlow.first().credentials ?: return Result.failure()
        
        return try {
            epgRepository.syncFullEpg(creds) {
                if (settingsRepository.settingsFlow.first().credentials != creds) {
                    throw CancellationException("Conta alterada")
                }
            }
            settingsRepository.updateSyncState("epg", true)
            Timber.i("EpgSyncWorker concluído")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Falha no EpgSyncWorker")
            if (SyncDataUseCase.isRetryable(e) && runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
