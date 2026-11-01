package rsv.squitv

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.core.data.repository.*
import rsv.squitv.data.local.entities.*
import rsv.squitv.data.model.*
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.model.*
import rsv.squitv.ui.viewmodel.CategoryViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CategoryBrowsingTest {
    private val store = ViewModelStore()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var catalog: CatalogRepository
    private lateinit var settings: SettingsRepository
    private lateinit var users: UserRepository
    private val credentials = XtreamCredentials("user", "password", "http://example.test")
    private lateinit var options: MutableStateFlow<SettingsRepository.AppSettings>
    private val favorites = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    private val progress = MutableStateFlow<List<WatchProgressEntity>>(emptyList())
    private val sources = PlaybackSourceRepository()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        catalog = mock(CatalogRepository::class.java)
        settings = mock(SettingsRepository::class.java)
        users = mock(UserRepository::class.java)
        val accountKey = SettingsBackupCodec.accountKey(credentials)
        options = MutableStateFlow(
            SettingsRepository.AppSettings(
                credentials,
                lastSyncTimestamp = 0L,
                syncIntervalHours = 24,
                movieCategoryModeMap = mapOf("${accountKey}_default" to "GENRE"),
                seriesCategoryModeMap = mapOf("${accountKey}_default" to "GENRE")
            )
        )
        `when`(settings.settingsFlow).thenReturn(options)
        `when`(users.getFavoritesListFlow()).thenReturn(favorites)
        `when`(users.getAllWatchProgressFlow()).thenReturn(progress)
        `when`(catalog.applyCategoryCustomization(anyList(), anyList(), anySet())).thenCallRealMethod()
        `when`(catalog.sortAndFilterRecentStreams(anyList(), anyInt(), anyLong())).thenReturn(emptyList())
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun entity(id: Int, name: String = "Título $id", type: String = "VOD", genre: String? = null) =
        IptvStreamEntity(id, name, "server", type, null, null, genre = genre)

    private fun vm() = CategoryViewModel(ApplicationProvider.getApplicationContext<Application>(), catalog, users,
        settings, sources, SavedStateHandle()).also { store.put("vm", it) }

    private suspend fun awaitCondition(condition: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5000) { while (!condition()) delay(10) }
    }

    @Test fun genreNavigationUpdatesFromActualMetadataAndRecoversWhenSelectedGenreBecomesEmpty() = runTest {
        val provider = listOf(IptvCategoryEntity("server", "Ação do servidor", "VOD"),
            IptvCategoryEntity("empty", "Vazia", "VOD"))
        val data = MutableStateFlow(provider to listOf(entity(1, genre = "Drama"), entity(2)))
        `when`(catalog.observeBrowseCatalog("movie")).thenReturn(data)
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.currentContentRows.collect() }
        vm.ensureContent("movie", "server", 1L)
        awaitCondition { vm.navigationCategories.value.any { it.id == "GENRE:Drama" } }
        assertFalse(vm.navigationCategories.value.any { it.id == "GENRE:Ação" || it.id == "empty" })
        assertEquals("FALLBACK:server", vm.activeCategoryId.value)
        vm.loadContent("movie", "GENRE:Drama")
        awaitCondition { vm.currentContentRows.value.singleOrNull()?.items?.map { it.id } == listOf("1") }
        data.value = provider to listOf(entity(1, genre = "Drama"), entity(2, genre = "Action"))
        awaitCondition { vm.navigationCategories.value.any { it.id == "GENRE:Ação" && it.count == 1 } }
        vm.loadContent("movie", "GENRE:Ação")
        awaitCondition { vm.currentContentRows.value.singleOrNull()?.items?.map { it.id } == listOf("2") }
        data.value = provider to listOf(entity(1, genre = "Drama"))
        awaitCondition { vm.activeCategoryId.value == "ALL" }
        assertFalse(vm.navigationCategories.value.any { it.id == "GENRE:Ação" })
        verify(catalog, never()).getVodStreams(credentials, "server", false)
    }

    @Test fun groupingCanBeDisabledAndEnabledAgainWithoutLosingVersionsOrPlaybackSource() = runTest {
        val data = MutableStateFlow(listOf(IptvCategoryEntity("server", "TV", "LIVE")) to listOf(
            entity(1, "Canal HD", "LIVE"), entity(2, "Canal FHD", "LIVE"), entity(3, "Outro HD", "LIVE")))
        `when`(catalog.observeBrowseCatalog("live")).thenReturn(data)
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.currentContentRows.collect() }
        vm.ensureContent("live", "server", 1L)
        awaitCondition { vm.currentContentRows.value.singleOrNull()?.items?.size == 2 }
        repeat(3) {
            options.value = options.value.copy(groupLiveChannels = false)
            awaitCondition { vm.currentContentRows.value.singleOrNull()?.items?.size == 3 &&
                vm.navigationCategories.value.firstOrNull()?.count == 3 }
            options.value = options.value.copy(groupLiveChannels = true)
            awaitCondition { vm.currentContentRows.value.singleOrNull()?.items?.size == 2 &&
                vm.navigationCategories.value.firstOrNull()?.count == 2 }
        }
        val captured = sources.get(vm.capturePlaybackSource(), PlaybackSourceRepository.scope(options.value))!!
        assertEquals(setOf("1", "2", "3"), captured.map { it.id }.toSet())
        assertTrue(vm.currentContentRows.value.single().items.first().containsStream("1"))
        assertTrue(vm.currentContentRows.value.single().items.first().containsStream("2"))
    }

    @Test fun hiddenProviderOrganizationStillFiltersContentWithoutDeletingIt() = runTest {
        val data = MutableStateFlow(listOf(IptvCategoryEntity("server", "Drama", "VOD")) to listOf(entity(1, genre = "Drama")))
        `when`(catalog.observeBrowseCatalog("movie")).thenReturn(data)
        val vm = vm()
        vm.ensureContent("movie", "ALL", 1L)
        awaitCondition { vm.navigationCategories.value.isNotEmpty() }
        options.value = options.value.copy(hiddenCategoriesMap =
            mapOf((SettingsBackupCodec.accountKey(credentials) + "_vod") to setOf("server")))
        awaitCondition { vm.navigationCategories.value.isEmpty() }
        assertEquals(1, data.value.second.size)
        options.value = options.value.copy(hiddenCategoriesMap = emptyMap())
        awaitCondition { vm.navigationCategories.value.any { it.id == "GENRE:Drama" && it.count == 1 } }
    }

    @Test fun favoritesAppearAndDisappearWithTheirActualContent() = runTest {
        val data = MutableStateFlow(listOf(IptvCategoryEntity("server", "Drama", "VOD")) to listOf(entity(1, genre = "Drama")))
        `when`(catalog.observeBrowseCatalog("movie")).thenReturn(data)
        val vm = vm()
        vm.ensureContent("movie", "ALL", 1L)
        awaitCondition { vm.navigationCategories.value.isNotEmpty() }
        assertFalse(vm.navigationCategories.value.any { it.id == "FAVORITES" })
        favorites.value = listOf(FavoriteEntity(1, "MOVIE", "Título 1"))
        awaitCondition { vm.navigationCategories.value.any { it.id == "FAVORITES" && it.count == 1 } }
        favorites.value = emptyList()
        awaitCondition { vm.navigationCategories.value.none { it.id == "FAVORITES" } }
    }
}
