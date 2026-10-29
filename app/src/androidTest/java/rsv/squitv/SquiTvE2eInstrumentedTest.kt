package rsv.squitv

import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rsv.squitv.core.data.repository.UserRepository
import rsv.squitv.data.local.AppDatabase
import rsv.squitv.data.local.entities.EpisodeEntity
import rsv.squitv.data.local.entities.FavoriteEntity
import rsv.squitv.data.local.entities.IptvStreamEntity
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.domain.usecase.WatchProgressUtils
import java.io.File
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
class SquiTvE2eInstrumentedTest {

    private lateinit var testDir: File
    private lateinit var testContext: Context
    private lateinit var db: AppDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var userRepository: UserRepository

    @Before
    fun createDb() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(targetContext.cacheDir, "test_ds_${System.currentTimeMillis()}_${(1..1000).random()}")
        testDir.mkdirs()

        testContext = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = testDir
            override fun getCacheDir(): File = testDir
        }

        db = Room.inMemoryDatabaseBuilder(testContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        settingsRepository = SettingsRepository(testContext)
        userRepository = UserRepository(db.iptvDao(), settingsRepository)

        val dummyCredentials = XtreamCredentials(
            username = "test_user",
            password = "test_password",
            baseUrl = "http://test.server:8080"
        )
        settingsRepository.saveCredentials(dummyCredentials)
        settingsRepository.updateActiveProfileId("default")
    }

    @After
    fun closeDb() {
        db.close()
        testDir.deleteRecursively()
    }

    private fun isFavoriteInList(
        favList: List<FavoriteEntity>,
        targetId: Int,
        type: ContentType
    ): Boolean {
        return when (type) {
            ContentType.LIVE -> favList.any { it.streamId == targetId && it.streamType.equals("LIVE", ignoreCase = true) }
            ContentType.MOVIE -> favList.any { it.streamId == targetId && (it.streamType.equals("MOVIE", ignoreCase = true) || it.streamType.equals("VOD", ignoreCase = true)) }
            ContentType.SERIES -> favList.any { it.streamId == targetId && it.streamType.equals("SERIES", ignoreCase = true) }
            else -> false
        }
    }

    @OptIn(UnstableApi::class)
    @Test
    fun instrumented_media3_playbackPositionRestoration_verifiesSeekAndCurrentPosition() = runBlocking {
        val context = testContext
        val streamId = 429677
        val savedPosition = 120_000L // 2 minutes (120,000 ms)
        val totalDuration = 3_600_000L // 1 hour

        // 1. Save Progress to Room DB
        userRepository.saveWatchProgress(streamId, "VOD", savedPosition, totalDuration)

        // 2. Read Progress from Room DB
        val roomWp = userRepository.getWatchProgress(streamId)
        assertNotNull(roomWp)
        val roomPosition = roomWp!!.position
        assertEquals(savedPosition, roomPosition)

        Timber.tag("SquiTV_E2E").i("[RESUME_TEST] media=movie_429677")
        Timber.tag("SquiTV_E2E").i("[ROOM_SAVED] position=$savedPosition")
        Timber.tag("SquiTV_E2E").i("[ROOM_READ] position=$roomPosition")

        // 3. Test Seek & CurrentPosition on Real Media3 ExoPlayer Instance
        var playerReadyPosition = -1L
        var playerPlayingPosition = -1L

        withContext(Dispatchers.Main) {
            val exoPlayer = ExoPlayer.Builder(context).build()
            val mediaItem = MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4")
            exoPlayer.setMediaItem(mediaItem)

            Timber.tag("SquiTV_E2E").i("[SEEK_REQUESTED] position=$roomPosition")
            exoPlayer.seekTo(roomPosition)

            exoPlayer.prepare()

            var count = 0
            while (exoPlayer.playbackState != Player.STATE_READY && count < 50) {
                delay(100)
                count++
            }

            playerReadyPosition = exoPlayer.currentPosition
            Timber.tag("SquiTV_E2E").i("[PLAYER_READY] position=$playerReadyPosition")

            exoPlayer.play()
            delay(500)
            playerPlayingPosition = exoPlayer.currentPosition
            Timber.tag("SquiTV_E2E").i("[PLAYER_PLAYING] position=$playerPlayingPosition")

            exoPlayer.release()
        }

        val diff = kotlin.math.abs(playerReadyPosition - savedPosition)
        assertTrue("Player position ($playerReadyPosition) must be close to saved position ($savedPosition)", diff < 5000L)
        assertTrue("Playing position ($playerPlayingPosition) must advance past ready position ($playerReadyPosition)", playerPlayingPosition >= playerReadyPosition)
        Timber.tag("SquiTV_E2E").i("[ASSERTION] PASS")
    }

    @Test
    fun instrumented_initialFavoriteState_returnsTrueForPreFavoritedItems() = runBlocking {
        val liveChannel = IptvItem(id = "101", name = "CHAVES HD", icon = "http://logo.png", type = ContentType.LIVE)
        val movie = IptvItem(id = "201", name = "INCEPTION", icon = "http://poster.png", type = ContentType.MOVIE)
        val parentSeries = IptvItem(id = "500", name = "The Last of Us", icon = "http://logo.png", type = ContentType.SERIES)

        userRepository.updateFavorite(liveChannel, isFav = true)
        userRepository.updateFavorite(movie, isFav = true)
        userRepository.updateFavorite(parentSeries, isFav = true)

        val favList = userRepository.getFavoritesListFlow().first()

        assertTrue("Live Channel 101 must be recognized as favorited", isFavoriteInList(favList, 101, ContentType.LIVE))
        assertTrue("Movie 201 must be recognized as favorited", isFavoriteInList(favList, 201, ContentType.MOVIE))
        assertTrue("Parent Series 500 must be recognized as favorited", isFavoriteInList(favList, 500, ContentType.SERIES))
    }

    @Test
    fun instrumented_liveTvFavorite_insertsAndRemovesInRoom() = runBlocking {
        val channelItem = IptvItem(
            id = "101",
            name = "CHAVES HD",
            icon = "http://logo.png",
            type = ContentType.LIVE,
            epgId = "chaves_epg",
            categoryId = "1"
        )

        // 1. Insert Favorite
        userRepository.updateFavorite(channelItem, isFav = true)
        var favorites = userRepository.getFavoritesListFlow().first()

        assertEquals(1, favorites.size)
        assertEquals(101, favorites[0].streamId)
        assertEquals("LIVE", favorites[0].streamType)
        assertEquals("CHAVES HD", favorites[0].name)

        // 2. Remove Favorite
        userRepository.updateFavorite(channelItem, isFav = false)
        favorites = userRepository.getFavoritesListFlow().first()

        assertTrue("Favorite list must be empty after removal", favorites.isEmpty())
    }

    @Test
    fun instrumented_movieFavorite_insertsAndRemovesInRoom() = runBlocking {
        val movieItem = IptvItem(
            id = "201",
            name = "INCEPTION 4K",
            icon = "http://poster.png",
            type = ContentType.MOVIE,
            containerExtension = "mp4",
            categoryId = "10"
        )

        userRepository.updateFavorite(movieItem, isFav = true)
        val favorites = userRepository.getFavoritesListFlow().first()

        assertEquals(1, favorites.size)
        assertEquals(201, favorites[0].streamId)
        assertEquals("MOVIE", favorites[0].streamType)
        assertEquals("INCEPTION 4K", favorites[0].name)
    }

    @Test
    fun instrumented_seriesParentFavorite_resolvesSeriesFromEpisodeStreamId() = runBlocking {
        // Insert Parent Series into iptv_streams
        val seriesEntity = IptvStreamEntity(
            id = 500,
            name = "The Last of Us",
            categoryId = "20",
            streamType = "SERIES",
            url = null,
            logo = "http://logo.png"
        )
        db.iptvDao().insertStreams(listOf(seriesEntity))

        // Insert Episode into episodes table
        val episodeEntity = EpisodeEntity(
            seriesId = 500,
            seasonNumber = 2,
            episodeId = 205,
            episodeNum = 5,
            title = "Episode 5",
            streamId = 90005,
            containerExtension = "mkv"
        )
        db.iptvDao().insertEpisodes(listOf(episodeEntity))

        // Query parent seriesId via episode streamId 90005
        val epFromDb = userRepository.getEpisodeByStreamId(90005)
        assertNotNull(epFromDb)
        assertEquals(500, epFromDb!!.seriesId)

        // Insert parent series item into favorites
        val seriesItem = IptvItem(
            id = epFromDb.seriesId.toString(),
            name = "The Last of Us",
            icon = "http://logo.png",
            type = ContentType.SERIES
        )
        userRepository.updateFavorite(seriesItem, isFav = true)

        val favorites = userRepository.getFavoritesListFlow().first()
        assertEquals(1, favorites.size)
        assertEquals(500, favorites[0].streamId) // MUST be Parent Series ID 500!
        assertEquals("SERIES", favorites[0].streamType)
        assertEquals("The Last of Us", favorites[0].name)
    }

    @Test
    fun instrumented_watchProgress_movieAndEpisodePositionsKeyedSeparately() = runBlocking {
        // Save Movie Progress
        userRepository.saveWatchProgress(
            streamId = 301,
            type = "VOD",
            position = 2_245_000L, // 37m 25s
            duration = 7_200_000L  // 2h
        )

        // Save Episode 2 Progress
        userRepository.saveWatchProgress(
            streamId = 10002,
            type = "SERIES",
            position = 1_110_000L, // 18m 30s
            duration = 3_000_000L, // 50m
            seriesId = 500
        )

        // Save Episode 3 Progress
        userRepository.saveWatchProgress(
            streamId = 10003,
            type = "SERIES",
            position = 1_635_000L, // 27m 15s
            duration = 3_000_000L, // 50m
            seriesId = 500
        )

        // Retrieve Movie
        val movieWp = userRepository.getWatchProgress(301)
        assertNotNull(movieWp)
        assertEquals(2_245_000L, movieWp!!.position)

        // Retrieve Ep 2
        val ep2Wp = userRepository.getWatchProgress(10002)
        assertNotNull(ep2Wp)
        assertEquals(1_110_000L, ep2Wp!!.position)

        // Retrieve Ep 3
        val ep3Wp = userRepository.getWatchProgress(10003)
        assertNotNull(ep3Wp)
        assertEquals(1_635_000L, ep3Wp!!.position)

        // Confirm positions do not overwrite each other
        assertNotEquals(ep2Wp.position, ep3Wp.position)
    }

    @Test
    fun instrumented_continueWatching_filtersOutCompletedAndResolvesParentSeries() = runBlocking {
        userRepository.saveWatchProgress(streamId = 10002, type = "SERIES", position = 1_110_000L, duration = 3_000_000L, seriesId = 500)
        userRepository.saveWatchProgress(streamId = 10003, type = "SERIES", position = 2_999_500L, duration = 3_000_000L, seriesId = 500) // Near end (completed)

        val allWp = userRepository.getAllWatchProgressFlow().first()
        val pending = allWp.filter { WatchProgressUtils.isPendingWatchProgress(it.position, it.duration) }

        assertEquals(1, pending.size)
        assertEquals(10002, pending[0].streamId)
        assertEquals(500, pending[0].seriesId)
    }

    @Test
    fun instrumented_episodeProgressBar_calculatesRoomProgress() = runBlocking {
        // Save episode progress: 18m 30s (1,110,000 ms) out of 50m (3,000,000 ms)
        userRepository.saveWatchProgress(
            streamId = 90005,
            type = "SERIES",
            position = 1_110_000L,
            duration = 3_000_000L,
            seriesId = 500
        )

        val wpList = userRepository.getAllWatchProgressFlow().first()
        val progressMap = wpList.associate { 
            it.streamId to (it.position.toFloat() / it.duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        }

        val epProgress = progressMap[90005]
        assertNotNull(epProgress)
        assertEquals(0.37f, epProgress!!, 0.01f) // 37% watched
        assertTrue("Episode progress must be > 0.0f to render progress bar", epProgress > 0.0f)
    }

    @Test
    fun instrumented_movieAndSeries_availableGenresExtractedFromDatabase() = runBlocking {
        val movieStream = IptvStreamEntity(
            id = 201,
            name = "Inception 4K",
            categoryId = "10",
            streamType = "VOD",
            url = "http://movie.mp4",
            logo = "http://poster.png",
            genre = "Ficção Científica, Ação"
        )
        val seriesStream = IptvStreamEntity(
            id = 500,
            name = "Breaking Bad",
            categoryId = "20",
            streamType = "SERIES",
            url = null,
            logo = "http://logo.png",
            genre = "Drama, Crime"
        )
        db.iptvDao().insertStreams(listOf(movieStream, seriesStream))

        val vodGenres = db.iptvDao().getAvailableGenresByType("VOD")
        val seriesGenres = db.iptvDao().getAvailableGenresByType("SERIES")

        assertTrue("VOD genres must contain 'Ficção Científica, Ação'", vodGenres.contains("Ficção Científica, Ação"))
        assertTrue("SERIES genres must contain 'Drama, Crime'", seriesGenres.contains("Drama, Crime"))
    }

    @Test
    fun instrumented_genreNormalization_extractsActionAndSciFiFromMetadata() = runBlocking {
        val movie1 = IptvStreamEntity(id = 301, name = "Ação Total", categoryId = "10", streamType = "VOD", url = "http://movie1.mp4", logo = "http://p1.png", genre = "Action, Adventure / Sci-Fi")
        val movie2 = IptvStreamEntity(id = 302, name = "Sem Ação", categoryId = "10", streamType = "VOD", url = "http://movie2.mp4", logo = "http://p2.png", genre = "Drama / Romance")
        db.iptvDao().insertStreams(listOf(movie1, movie2))

        val vodRawGenres = db.iptvDao().getAvailableGenresByType("VOD")
        val normalizedGenres = vodRawGenres.flatMap { rsv.squitv.domain.usecase.GenreUtils.extractNormalizedGenres(it) }.distinct().sorted()

        assertTrue(normalizedGenres.contains("Ação"))
        assertTrue(normalizedGenres.contains("Aventura"))
        assertTrue(normalizedGenres.contains("Ficção Científica"))
        assertTrue(normalizedGenres.contains("Drama"))
        assertTrue(normalizedGenres.contains("Romance"))

        // Confirm Movie 301 matches "Ação", Movie 302 ("Sem Ação", genre "Drama / Romance") does NOT match "Ação"
        assertTrue(rsv.squitv.domain.usecase.GenreUtils.matchesGenre(movie1.genre, "Ação"))
        assertFalse("Movie with 'Ação' in name but genre 'Drama' must NOT match 'Ação' genre", rsv.squitv.domain.usecase.GenreUtils.matchesGenre(movie2.genre, "Ação"))
    }

    @Test
    fun instrumented_apkUpdateSessionPreservation_restoresCredentialsFromEncryptedStorage() = runBlocking {
        val testCreds = XtreamCredentials("user_update_test", "pass_secret_123", "http://server.iptv.com:8080")
        settingsRepository.saveCredentials(testCreds)

        // Re-read settings flow (simulating app process restart after APK update)
        val settings = settingsRepository.settingsFlow.first()

        val creds = settings.credentials
        assertNotNull("Credentials must be present after APK update / process restart", creds)
        assertEquals("user_update_test", creds?.username)
        assertEquals("pass_secret_123", creds?.password)
        assertEquals("http://server.iptv.com:8080", creds?.baseUrl)
    }
}
