package rsv.squitv.core.navigation

import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import rsv.squitv.BuildConfig
import rsv.squitv.R
import rsv.squitv.domain.model.ContentType
import rsv.squitv.ui.AppGalleryScreen
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import rsv.squitv.ui.account.AccountScreen
import rsv.squitv.ui.content.*
import rsv.squitv.ui.dashboard.DashboardScreen
import rsv.squitv.ui.epg.EpgGridScreen
import rsv.squitv.ui.login.InitialScreen
import rsv.squitv.ui.login.LocalLoginScreen
import rsv.squitv.ui.login.LoginScreen
import rsv.squitv.ui.login.LoginViewModel
import rsv.squitv.ui.login.SyncScreen
import rsv.squitv.ui.viewmodel.LocalLoginViewModel
import rsv.squitv.ui.search.SearchScreen
import rsv.squitv.ui.series.SeriesDetailScreen
import rsv.squitv.ui.debug.DebugConsoleScreen
import rsv.squitv.ui.settings.DnsTesterScreen
import rsv.squitv.ui.settings.SettingsScreen
import rsv.squitv.ui.settings.UpdatesScreen
import rsv.squitv.ui.viewmodel.*
import rsv.squitv.ui.viewmodel.SearchViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.ui.viewmodel.settings.SettingsViewModel

@UnstableApi
fun NavGraphBuilder.appNavGraph(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel,
    appController: AppController
) {
    composable<Route.Initial> {
        InitialScreen(mainViewModel, appController)
    }

    composable<Route.Login> {
        val loginViewModel: LoginViewModel = hiltViewModel()
        LoginScreen(
            viewModel = loginViewModel,
            onOpenLocalLogin = { appController.navigate(Route.LocalLogin) },
            onOpenGallery = if (BuildConfig.DEBUG) { { appController.navigate(Route.Gallery) } } else null,
            onLoginSuccess = { /* Managed by LaunchedEffect in MainNavigation */ }
        )
    }

    composable<Route.LocalLogin> {
        val localLoginViewModel: LocalLoginViewModel = hiltViewModel()
        LocalLoginScreen(
            viewModel = localLoginViewModel,
            onBack = { appController.goBack() }
        )
    }

    composable<Route.Gallery> {
        AppGalleryScreen(
            onBack = { appController.goBack("gallery") }
        )
    }

    composable<Route.Sync> {
        SyncScreen(
            viewModel = mainViewModel,
            onSyncComplete = { 
                appController.navigate(Route.Dashboard, popUpToRoute = Route.Sync, inclusive = true)
            }
        )
    }

    composable<Route.Dashboard> {
        DashboardScreen(
            viewModel = mainViewModel,
            onNavigateToCategory = { type, catId ->
                when (type) {
                    "live" -> {
                        Log.d("NAV_STACK_DEBUG", "NAVIGATING | from=Dashboard | to=LiveChannels($catId)")
                        appController.navigate(Route.LiveChannels(catId))
                    }
                    "movie" -> {
                        Log.d("NAV_STACK_DEBUG", "NAVIGATING | from=Dashboard | to=Movies($catId)")
                        appController.navigate(Route.Movies(catId, "movie"))
                    }
                    "genre" -> appController.navigate(Route.Movies(catId, "genre"))
                    "series" -> appController.navigate(Route.Series(catId))
                    "favorites" -> appController.navigate(Route.Favorites)
                    "history" -> appController.navigate(Route.History)
                }
            },
            onNavigateToMultiView = { appController.navigate(Route.MultiView) },
            onNavigateToEpgGrid = { appController.navigate(Route.EpgGrid) },
            onNavigateToSearch = { appController.navigate(Route.Search()) },
            onNavigateToAccount = { appController.navigate(Route.Account) },
            onNavigateToSettings = { appController.navigate(Route.Settings) },
            onNavigateToDownloads = { appController.navigate(Route.Downloads) }
        )
    }

    composable<Route.LiveChannels> { backStackEntry ->
        val route: Route.LiveChannels = backStackEntry.toRoute()
        val catViewModel: CategoryViewModel = hiltViewModel()
        val blockedIds by mainViewModel.blockedCategoryIds.collectAsStateWithLifecycle()
        val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            mainViewModel.loadCategories("live")
        }

        LaunchedEffect(route.categoryId, blockedIds, settings.hideBlockedCategories) {
            val ids = if (settings.hideBlockedCategories) blockedIds else emptySet()
            val catToLoad = route.categoryId ?: settings.lastLiveCategory ?: "RECENTS"
            catViewModel.loadContent("live", catToLoad, ids)
        }
        LiveChannelsScreen(
            viewModel = mainViewModel,
            categoryViewModel = catViewModel,
            settingsViewModel = settingsViewModel,
            onBack = { appController.goBack() },
            onNavigateToSearch = { appController.navigate(Route.Search()) },
            onPlay = { streamId, name, type, epgId, qualities, icon ->
                val qualitiesJson = Json.encodeToString(qualities)
                appController.navigate(Route.Player(streamId, name, type, epgId = epgId, qualitiesJson = qualitiesJson, categoryId = route.categoryId ?: settings.lastLiveCategory ?: "RECENTS", streamIcon = icon))
            },
            onCategoryClick = { type, catId ->
                if (catId != null) mainViewModel.saveLastCategory(type, catId)
                appController.navigate(Route.LiveChannels(catId), popUpToRoute = Route.Dashboard, inclusive = false)
            }
        )
    }

    composable<Route.Movies> { backStackEntry ->
        val route: Route.Movies = backStackEntry.toRoute()
        val catViewModel: CategoryViewModel = hiltViewModel()
        val blockedIds by mainViewModel.blockedCategoryIds.collectAsStateWithLifecycle()
        val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            mainViewModel.loadCategories(route.type)
        }

        LaunchedEffect(route.categoryId, route.type, blockedIds, settings.hideBlockedCategories) {
            val ids = if (settings.hideBlockedCategories) blockedIds else emptySet()
            val catToLoad = route.categoryId ?: (if (route.type == "movie") settings.lastMovieCategory else null) ?: "RECENTS"
            catViewModel.loadContent(route.type, catToLoad, ids)
        }
        MoviesScreen(
            title = if (route.type == "movie") stringResource(R.string.movies_nav_title) else route.categoryId ?: "",
            viewModel = mainViewModel,
            categoryViewModel = catViewModel,
            libraryViewModel = libraryViewModel,
            settingsViewModel = settingsViewModel,
            onBack = { appController.goBack() },
            onNavigateToSearch = { appController.navigate(Route.Search()) },
            onVodClick = { streamId, name, icon ->
                appController.navigate(Route.VodDetail(streamId, name, icon))
            },
            onCategoryClick = { type, catId ->
                if (catId != null) mainViewModel.saveLastCategory(type, catId)
                appController.navigate(Route.Movies(catId, type), popUpToRoute = Route.Dashboard, inclusive = false)
            }
        )
    }

    composable<Route.Series> { backStackEntry ->
        val route: Route.Series = backStackEntry.toRoute()
        val catViewModel: CategoryViewModel = hiltViewModel()
        val blockedIds by mainViewModel.blockedCategoryIds.collectAsStateWithLifecycle()
        val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            mainViewModel.loadCategories(route.type)
        }

        LaunchedEffect(route.categoryId, route.type, blockedIds, settings.hideBlockedCategories) {
            val ids = if (settings.hideBlockedCategories) blockedIds else emptySet()
            val catToLoad = route.categoryId ?: (if (route.type == "series") settings.lastSeriesCategory else null) ?: "RECENTS"
            catViewModel.loadContent(route.type, catToLoad, ids)
        }
        SeriesScreen(
            title = if (route.type == "series") stringResource(R.string.series_nav_title) else route.categoryId ?: "",
            viewModel = mainViewModel,
            categoryViewModel = catViewModel,
            libraryViewModel = libraryViewModel,
            settingsViewModel = settingsViewModel,
            onBack = { appController.goBack() },
            onNavigateToSearch = { appController.navigate(Route.Search()) },
            onSeriesClick = { streamId, name, icon ->
                appController.navigate(Route.SeriesDetail(streamId, name, icon))
            },
            onCategoryClick = { type, catId ->
                if (catId != null) mainViewModel.saveLastCategory(type, catId)
                appController.navigate(Route.Series(catId, type), popUpToRoute = Route.Dashboard, inclusive = false)
            }
        )
    }

    composable<Route.SeriesDetail> { backStackEntry ->
        val route: Route.SeriesDetail = backStackEntry.toRoute()
        val seriesViewModel: SeriesDetailViewModel = hiltViewModel()
        val watchProgress by libraryViewModel.watchProgress.collectAsStateWithLifecycle()
        LaunchedEffect(route.seriesId) {
            seriesViewModel.loadSeriesInfo(route.seriesId, watchProgress)
        }
        SeriesDetailScreen(
            viewModel = seriesViewModel,
            mainViewModel = mainViewModel,
            seriesId = route.seriesId,
            seriesName = route.seriesName,
            cover = route.cover,
            onBack = { appController.goBack() },
            onPlayEpisode = { streamId, name, container ->
                appController.navigate(
                    Route.Player(
                        streamId = streamId,
                        streamName = name,
                        streamType = "series",
                        container = container,
                        seriesId = route.seriesId,
                        seasonNumber = seriesViewModel.selectedSeason.value
                    )
                )
            },
            onActorClick = { actor -> appController.navigate(Route.ActorDetail(actor)) },
            onGenreClick = { genre -> appController.navigate(Route.Series(genre, "genre")) },
            onDirectorClick = { director -> appController.navigate(Route.ActorDetail(director)) }
        )
    }

    composable<Route.VodDetail> { backStackEntry ->
        val route: Route.VodDetail = backStackEntry.toRoute()
        VodDetailScreen(
            vodId = route.vodId,
            vodName = route.vodName,
            icon = route.icon,
            viewModel = mainViewModel,
            onBack = { appController.goBack() },
            onPlay = { id, name, container ->
                appController.navigate(Route.Player(id, name, "movie", container = container))
            },
            onActorClick = { actor -> appController.navigate(Route.ActorDetail(actor)) },
            onGenreClick = { genre -> appController.navigate(Route.Movies(genre, "genre")) },
            onDirectorClick = { director -> appController.navigate(Route.ActorDetail(director)) }
        )
    }

    composable<Route.Player> { backStackEntry ->
        val route: Route.Player = backStackEntry.toRoute()
        val playerViewModel: PlayerViewModel = hiltViewModel()
        LaunchedEffect(route) {
            val qualities: Map<String, Int> = try {
                Json.decodeFromString(route.qualitiesJson ?: "{}")
            } catch (e: Exception) {
                emptyMap()
            }
            playerViewModel.playStream(
                route.streamId,
                ContentType.fromString(route.streamType),
                route.container,
                route.epgId,
                route.seriesId,
                route.seasonNumber,
                qualities,
                route.categoryId,
                displayName = route.streamName,
                streamIcon = route.streamIcon
            )
        }
        PlayerScreen(
            viewModel = playerViewModel,
            streamName = route.streamName,
            categoryId = route.categoryId,
            onBack = { appController.goBack() },
            onNavigateToPlayer = { streamId, name, type, container ->
                appController.navigate(
                    route.copy(
                        streamId = streamId,
                        streamName = name,
                        streamType = type,
                        container = container
                    ),
                    popUpToRoute = route,
                    inclusive = true
                )
            }
        )
    }

    composable<Route.Account> {
        AccountScreen(viewModel = mainViewModel, onBack = { appController.goBack() })
    }

    composable<Route.Settings> {
        SettingsScreen(
            mainViewModel = mainViewModel,
            settingsViewModel = settingsViewModel,
            libraryViewModel = libraryViewModel,
            onBack = { appController.goBack() },
            onNavigateToAccount = { appController.navigate(Route.Account) },
            onNavigateToDnsTester = { appController.navigate(Route.DnsTester) },
            onNavigateToUpdates = { appController.navigate(Route.Updates) }
        )
    }

    composable<Route.Updates> {
        UpdatesScreen(
            onBack = { appController.goBack() }
        )
    }

    composable<Route.DnsTester> {
        DnsTesterScreen(
            onBack = { appController.goBack() }
        )
    }

    composable<Route.DebugConsole> {
        DebugConsoleScreen(
            onNavigateBack = { appController.goBack() }
        )
    }

    composable<Route.MultiView> {
        MultiViewScreen(mainViewModel = mainViewModel, onBack = { appController.goBack() })
    }

    composable<Route.EpgGrid> {
        EpgGridScreen(
            mainViewModel = mainViewModel,
            onBack = { appController.goBack() },
            onChannelClick = { id, name, epgId ->
                appController.navigate(Route.Player(id, name, "live", epgId = epgId, categoryId = null))
            }
        )
    }

    composable<Route.Downloads> {
        DownloadsScreen(
            viewModel = mainViewModel,
            onBack = { appController.goBack() },
            onPlay = { streamId, name, type, container, seriesId, seasonNumber ->
                appController.navigate(
                    Route.Player(
                        streamId = streamId,
                        streamName = name,
                        streamType = type,
                        container = container,
                        seriesId = seriesId,
                        seasonNumber = seasonNumber
                    )
                )
            }
        )
    }

    composable<Route.Favorites> {
        val catViewModel: CategoryViewModel = hiltViewModel()
        val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()

        LaunchedEffect(favorites) {
            catViewModel.loadContent("favorites", favorites = favorites)
        }
        LibraryContentScreen(
            title = stringResource(R.string.favorites_label),
            viewModel = mainViewModel,
            categoryViewModel = catViewModel,
            libraryViewModel = libraryViewModel,
            onBack = { appController.goBack() },
            onNavigateToSearch = { appController.navigate(Route.Search()) },
            onPlay = { streamId, name, type, epgId, qualities, icon ->
                val qualitiesJson = Json.encodeToString(qualities)
                if (type == "movie") appController.navigate(Route.VodDetail(streamId, name, icon))
                else if (type == "series") appController.navigate(Route.SeriesDetail(streamId, name, icon))
                else appController.navigate(Route.Player(streamId, name, type, epgId = epgId, qualitiesJson = qualitiesJson, streamIcon = icon))
            },
            onSeriesClick = { id, name, icon -> appController.navigate(Route.SeriesDetail(id, name, icon)) },
            onVodClick = { id, name, icon -> appController.navigate(Route.VodDetail(id, name, icon)) }
        )
    }

    composable<Route.History> {
        val catViewModel: CategoryViewModel = hiltViewModel()

        LaunchedEffect(Unit) {
            catViewModel.loadContent("history")
        }
        LibraryContentScreen(
            title = stringResource(R.string.history_label),
            viewModel = mainViewModel,
            categoryViewModel = catViewModel,
            libraryViewModel = libraryViewModel,
            onBack = { appController.goBack() },
            onNavigateToSearch = { appController.navigate(Route.Search()) },
            onPlay = { streamId, name, type, epgId, qualities, icon ->
                val qualitiesJson = Json.encodeToString(qualities)
                if (type == "movie") appController.navigate(Route.VodDetail(streamId, name, icon))
                else if (type == "series") appController.navigate(Route.SeriesDetail(streamId, name, icon))
                else appController.navigate(Route.Player(streamId, name, type, epgId = epgId, qualitiesJson = qualitiesJson, streamIcon = icon))
            },
            onSeriesClick = { id, name, icon -> appController.navigate(Route.SeriesDetail(id, name, icon)) },
            onVodClick = { id, name, icon -> appController.navigate(Route.VodDetail(id, name, icon)) }
        )
    }

    composable<Route.ActorDetail> { backStackEntry ->
        val route: Route.ActorDetail = backStackEntry.toRoute()
        val catViewModel: CategoryViewModel = hiltViewModel()
        LaunchedEffect(route.name) {
            catViewModel.loadContent("actor", route.name)
        }
        ActorDetailScreen(
            actorName = route.name,
            categoryViewModel = catViewModel,
            onBack = { appController.goBack() },
            onSeriesClick = { id, name, icon -> appController.navigate(Route.SeriesDetail(id, name, icon)) },
            onVodClick = { id, name, icon -> appController.navigate(Route.VodDetail(id, name, icon)) }
        )
    }

    composable<Route.Search> {
        val blockedIds by mainViewModel.blockedCategoryIds.collectAsStateWithLifecycle()
        val searchViewModel: SearchViewModel = hiltViewModel()

        LaunchedEffect(blockedIds) {
            searchViewModel.setBlockedCategories(blockedIds)
        }

        SearchScreen(
            viewModel = searchViewModel,
            onNavigateBack = { appController.goBack() },
            onItemClick = { id, name, type, container, seriesId, seasonNumber, icon ->
                mainViewModel.addSearchHistory(name)
                if (type == "movie") {
                    appController.navigate(Route.VodDetail(id, name, icon))
                } else if (type == "series") {
                    appController.navigate(Route.SeriesDetail(seriesId ?: id, name, icon))
                } else {
                    appController.navigate(
                        Route.Player(
                            streamId = id,
                            streamName = name,
                            streamType = type,
                            container = container,
                            seriesId = seriesId,
                            seasonNumber = seasonNumber,
                            streamIcon = icon
                        )
                    )
                }
            }
        )
    }
}
