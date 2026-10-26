package rsv.squitv.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import rsv.squitv.domain.usecase.SyncDataUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncDataUseCase: SyncDataUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean("force", false)
        Timber.d("Iniciando SyncWorker (force=%b)", force)
        
        return try {
            var finalResult: SyncDataUseCase.SyncResult? = null
            syncDataUseCase(force,
                syncLive = inputData.getBoolean("syncLive", true),
                syncVod = inputData.getBoolean("syncVod", true),
                syncSeries = inputData.getBoolean("syncSeries", true)).collect {
                if (it.isComplete) finalResult = it
            }
            val result = finalResult
            when {
                result == null || result.isCancelled -> Result.failure()
                result.error == null -> Result.success()
                result.retryable && runAttemptCount < 3 -> Result.retry()
                else -> Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Erro no SyncWorker")
            if (SyncDataUseCase.isRetryable(e) && runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
