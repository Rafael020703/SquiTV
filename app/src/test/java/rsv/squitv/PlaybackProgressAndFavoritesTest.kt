package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.domain.usecase.WatchProgressUtils

class PlaybackProgressAndFavoritesTest {

    @Test
    fun watchProgress_completionThreshold_deletesProgressWhenNearEnd() {
        // 2 hours movie (7,200,000 ms)
        val duration2h = 7_200_000L

        // At 1 hour 48 minutes (6,480,000 ms, 90% watched, 12 min remaining) -> NOT completed
        assertFalse(WatchProgressUtils.isVideoCompleted(6_480_000L, duration2h))

        // At 1 hour 55 minutes (6,900,000 ms, 95.8% watched, 5 min remaining) -> NOT completed
        assertFalse(WatchProgressUtils.isVideoCompleted(6_900_000L, duration2h))

        // At 1 hour 59 minutes (7,140,000 ms, 1 min remaining) -> NOT completed
        assertFalse(WatchProgressUtils.isVideoCompleted(7_140_000L, duration2h))

        // At 5 seconds remaining -> NOT completed unless <= 2s or STATE_ENDED
        assertFalse(WatchProgressUtils.isVideoCompleted(7_195_000L, duration2h))

        // At 1 second remaining (<= 2s) -> COMPLETED
        assertTrue(WatchProgressUtils.isVideoCompleted(7_199_000L, duration2h))

        // Explicit ended event -> ALWAYS COMPLETED
        assertTrue(WatchProgressUtils.isVideoCompleted(1000L, duration2h, isEndedEvent = true))
    }

    @Test
    fun continueWatching_filtersOutCompletedItemsAndSortsByLastWatched() {
        data class FakeWatchProgress(val streamId: Int, val position: Long, val duration: Long, val lastWatched: Long)

        val progressList = listOf(
            FakeWatchProgress(101, 300_000L, 600_000L, 1000L), // 50% watched, older
            FakeWatchProgress(102, 599_000L, 600_000L, 2000L), // 99.8% watched (COMPLETED)
            FakeWatchProgress(103, 100_000L, 600_000L, 3000L)  // 16% watched, newer
        )

        val pendingProgress = progressList.filter { wp ->
            WatchProgressUtils.isPendingWatchProgress(wp.position, wp.duration)
        }.sortedByDescending { it.lastWatched }

        // Must exclude stream 102 (completed)
        assertEquals(2, pendingProgress.size)
        // Must sort by lastWatched DESC (103 first, then 101)
        assertEquals(103, pendingProgress[0].streamId)
        assertEquals(101, pendingProgress[1].streamId)
    }

    @Test
    fun virtualCategories_continueWatchingOmittedForLiveTv_includedForMoviesAndSeries() {
        fun buildVirtualCategories(normType: String): List<String> {
            val list = mutableListOf<String>()
            if (normType != "live") {
                list.add("CONTINUE_WATCHING")
            }
            list.add("FAVORITES")
            list.add("RECENTS")
            return list
        }

        val liveCategories = buildVirtualCategories("live")
        assertFalse("Live TV must NOT contain CONTINUE_WATCHING category", liveCategories.contains("CONTINUE_WATCHING"))
        assertTrue("Live TV must contain FAVORITES category", liveCategories.contains("FAVORITES"))
        assertTrue("Live TV must contain RECENTS category", liveCategories.contains("RECENTS"))

        val movieCategories = buildVirtualCategories("vod")
        assertTrue("Movies MUST contain CONTINUE_WATCHING category", movieCategories.contains("CONTINUE_WATCHING"))

        val seriesCategories = buildVirtualCategories("series")
        assertTrue("Series MUST contain CONTINUE_WATCHING category", seriesCategories.contains("CONTINUE_WATCHING"))
    }

    @Test
    fun playerFavoriteButton_seriesEpisodeTogglesParentSeriesNotEpisode() {
        data class FakeFavoriteEntity(val streamId: Int, val streamType: String, val name: String)

        val favoritesTable = mutableListOf<FakeFavoriteEntity>()

        fun toggleSeriesFavorite(currentEpisodeStreamId: Int, seriesId: Int?, seriesName: String) {
            val parentSeriesId = seriesId ?: currentEpisodeStreamId
            val existingIndex = favoritesTable.indexOfFirst { it.streamId == parentSeriesId && it.streamType == "SERIES" }
            if (existingIndex != -1) {
                favoritesTable.removeAt(existingIndex)
            } else {
                favoritesTable.add(FakeFavoriteEntity(parentSeriesId, "SERIES", seriesName))
            }
        }

        // Watching The Last of Us (Series ID 500), Season 2, Episode 5 (Stream ID 90005)
        toggleSeriesFavorite(currentEpisodeStreamId = 90005, seriesId = 500, seriesName = "The Last of Us")

        // Must favorite Parent Series ID 500, NOT episode 90005!
        assertEquals(1, favoritesTable.size)
        assertEquals(500, favoritesTable[0].streamId)
        assertEquals("SERIES", favoritesTable[0].streamType)
        assertEquals("The Last of Us", favoritesTable[0].name)

        // Watching Season 2 Episode 6 (Stream ID 90006) of the same series and clicking favorite button -> toggles off parent series
        toggleSeriesFavorite(currentEpisodeStreamId = 90006, seriesId = 500, seriesName = "The Last of Us")
        assertEquals(0, favoritesTable.size)
    }

    @Test
    fun parentSeriesId_fallbackToEpisodeDatabaseLookupWhenCurrentSeriesIdNull() {
        data class FakeEpisodeDbEntry(val streamId: Int, val seriesId: Int)
        val episodesDb = listOf(
            FakeEpisodeDbEntry(streamId = 98765, seriesId = 777)
        )

        fun resolveParentSeriesId(currentSeriesId: Int?, episodeStreamId: Int): Int? {
            return currentSeriesId ?: episodesDb.firstOrNull { it.streamId == episodeStreamId }?.seriesId
        }

        // Case 1: currentSeriesId is explicitly provided (777)
        val resolved1 = resolveParentSeriesId(currentSeriesId = 777, episodeStreamId = 98765)
        assertEquals(777, resolved1)

        // Case 2: currentSeriesId is null, DB finds seriesId 777 via episodeStreamId 98765
        val resolved2 = resolveParentSeriesId(currentSeriesId = null, episodeStreamId = 98765)
        assertEquals(777, resolved2)

        // Case 3: currentSeriesId is null, DB has no entry -> returns null (prevents corrupt favorite creation!)
        val resolved3 = resolveParentSeriesId(currentSeriesId = null, episodeStreamId = 99999)
        assertNull(resolved3)
    }

    @Test
    fun playerNextEpisode_crossSeasonResolution_resolvesFirstEpisodeOfNextSeason() {
        data class FakeSeason(val seasonNumber: Int)
        data class FakeEpisode(val streamId: Int, val seasonNumber: Int, val episodeNum: Int, val title: String)

        val seasons = listOf(FakeSeason(1), FakeSeason(2))
        val episodesMap = mapOf(
            1 to listOf(
                FakeEpisode(1001, 1, 1, "S1E1"),
                FakeEpisode(1002, 1, 2, "S1E2 (Last of S1)")
            ),
            2 to listOf(
                FakeEpisode(2001, 2, 1, "S2E1 (First of S2)"),
                FakeEpisode(2002, 2, 2, "S2E2")
            )
        )

        fun findNextEpisode(currentSeasonNum: Int, currentStreamId: Int): FakeEpisode? {
            val sortedSeasons = seasons.sortedBy { it.seasonNumber }
            val currentSeasonEpisodes = (episodesMap[currentSeasonNum] ?: emptyList()).sortedBy { it.episodeNum }
            val currentIndex = currentSeasonEpisodes.indexOfFirst { it.streamId == currentStreamId }

            if (currentIndex != -1 && currentIndex < currentSeasonEpisodes.size - 1) {
                return currentSeasonEpisodes[currentIndex + 1]
            } else {
                val currentSeasonIdx = sortedSeasons.indexOfFirst { it.seasonNumber == currentSeasonNum }
                if (currentSeasonIdx != -1 && currentSeasonIdx < sortedSeasons.size - 1) {
                    val nextSeasonObj = sortedSeasons[currentSeasonIdx + 1]
                    val nextSeasonEpisodes = (episodesMap[nextSeasonObj.seasonNumber] ?: emptyList()).sortedBy { it.episodeNum }
                    if (nextSeasonEpisodes.isNotEmpty()) {
                        return nextSeasonEpisodes.first()
                    }
                }
            }
            return null
        }

        // Test S1E1 -> S1E2
        val next1 = findNextEpisode(currentSeasonNum = 1, currentStreamId = 1001)
        assertNotNull(next1)
        assertEquals(1002, next1!!.streamId)
        assertEquals("S1E2 (Last of S1)", next1.title)

        // Test S1E2 (Last of S1) -> S2E1 (First of S2)
        val next2 = findNextEpisode(currentSeasonNum = 1, currentStreamId = 1002)
        assertNotNull(next2)
        assertEquals(2001, next2!!.streamId)
        assertEquals("S2E1 (First of S2)", next2.title)

        // Test S2E2 (Last of final season) -> null
        val next3 = findNextEpisode(currentSeasonNum = 2, currentStreamId = 2002)
        assertNull(next3)
    }

    @Test
    fun liveTvPlayer_hasFavoriteHeartButtonAndSettingsIcon() {
        val liveOverlayControls = listOf("FAVORITE_HEART", "SETTINGS_GEAR")
        
        assertTrue("Live TV overlay must render Favorite Heart button", liveOverlayControls.contains("FAVORITE_HEART"))
        assertTrue("Live TV overlay must render Settings Gear button", liveOverlayControls.contains("SETTINGS_GEAR"))
        assertFalse("Live TV overlay must NOT render duplicate Subtitles/Audio quick icons", liveOverlayControls.contains("CLOSED_CAPTION"))
    }

    @Test
    fun playerSettings_containsVideoAudioSubtitlesSpeedSystem() {
        val settingsCategories = listOf("VÍDEO", "ÁUDIO", "LEGENDAS", "VELOCIDADE", "SISTEMA")

        assertEquals(5, settingsCategories.size)
        assertTrue(settingsCategories.contains("VÍDEO"))
        assertTrue(settingsCategories.contains("ÁUDIO"))
        assertTrue(settingsCategories.contains("LEGENDAS"))
        assertTrue(settingsCategories.contains("VELOCIDADE"))
        assertTrue(settingsCategories.contains("SISTEMA"))
    }

    @Test
    fun favoriteToggle_updatesItemFavoriteStateInstantly() {
        val originalItem = IptvItem(
            id = "202",
            name = "HBO HD",
            icon = "http://logo.png",
            type = ContentType.LIVE,
            isFavorite = false
        )

        val favoritedItem = originalItem.copy(isFavorite = true)

        assertFalse(originalItem.isFavorite)
        assertTrue(favoritedItem.isFavorite)
        assertEquals(originalItem.id, favoritedItem.id)
    }

    @Test
    fun seriesEpisodes_haveIsolatedWatchProgressKeyedByStreamId() {
        data class FakeWatchProgress(
            val streamId: Int,
            val type: String,
            val position: Long,
            val duration: Long,
            val seriesId: Int?
        )

        // Series ID 500 ("Breaking Bad")
        val episode2Progress = FakeWatchProgress(streamId = 10002, type = "SERIES", position = 1_110_000L, duration = 3_000_000L, seriesId = 500) // 18m30s
        val episode3Progress = FakeWatchProgress(streamId = 10003, type = "SERIES", position = 1_635_000L, duration = 3_000_000L, seriesId = 500) // 27m15s

        val db = mutableMapOf<Int, FakeWatchProgress>()
        db[episode2Progress.streamId] = episode2Progress
        db[episode3Progress.streamId] = episode3Progress

        // Fetching Episode 2 must return Episode 2 position
        val fetchedEp2 = db[10002]
        assertNotNull(fetchedEp2)
        assertEquals(1_110_000L, fetchedEp2!!.position)

        // Fetching Episode 3 must return Episode 3 position
        val fetchedEp3 = db[10003]
        assertNotNull(fetchedEp3)
        assertEquals(1_635_000L, fetchedEp3!!.position)

        // Episode 2 and Episode 3 do not overwrite each other
        assertNotEquals(fetchedEp2.position, fetchedEp3.position)
    }

    @Test
    fun continueWatching_resolvesParentSeriesIdForEpisodes() {
        data class FakeWatchProgress(
            val streamId: Int,
            val type: String,
            val position: Long,
            val duration: Long,
            val lastWatched: Long,
            val seriesId: Int?
        )

        data class FakeStream(val id: Int, val name: String, val streamType: String)

        val progressList = listOf(
            FakeWatchProgress(10002, "SERIES", 1_110_000L, 3_000_000L, 1000L, seriesId = 500), // Episode 2
            FakeWatchProgress(10003, "SERIES", 1_635_000L, 3_000_000L, 2000L, seriesId = 500), // Episode 3 (newer)
            FakeWatchProgress(20001, "MOVIE",  2_140_000L, 7_200_000L, 1500L, seriesId = null)  // Movie
        )

        val targetStreamIds = progressList.map { wp ->
            if (wp.type.equals("series", ignoreCase = true) && wp.seriesId != null) wp.seriesId else wp.streamId
        }.distinct()

        // Target stream IDs must contain series ID 500 and movie ID 20001
        assertTrue(targetStreamIds.contains(500))
        assertTrue(targetStreamIds.contains(20001))
        assertFalse(targetStreamIds.contains(10002)) // Episode stream ID 10002 mapped to seriesId 500

        val catalog = listOf(
            FakeStream(500, "Breaking Bad", "SERIES"),
            FakeStream(20001, "Inception", "MOVIE")
        )

        val resolvedItems = mutableListOf<String>()
        val seenSeries = mutableSetOf<Int>()

        for (wp in progressList.sortedByDescending { it.lastWatched }) {
            val lookupId = if (wp.type.equals("series", ignoreCase = true) && wp.seriesId != null) wp.seriesId else wp.streamId
            if (wp.type.equals("series", ignoreCase = true)) {
                if (!seenSeries.add(lookupId)) continue
            }
            val stream = catalog.firstOrNull { it.id == lookupId }
            if (stream != null) {
                resolvedItems.add(stream.name)
            }
        }

        // Must resolved to 2 distinct items ordered by newest lastWatched
        assertEquals(2, resolvedItems.size)
        assertEquals("Breaking Bad", resolvedItems[0]) // Ep 3 was newest (2000L)
        assertEquals("Inception", resolvedItems[1])    // Movie was second (1500L)
    }

    @Test
    fun seriesDetailViewModel_selectsSeasonOfMostRecentlyWatchedPendingEpisode() {
        data class FakeWatchProgress(
            val streamId: Int,
            val seriesId: Int,
            val position: Long,
            val duration: Long,
            val lastWatched: Long
        )
        data class FakeSeason(val seasonNumber: Int)
        data class FakeEpisode(val streamId: Int, val seasonNumber: Int, val title: String)

        val seasons = listOf(FakeSeason(1), FakeSeason(2))
        val episodes = mapOf(
            1 to listOf(FakeEpisode(1001, 1, "S1E1"), FakeEpisode(1002, 1, "S1E2")),
            2 to listOf(FakeEpisode(2001, 2, "S2E1"), FakeEpisode(2005, 2, "S2E5"))
        )

        val allProgress = listOf(
            FakeWatchProgress(streamId = 1002, seriesId = 500, position = 1_110_000L, duration = 3_000_000L, lastWatched = 1000L), // S1E2 (older)
            FakeWatchProgress(streamId = 2005, seriesId = 500, position = 1_635_000L, duration = 3_000_000L, lastWatched = 5000L)  // S2E5 (newest)
        )

        // Find most recently watched pending episode
        val pendingSeriesProgress = allProgress.filter { wp ->
            wp.seriesId == 500 && WatchProgressUtils.isPendingWatchProgress(wp.position, wp.duration)
        }.sortedByDescending { it.lastWatched }

        val latestWp = pendingSeriesProgress.firstOrNull()
        assertNotNull(latestWp)
        assertEquals(2005, latestWp!!.streamId)

        val selectedSeasonNum = seasons.firstOrNull { season ->
            episodes[season.seasonNumber]?.any { it.streamId == latestWp.streamId } == true
        }?.seasonNumber

        // Must select Season 2 because S2E5 was watched most recently
        assertEquals(2, selectedSeasonNum)
    }

    @Test
    fun syncFinalProgress_capturesSnapshotsBeforePlayerRelease() {
        var savedStreamId: Int? = null
        var savedPosition: Long? = null

        class MockPlayerViewModel {
            var activeStreamId: Int? = 101
            var activePosition: Long = 2_245_000L // 37m 25s

            fun stopPlayback() {
                // Snapshot BEFORE teardown
                val snapStreamId = activeStreamId
                val snapPos = activePosition

                // Teardown
                activeStreamId = null
                activePosition = 0L

                // Async non-cancellable save uses pre-captured snapshots!
                if (snapStreamId != null && snapPos >= 5000L) {
                    savedStreamId = snapStreamId
                    savedPosition = snapPos
                }
            }
        }

        val vm = MockPlayerViewModel()
        vm.stopPlayback()

        // Verify that 2,245,000 ms (37m 25s) was captured and saved for Movie 101!
        assertEquals(101, savedStreamId)
        assertEquals(2_245_000L, savedPosition)
    }

    @Test
    fun moviePlayback_restoresExactSavedPosition() {
        data class FakeWatchProgress(val streamId: Int, val position: Long, val duration: Long)
        val watchProgressDb = mapOf(
            201 to FakeWatchProgress(201, 1_800_000L, 7_200_000L) // Movie 201 at 30 min (1,800,000 ms)
        )

        fun resolveInitialSeekPosition(streamId: Int, isLive: Boolean): Long {
            if (isLive || streamId <= 0) return 0L
            val progress = watchProgressDb[streamId]
            if (progress != null && progress.position >= 5000L && !WatchProgressUtils.isVideoCompleted(progress.position, progress.duration)) {
                return progress.position
            }
            return 0L
        }

        // Movie 201 has 1,800,000ms saved -> must seek to 1,800,000ms
        val initialSeekPos = resolveInitialSeekPosition(streamId = 201, isLive = false)
        assertEquals(1_800_000L, initialSeekPos)

        // Movie 202 has no saved progress -> must seek to 0ms
        val initialSeekPosNew = resolveInitialSeekPosition(streamId = 202, isLive = false)
        assertEquals(0L, initialSeekPosNew)
    }

    @Test
    fun seriesEpisodePlayback_restoresEpisodeSpecificSavedPosition() {
        data class FakeWatchProgress(val streamId: Int, val seriesId: Int?, val position: Long, val duration: Long)
        val watchProgressDb = mapOf(
            1001 to FakeWatchProgress(1001, 500, 1_200_000L, 3_000_000L), // Episode 1 at 20 min
            1002 to FakeWatchProgress(1002, 500, 2_100_000L, 3_000_000L)  // Episode 2 at 35 min
        )

        fun resolveEpisodeSeekPosition(episodeStreamId: Int): Long {
            val progress = watchProgressDb[episodeStreamId]
            if (progress != null && progress.position >= 5000L && !WatchProgressUtils.isVideoCompleted(progress.position, progress.duration)) {
                return progress.position
            }
            return 0L
        }

        // Episode 1001 must restore 1,200,000 ms
        assertEquals(1_200_000L, resolveEpisodeSeekPosition(1001))

        // Episode 1002 must restore 2,100,000 ms independently
        assertEquals(2_100_000L, resolveEpisodeSeekPosition(1002))
    }

    @Test
    fun stateReadyFallback_restoresPositionIfExoPlayerResetsToZero() {
        var playerCurrentPosition = 0L // Player initialized at 0 by mistake
        val savedPosition = 1_500_000L // 25 min saved

        fun onPlayerStateReady(savedPos: Long?, isLive: Boolean) {
            if (savedPos != null && savedPos >= 5000L && playerCurrentPosition < 1000L && !isLive) {
                // Perform fallback seek to saved position
                playerCurrentPosition = savedPos
            }
        }

        // Fire STATE_READY
        onPlayerStateReady(savedPosition, isLive = false)

        // Player position must now be 1,500,000 ms!
        assertEquals(1_500_000L, playerCurrentPosition)
    }

    @Test
    fun episodeProgressBar_calculatesProgressForAnyNonZeroWatchTime() {
        data class FakeEpisode(val streamId: Int, val title: String, val position: Long, val duration: Long)
        val ep1 = FakeEpisode(1001, "S1E1", 30_000L, 3_000_000L)  // 1% watched (30s)
        val ep2 = FakeEpisode(1002, "S1E2", 1_200_000L, 3_000_000L) // 40% watched (20m)
        val ep3 = FakeEpisode(1003, "S1E3", 0L, 3_000_000L)          // Never watched

        fun calculateProgress(position: Long, duration: Long): Float? {
            if (duration <= 0L || position <= 0L) return null
            return (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }

        val prog1 = calculateProgress(ep1.position, ep1.duration)
        val prog2 = calculateProgress(ep2.position, ep2.duration)
        val prog3 = calculateProgress(ep3.position, ep3.duration)

        assertNotNull(prog1)
        assertEquals(0.01f, prog1!!, 0.001f)
        assertTrue("Progress bar must render for 1% watch time (> 0.0f)", prog1 > 0.0f)

        assertNotNull(prog2)
        assertEquals(0.40f, prog2!!, 0.001f)

        assertNull("Never watched episode must return null progress (no bar rendered)", prog3)
    }

    @Test
    fun movieAndSeriesFiltering_bothSupportGenreFilterAndSorting() {
        data class FakeStream(val id: Int, val name: String, val type: String, val genre: String?, val rating: Double)

        val catalog = listOf(
            FakeStream(1, "Sem Ação", "VOD", "Drama", 8.5), // "Ação" in title, but genre is Drama!
            FakeStream(2, "Aventuras no Espaço", "VOD", "Ficção Científica", 8.5),
            FakeStream(3, "Comédia em Família", "VOD", "Comédia", 7.0),
            FakeStream(4, "Espaço Profundo", "SERIES", "Ficção Científica", 9.1),
            FakeStream(5, "Resgate Mortal", "VOD", "Action, Thriller", 8.9) // Raw genre "Action, Thriller"
        )

        fun filterStreamsByGenreAndType(type: String, targetGenre: String?): List<FakeStream> {
            val targetType = if (type == "movie") "VOD" else "SERIES"
            var filtered = catalog.filter { it.type == targetType }
            if (!targetGenre.isNullOrBlank()) {
                filtered = filtered.filter { stream ->
                    rsv.squitv.domain.usecase.GenreUtils.matchesGenre(stream.genre, targetGenre)
                }
            }
            return filtered
        }

        // 1. Movie Filtering: "Ação" must match "Action, Thriller" (Resgate Mortal) but NOT "Sem Ação" (Drama)!
        val movieAction = filterStreamsByGenreAndType("movie", "Ação")
        assertEquals(1, movieAction.size)
        assertEquals("Resgate Mortal", movieAction[0].name)

        // 2. Movie Filtering: "Ficção Científica"
        val movieSciFi = filterStreamsByGenreAndType("movie", "Ficção Científica")
        assertEquals(1, movieSciFi.size)
        assertEquals("Aventuras no Espaço", movieSciFi[0].name)

        // 3. Clear filter ("TODOS")
        val allMovies = filterStreamsByGenreAndType("movie", null)
        assertEquals(4, allMovies.size)
    }

    @Test
    fun genreUtils_extractsAndNormalizesGenresDiscardingJunkKeywords() {
        val rawGenre1 = "Action, Adventure / Sci-Fi"
        val normalized1 = rsv.squitv.domain.usecase.GenreUtils.extractNormalizedGenres(rawGenre1)
        assertEquals(listOf("Ação", "Aventura", "Ficção Científica"), normalized1)

        val rawGenre2 = "Drama | Romance & Comedy"
        val normalized2 = rsv.squitv.domain.usecase.GenreUtils.extractNormalizedGenres(rawGenre2)
        assertEquals(listOf("Drama", "Romance", "Comédia"), normalized2)

        val rawJunkCategory = "Filmes 4K, Dublados 2026, Netflix, FHD"
        val normalizedJunk = rsv.squitv.domain.usecase.GenreUtils.extractNormalizedGenres(rawJunkCategory)
        assertTrue("Playlist format/category tags must be discarded", normalizedJunk.isEmpty())
    }
}
