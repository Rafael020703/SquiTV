package rsv.squitv

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.core.domain.interactor.AuthManager
import rsv.squitv.core.domain.interactor.DnsManager
import rsv.squitv.core.domain.state.AppSyncStatus
import rsv.squitv.data.model.*
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.model.SessionStatus
import rsv.squitv.domain.usecase.SyncDataUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class SyncOrchestrationTest {
    private val credentials = XtreamCredentials("test", "test", "https://example.invalid")
    private val settings = MutableStateFlow(SettingsRepository.AppSettings(credentials, lastSyncTimestamp = 0, syncIntervalHours = 24))
    private val settingsRepository = mock(SettingsRepository::class.java)
    private val catalog = mock(CatalogRepository::class.java)
    private val epg = mock(EpgRepository::class.java)
    private val auth = mock(AuthManager::class.java)
    private val dns = mock(DnsManager::class.java)
    private lateinit var useCase: SyncDataUseCase
    private fun anyCallback(): suspend () -> Unit = any<suspend () -> Unit>() ?: {}

    @Before fun setup() = runBlocking {
        doReturn(settings).`when`(settingsRepository).settingsFlow
        doReturn(Triple(1, 2, 3)).`when`(catalog).getStats()
        doReturn(XtreamResponse(UserInfo(auth = 1, status = "Active"))).`when`(auth).login(credentials)
        for (type in listOf("LIVE", "VOD", "SERIES")) {
            doReturn(5).`when`(catalog).syncCatalogType(eq(credentials) ?: credentials, eq(type) ?: type, anyCallback())
        }
        doReturn(10).`when`(epg).syncFullEpg(eq(credentials) ?: credentials, anyCallback())
        useCase = SyncDataUseCase(catalog, epg, dns, auth, settingsRepository, mock(Context::class.java))
    }

    @Test fun forcedSyncRespectsUnrequestedTypesAndPublishesACompleteResult() = runTest {
        val result = useCase(force = true, syncLive = false, syncSeries = false).last()
        assertTrue(result.isComplete)
        assertNull(result.error)
        assertEquals(AppSyncStatus.Done(5), result.vodStatus)
        assertEquals(AppSyncStatus.Skipped, result.liveStatus)
        assertEquals(AppSyncStatus.Skipped, result.epgStatus)
        verify(catalog, never()).syncCatalogType(eq(credentials) ?: credentials, eq("LIVE") ?: "LIVE", anyCallback())
        verify(epg, never()).syncFullEpg(eq(credentials) ?: credentials, anyCallback())
    }

    @Test fun rejectedAuthenticationDoesNotDownloadOrUpdateSuccessTimestamps() = runTest {
        doReturn(XtreamResponse(UserInfo(auth = 0))).`when`(auth).login(credentials)
        val result = useCase(force = true).last()
        assertEquals(SessionStatus.INVALID, result.sessionStatus)
        assertNotNull(result.error)
        assertFalse(result.retryable)
        verify(catalog, never()).syncCatalogType(eq(credentials) ?: credentials, eq("LIVE") ?: "LIVE", anyCallback())
        verify(settingsRepository, never()).updateLastSyncTimestamp(anyLong())
    }

    @Test fun expiredAccountIsNotReplacedByValidSession() = runTest {
        doReturn(XtreamResponse(UserInfo(auth = 1, expDate = "1"))).`when`(auth).login(credentials)
        assertEquals(SessionStatus.EXPIRED, useCase(force = true).last().sessionStatus)
        verify(settingsRepository, never()).updateLastSyncTimestamp(anyLong())
    }

    @Test fun offlineSyncKeepsCachedReadinessButReportsFailure() = runTest {
        doAnswer { throw IOException("offline") }.`when`(auth).login(credentials)
        val result = useCase(force = true).last()
        assertEquals(SessionStatus.OFFLINE, result.sessionStatus)
        assertTrue(result.isContentReady)
        assertTrue(result.retryable)
        assertNotNull(result.error)
        verify(settingsRepository, never()).updateLastSyncTimestamp(anyLong())
    }

    @Test fun epgFailureProducesPartialFailureWithoutMarkingGlobalSuccess() = runTest {
        doAnswer { throw IOException("EPG unavailable") }.`when`(epg).syncFullEpg(eq(credentials) ?: credentials, anyCallback())
        val result = useCase(force = true).last()
        assertTrue(result.epgStatus is AppSyncStatus.Error)
        assertEquals(AppSyncStatus.Done(5), result.liveStatus)
        assertNotNull(result.error)
        assertTrue(result.retryable)
        verify(settingsRepository, never()).updateLastSyncTimestamp(anyLong())
        verify(settingsRepository).updateSyncState("LIVE", true)
    }

    @Test fun missingCredentialsStillEmitATerminalError() = runTest {
        settings.value = settings.value.copy(credentials = null)
        val result = useCase(force = true).last()
        assertTrue(result.isComplete)
        assertNotNull(result.error)
        verifyNoInteractions(auth)
    }

    @Test fun concurrentRunWaitsAndCancellationReleasesTheCoordinator() = runTest {
        val entered = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        val first = launch {
            useCase(force = true).onEach { if (!entered.isCompleted) { entered.complete(Unit); hold.await() } }.collect()
        }
        entered.await()
        val second = async { useCase(force = true, syncLive = false, syncSeries = false).last() }
        runCurrent()
        assertFalse(second.isCompleted)
        verifyNoInteractions(auth)
        first.cancelAndJoin()
        assertTrue(second.await().isComplete)
        verify(auth, times(1)).login(credentials)
    }

    @Test fun accountChangeDuringLoginCancelsWithoutPublishingOrReportingSuccess(): Unit = runTest {
        doAnswer {
            settings.value = settings.value.copy(credentials = credentials.copy(username = "another"))
            XtreamResponse(UserInfo(auth = 1))
        }.`when`(auth).login(credentials)
        try { useCase(force = true).collect(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertTrue(useCase.progress.value?.isCancelled == true)
        verify(settingsRepository, never()).updateLastSyncTimestamp(anyLong())
    }

    @Test fun staleEpgRetriesWithoutRedownloadingFreshCatalogTypes() = runTest {
        val now = System.currentTimeMillis()
        settings.value = settings.value.copy(lastSyncTimestamp = now, lastSyncLive = now,
            lastSyncVod = now, lastSyncSeries = now, lastSyncEpg = 0)
        val result = useCase().last()
        assertEquals(AppSyncStatus.Skipped, result.liveStatus)
        assertEquals(AppSyncStatus.Done(10), result.epgStatus)
        verify(catalog, never()).syncCatalogType(eq(credentials) ?: credentials, eq("LIVE") ?: "LIVE", anyCallback())
    }

    @Test fun failureInOneTypeStillAllowsOtherTypesToComplete() = runTest {
        doAnswer { throw IOException("Live unavailable") }.`when`(catalog)
            .syncCatalogType(eq(credentials) ?: credentials, eq("LIVE") ?: "LIVE", anyCallback())
        val result = useCase(force = true).last()
        assertTrue(result.liveStatus is AppSyncStatus.Error)
        assertEquals(AppSyncStatus.Done(5), result.vodStatus)
        assertEquals(AppSyncStatus.Done(5), result.seriesStatus)
        assertNotNull(result.error)
        verify(settingsRepository, never()).updateLastSyncTimestamp(anyLong())
    }
}
