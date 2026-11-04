package rsv.squitv.shared.catalog

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.shared.player.TrackLanguage

class ContentDiscoveryTest {
    @Test fun trailersAreCanonicalizedToHttpsAndUnrelatedHostsAreRejected() {
        val id = "dQw4w9WgXcQ"
        val canonical = "https://www.youtube.com/watch?v=$id"
        listOf(id, "http://youtu.be/$id", "https://www.youtube.com/watch?v=$id&t=1",
            "https://youtube.com/embed/$id", "https://youtube.com/shorts/$id").forEach {
            assertEquals(canonical, ContentDiscovery.trailerUrl(it))
        }
        assertNull(ContentDiscovery.trailerUrl("https://youtube.com.evil.invalid/watch?v=$id"))
        assertNull(ContentDiscovery.trailerUrl("https://fixture@youtube.com/watch?v=$id"))
        assertNull(ContentDiscovery.trailerUrl("file:///fixture"))
    }
    @Test fun suggestionsStayInTheKnownCategoryAndExcludeTheCurrentItem() {
        data class Item(val id: Int, val category: String)
        val items = (1..25).map { Item(it, if (it <= 20) "one" else "two") }
        val selected = ContentDiscovery.similar(items, 1, "one", { it.id }, { it.category })
        assertEquals(15, selected.size)
        assertTrue(selected.all { it.category == "one" && it.id != 1 })
        assertTrue(ContentDiscovery.similar(items, 1, null, { it.id }, { it.category }).isEmpty())
        assertTrue(ContentDiscovery.matchesPerson("Ada One", "Ada One, Name Two", null))
        assertFalse(ContentDiscovery.matchesPerson("Ada", "Ada One, Name Two", null))
    }
    @Test fun languagesUseCodesAndAmbiguousLabelsRemainLocal() {
        assertEquals("pt", TrackLanguage.normalize("por"))
        assertEquals("en", TrackLanguage.fromLabel("English [eng]"))
        assertEquals("pt", TrackLanguage.fromLabel("Português (pt-BR)"))
        assertNull(TrackLanguage.fromLabel("English / Português"))
        assertNull(TrackLanguage.fromLabel("Faixa 2"))
    }
    @Test fun androidOrderingIsStableAndRatingsAreNumeric() {
        data class Item(val name: String, val date: String?, val rating: String?)
        val items = listOf(Item("Zulu", "2020-01-01", "9.5"), Item("alpha", "2026-01-01", "10"), Item("Beta", null, null))
        fun sort(order: String) = CatalogOrdering.sort(items, order, { it.name }, { it.date }, { it.rating })
        assertEquals(listOf("alpha", "Beta", "Zulu"), sort("NAME").map { it.name })
        assertEquals("alpha", sort("RECENT").first().name)
        assertEquals("alpha", sort("RATING").first().name)
        assertEquals("Beta", sort("RATING").last().name)
    }
}
