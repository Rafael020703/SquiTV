package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.data.local.entities.IptvCategoryEntity

class ContentCustomizationTest {

    private fun applyCategoryCustomization(
        categories: List<IptvCategoryEntity>,
        customOrder: List<String>? = null,
        hiddenCategories: Set<String>? = null
    ): List<IptvCategoryEntity> {
        var result = categories
        if (!hiddenCategories.isNullOrEmpty()) {
            result = result.filter { it.id !in hiddenCategories }
        }
        if (!customOrder.isNullOrEmpty()) {
            val orderMap = customOrder.withIndex().associate { it.value to it.index }
            result = result.sortedWith(compareBy { orderMap[it.id] ?: Int.MAX_VALUE })
        }
        return result
    }

    @Test
    fun applyCategoryCustomization_reordersCategories_basedOnCustomOrderList() {
        val rawCategories = listOf(
            IptvCategoryEntity("cat1", "Notícias", "LIVE"),
            IptvCategoryEntity("cat2", "Esportes", "LIVE"),
            IptvCategoryEntity("cat3", "Filmes", "LIVE")
        )

        val customOrder = listOf("cat2", "cat3", "cat1")
        val customized = applyCategoryCustomization(rawCategories, customOrder = customOrder)

        assertEquals(3, customized.size)
        assertEquals("cat2", customized[0].id)
        assertEquals("cat3", customized[1].id)
        assertEquals("cat1", customized[2].id)
    }

    @Test
    fun applyCategoryCustomization_filtersOutHiddenCategories() {
        val rawCategories = listOf(
            IptvCategoryEntity("cat1", "Notícias", "LIVE"),
            IptvCategoryEntity("cat2", "Esportes", "LIVE"),
            IptvCategoryEntity("cat3", "Variedades", "LIVE")
        )

        val hiddenCategories = setOf("cat2")
        val customized = applyCategoryCustomization(rawCategories, hiddenCategories = hiddenCategories)

        assertEquals(2, customized.size)
        assertFalse(customized.any { it.id == "cat2" })
    }

    @Test
    fun applyCategoryCustomization_preservesCustomOrder_andAppendsNewCategoriesFromResyncAtBottom() {
        // Simulating server re-sync where "cat4" is a new category added by the provider
        val rawCategoriesFromResync = listOf(
            IptvCategoryEntity("cat1", "Notícias", "LIVE"),
            IptvCategoryEntity("cat2", "Esportes", "LIVE"),
            IptvCategoryEntity("cat3", "Filmes", "LIVE"),
            IptvCategoryEntity("cat4", "Lançamentos 2026", "LIVE") // NEW CATEGORY FROM SERVER
        )

        val customOrder = listOf("cat3", "cat1")
        val customized = applyCategoryCustomization(rawCategoriesFromResync, customOrder = customOrder)

        assertEquals(4, customized.size)
        assertEquals("cat3", customized[0].id) // First custom
        assertEquals("cat1", customized[1].id) // Second custom
        // New categories (cat2, cat4) without explicit custom order index are appended at bottom
        assertEquals("cat2", customized[2].id)
        assertEquals("cat4", customized[3].id)
    }

    @Test
    fun reorderCategoryList_moveUpAndDown_updatesListIndexesCorrectly() {
        val categories = mutableListOf("cat1", "cat2", "cat3")

        // Move cat2 UP -> ["cat2", "cat1", "cat3"]
        val idxUp = categories.indexOf("cat2")
        val itemUp = categories.removeAt(idxUp)
        categories.add(idxUp - 1, itemUp)

        assertEquals(listOf("cat2", "cat1", "cat3"), categories)

        // Move cat1 DOWN -> ["cat2", "cat3", "cat1"]
        val idxDown = categories.indexOf("cat1")
        val itemDown = categories.removeAt(idxDown)
        categories.add(idxDown + 1, itemDown)

        assertEquals(listOf("cat2", "cat3", "cat1"), categories)
    }

    @Test
    fun perAccountCustomizationKeys_differentAccounts_haveIsolatedPreferencesKeys() {
        fun makeKey(user: String, url: String, type: String) = "${url.lowercase().trim().removeSuffix("/")}#${user.lowercase().trim()}_${type.lowercase()}"

        val keyAccountA = makeKey("rafael", "http://serverA.com:8080", "live")
        val keyAccountB = makeKey("rafael", "http://serverB.com:8080", "live")

        assertNotEquals(keyAccountA, keyAccountB)
        assertEquals("http://servera.com:8080#rafael_live", keyAccountA)
        assertEquals("http://serverb.com:8080#rafael_live", keyAccountB)
    }

    @Test
    fun autoCenteringIndexCalculation_centeringIndexAroundTargetItem_computesValidViewportOffset() {
        fun computeCenteredScrollIndex(targetIndex: Int): Int = (targetIndex - 2).coerceAtLeast(0)

        assertEquals(0, computeCenteredScrollIndex(0))
        assertEquals(0, computeCenteredScrollIndex(1))
        assertEquals(0, computeCenteredScrollIndex(2))
        assertEquals(1, computeCenteredScrollIndex(3))
        assertEquals(3, computeCenteredScrollIndex(5))
    }

    @Test
    fun hidingCategory_doesNotAlterOriginalDatabaseEntities() {
        val databaseEntities = listOf(
            IptvCategoryEntity("cat1", "Esportes", "LIVE"),
            IptvCategoryEntity("cat2", "Notícias", "LIVE")
        )

        val hiddenCategories = setOf("cat1")
        val displayCategories = applyCategoryCustomization(databaseEntities, hiddenCategories = hiddenCategories)

        // Visual display list excludes cat1
        assertEquals(1, displayCategories.size)
        assertEquals("cat2", displayCategories[0].id)

        // Original database entity list is 100% untouched and preserved
        assertEquals(2, databaseEntities.size)
        assertTrue(databaseEntities.any { it.id == "cat1" })
    }
}
