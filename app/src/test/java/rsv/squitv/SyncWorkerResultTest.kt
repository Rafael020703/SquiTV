package rsv.squitv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.domain.usecase.SyncDataUseCase
import rsv.squitv.worker.SyncWorker

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SyncWorkerResultTest {
    private val useCase = mock(SyncDataUseCase::class.java)
    private val parameters = mock(WorkerParameters::class.java)

    private fun worker(attempt: Int = 0): SyncWorker {
        doReturn(workDataOf("force" to true, "syncLive" to false, "syncVod" to true, "syncSeries" to false))
            .`when`(parameters).inputData
        doReturn(attempt).`when`(parameters).runAttemptCount
        return SyncWorker(ApplicationProvider.getApplicationContext(), parameters, useCase)
    }

    @Test fun workerPassesSelectedTypesAndReportsSuccessOnlyForCompletedSuccess(): Unit = runBlocking {
        doReturn(flowOf(SyncDataUseCase.SyncResult(isComplete = true)))
            .`when`(useCase).invoke(true, false, true, false)
        assertEquals(Result.success(), worker().doWork())
        verify(useCase).invoke(true, false, true, false)
    }

    @Test fun emittedPermanentFailureIsNotReportedAsSuccess(): Unit = runBlocking {
        doReturn(flowOf(SyncDataUseCase.SyncResult(isComplete = true, error = "Invalid account")))
            .`when`(useCase).invoke(true, false, true, false)
        assertEquals(Result.failure(), worker().doWork())
    }

    @Test fun transientFailureRetriesWithinTheAttemptLimit(): Unit = runBlocking {
        doReturn(flowOf(SyncDataUseCase.SyncResult(isComplete = true, error = "Offline", retryable = true)))
            .`when`(useCase).invoke(true, false, true, false)
        assertEquals(Result.retry(), worker().doWork())
        assertEquals(Result.failure(), worker(3).doWork())
    }

    @Test fun cancellationPropagatesToWorkManager(): Unit = runBlocking {
        doAnswer { throw CancellationException("Stopped") }.`when`(useCase).invoke(true, false, true, false)
        try { worker().doWork(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
}
