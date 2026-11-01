package rsv.squitv

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import rsv.squitv.core.data.repository.*
import rsv.squitv.data.model.*
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.usecase.GetQuickSwitchUseCase

class GetQuickSwitchUseCaseTest {
    private val credentials = XtreamCredentials("user", "test", "https://provider.invalid")
    private val catalog = mock(CatalogRepository::class.java)
    private val epg = mock(EpgRepository::class.java)
    private val users = mock(UserRepository::class.java)
    private val settings = mock(SettingsRepository::class.java).also {
        `when`(it.settingsFlow).thenReturn(flowOf(SettingsRepository.AppSettings(
            credentials, lastSyncTimestamp = 0L, syncIntervalHours = 24)))
    }
    private val useCase = GetQuickSwitchUseCase(catalog, epg, settings, users)

    @Test fun emptySpecialSourcesNeverRequestTheGlobalCatalogue() = runBlocking {
        `when`(users.getFavoritesListFlow()).thenReturn(flowOf(emptyList()))
        `when`(users.getAllWatchProgressFlow()).thenReturn(flowOf(emptyList()))
        listOf("FAVORITES", "HISTORY", "CONTINUE_WATCHING", "SEARCH:").forEach {
            assertTrue(useCase.getStreams(it).isEmpty())
        }
        verifyNoInteractions(catalog)
    }

    @Test fun emptyCategoryNeverFallsBackToAllChannels() = runBlocking {
        `when`(catalog.getLiveStreams(credentials, "missing")).thenReturn(emptyList())
        assertTrue(useCase.getStreams("missing").isEmpty())
        verify(catalog).getLiveStreams(credentials, "missing")
        verifyNoMoreInteractions(catalog)
    }

    @Test fun favoritesKeepTheirExactIdsAndOrderWithoutAddingOtherVersions() = runBlocking {
        `when`(users.getFavoritesListFlow()).thenReturn(flowOf(listOf(
            rsv.squitv.data.local.entities.FavoriteEntity(9, "LIVE", "Z HD", categoryId = "a"),
            rsv.squitv.data.local.entities.FavoriteEntity(2, "LIVE", "A SD", categoryId = "a"))))
        `when`(catalog.getStreamsByIds(listOf(9, 2))).thenReturn(emptyList())
        assertEquals(listOf(9, 2), useCase.getStreams("FAVORITES").map { it.streamId })
        verify(catalog).getStreamsByIds(listOf(9, 2))
        verifyNoMoreInteractions(catalog)
    }
}
