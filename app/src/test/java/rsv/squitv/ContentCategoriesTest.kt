package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.domain.model.*

class ContentCategoriesTest {
    private fun item(id: Int, genre: String? = null, category: String = "server", type: ContentType = ContentType.MOVIE) =
        IptvItem(id.toString(), "Título $id", null, type, genre = genre, categoryId = category)
    private fun categories(items: List<IptvItem>, providers: List<Pair<String, String>> = emptyList(), grouped: Boolean = true, mode: CategoryMode = CategoryMode.GENRE) =
        ContentCategories.catalog(
            items = items,
            providerCategories = providers,
            allLabel = "Todos",
            unclassifiedLabel = "Sem gênero informado",
            scope = "scope",
            grouped = grouped,
            quality = ChannelQuality.AUTO,
            mode = mode
        )

    @Test fun detailGenreShortcutsUseTheSameCanonicalCategoryIds() {
        assertEquals("GENRE:Ação", ContentCategories.genreId("Action"))
        assertEquals("GENRE:Ficção Científica", ContentCategories.genreId("Science Fiction"))
        assertEquals("GENRE:Drama", ContentCategories.genreId("Drama"))
    }
    @Test fun genresComeFromActualContentAndNeverFromServerNames() {
        val result = categories(listOf(item(1, "Drama"), item(2, null)),
            listOf("server" to "Ação", "empty" to "Terror"))
        assertEquals(listOf("ALL", "HEADER_GENRES", "GENRE:Drama", "HEADER_SERVER", "FALLBACK:server"), result.map { it.id })
        assertFalse(result.any { it.name == "Terror" })
    }
    @Test fun aliasesCountOneTitleOncePerGenreAndOpenTheExactCountedItems() {
        val result = categories(listOf(item(1, "Action, Ação / Drama"), item(2, "Action")))
        val action = result.single { it.id == "GENRE:Ação" }
        assertEquals(2, action.count)
        assertEquals(listOf("1", "2"), action.items.map { it.id })
        assertEquals(listOf("ALL", "HEADER_GENRES", "GENRE:Ação", "GENRE:Drama"), result.map { it.id })
    }
    @Test fun liveCategoriesWithZeroItemsDisappearAndCountsRespectGrouping() {
        val items = listOf(item(1, category = "a", type = ContentType.LIVE).copy(name = "Canal HD"),
            item(2, category = "a", type = ContentType.LIVE).copy(name = "Canal FHD"))
        val providers = listOf("empty" to "Vazia", "a" to "Ao vivo")
        assertEquals(listOf("ALL", "a"), categories(items, providers).map { it.id })
        assertEquals(listOf(1, 1), categories(items, providers).map { it.count })
        assertEquals(listOf(2, 2), categories(items, providers, false).map { it.count })
    }
    @Test fun emptySpecialsAreHiddenAndRestoredEmptySelectionsFallBackToActualContent() {
        val base = categories(listOf(item(1, "Drama")))
        val entries = ContentCategories.withSpecials(base, listOf(
            ContentCategory("FAVORITES", "Favoritos", emptyList()),
            ContentCategory("RECENTS", "Recentes", listOf(item(1)))))
        assertFalse(entries.any { it.id == "FAVORITES" })
        assertEquals("ALL", ContentCategories.selected(entries, "old-provider-id")!!.id)
        assertEquals("ALL", ContentCategories.selected(entries, "GENRE:Ação")!!.id)
        assertEquals("GENRE:Drama", ContentCategories.selected(entries, "GENRE:Drama")!!.id)
        assertTrue(categories(emptyList()).isEmpty())
        assertNull(ContentCategories.selected(emptyList(), "ALL"))
    }
}
