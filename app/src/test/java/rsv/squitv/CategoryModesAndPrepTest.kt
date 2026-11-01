package rsv.squitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rsv.squitv.data.local.entities.CatalogMetadataEntity
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.model.CategoryMode
import rsv.squitv.domain.model.ChannelQuality
import rsv.squitv.domain.model.ContentCategories
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem

class CategoryModesAndPrepTest {

    private fun item(id: Int, genre: String? = null, category: String = "cat1", type: ContentType = ContentType.MOVIE) =
        IptvItem(id.toString(), "Título $id", null, type, genre = genre, categoryId = category)

    @Test
    fun test1_categoryModeDefaultsToProvider() {
        val mode = CategoryMode.fromString(null)
        assertEquals(CategoryMode.PROVIDER, mode)
    }

    @Test
    fun test2_categoryModeGenreParsing() {
        assertEquals(CategoryMode.GENRE, CategoryMode.fromString("GENRE"))
        assertEquals(CategoryMode.GENRE, CategoryMode.fromString("genre"))
        assertEquals(CategoryMode.PROVIDER, CategoryMode.fromString("invalid"))
    }

    @Test
    fun test3_catalogInProviderMode_groupsByCategoryId() {
        val items = listOf(
            item(1, category = "cat1"),
            item(2, category = "cat2"),
            item(3, category = "cat1")
        )
        val providerCats = listOf("cat1" to "Ação Server", "cat2" to "Comédia Server")

        val result = ContentCategories.catalog(
            items = items,
            providerCategories = providerCats,
            allLabel = "Todos",
            unclassifiedLabel = "Sem categoria",
            scope = "scope",
            grouped = true,
            quality = ChannelQuality.AUTO,
            mode = CategoryMode.PROVIDER
        )

        assertEquals(3, result.size)
        assertEquals("ALL", result[0].id)
        assertEquals("cat1", result[1].id)
        assertEquals("Ação Server", result[1].name)
        assertEquals(2, result[1].count)
        assertEquals("cat2", result[2].id)
        assertEquals("Comédia Server", result[2].name)
        assertEquals(1, result[2].count)
    }

    @Test
    fun test4_catalogInGenreMode_groupsByContentMetadata() {
        val items = listOf(
            item(1, genre = "Ação"),
            item(2, genre = "Comédia"),
            item(3, genre = null)
        )
        val providerCats = listOf("cat1" to "Ação Server")

        val result = ContentCategories.catalog(
            items = items,
            providerCategories = providerCats,
            allLabel = "Todos",
            unclassifiedLabel = "Sem gênero informado",
            scope = "scope",
            grouped = true,
            quality = ChannelQuality.AUTO,
            mode = CategoryMode.GENRE
        )

        assertEquals(6, result.size)
        assertEquals("ALL", result[0].id)
        assertEquals("HEADER_GENRES", result[1].id)
        assertEquals("GENRE:Ação", result[2].id)
        assertEquals("GENRE:Comédia", result[3].id)
        assertEquals("HEADER_SERVER", result[4].id)
        assertEquals("FALLBACK:cat1", result[5].id)
        assertEquals("Ação Server", result[5].name)
    }

    @Test
    fun test5_negativeCachingAndBackoffLogic() {
        val entity = CatalogMetadataEntity(
            accountKey = "http://server#user",
            streamType = "VOD",
            streamId = 101,
            status = CatalogMetadataEntity.STATUS_PENDING
        )

        assertEquals("PENDING", entity.status)
        assertEquals(0L, entity.nextEligibleAt)

        val noDataEntity = entity.copy(
            status = CatalogMetadataEntity.STATUS_NO_DATA,
            nextEligibleAt = 1000L + (7 * 86400_000L)
        )

        assertEquals("NO_DATA", noDataEntity.status)
        assertTrue(noDataEntity.nextEligibleAt > 1000L)
    }

    @Test
    fun test6_appSettingsCategoryModeHelperMethods() {
        val appSettings = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24,
            movieCategoryModeMap = mapOf("acc1_p1" to "GENRE"),
            moviePromptDismissedMap = mapOf("acc1_p1" to true)
        )

        assertEquals(CategoryMode.GENRE, appSettings.getMovieCategoryMode("acc1", "p1"))
        assertEquals(CategoryMode.PROVIDER, appSettings.getSeriesCategoryMode("acc1", "p1"))
        assertTrue(appSettings.isMoviePromptDismissed("acc1", "p1"))
        assertFalse(appSettings.isSeriesPromptDismissed("acc1", "p1"))
    }
}
