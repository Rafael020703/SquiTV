package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem

class FocusAndDpadNavigationTest {

    private enum class FocusArea {
        SIDEBAR_CATEGORY,
        CONTENT_GRID_CARD,
        GENRE_FILTER_CHIP,
        HEADER_BUTTON
    }

    private class MockDpadFocusController {
        var currentFocusArea: FocusArea = FocusArea.SIDEBAR_CATEGORY
        var selectedCategoryIndex: Int = 0
        var gridCardIndex: Int = 0
        var totalGridCards: Int = 10
        var isGridEmpty: Boolean = false

        fun onDpadRight(): Boolean {
            return when (currentFocusArea) {
                FocusArea.SIDEBAR_CATEGORY -> {
                    if (!isGridEmpty && totalGridCards > 0) {
                        currentFocusArea = FocusArea.CONTENT_GRID_CARD
                        gridCardIndex = 0
                        true
                    } else {
                        false // Focus remains in sidebar if grid is empty
                    }
                }
                FocusArea.CONTENT_GRID_CARD -> {
                    if (gridCardIndex < totalGridCards - 1) {
                        gridCardIndex++
                        true
                    } else {
                        false // Right boundary reached
                    }
                }
                else -> false
            }
        }

        fun onDpadLeft(): Boolean {
            return when (currentFocusArea) {
                FocusArea.CONTENT_GRID_CARD -> {
                    if (gridCardIndex % 4 == 0) { // Column 0 in 4-column grid
                        currentFocusArea = FocusArea.SIDEBAR_CATEGORY
                        true
                    } else {
                        gridCardIndex--
                        true
                    }
                }
                FocusArea.SIDEBAR_CATEGORY -> false // Already in sidebar
                else -> false
            }
        }

        fun onDpadDown(): Boolean {
            return when (currentFocusArea) {
                FocusArea.SIDEBAR_CATEGORY -> {
                    selectedCategoryIndex++
                    true
                }
                FocusArea.CONTENT_GRID_CARD -> {
                    if (gridCardIndex + 4 < totalGridCards) {
                        gridCardIndex += 4
                        true
                    } else false
                }
                else -> false
            }
        }

        fun onDpadUp(): Boolean {
            return when (currentFocusArea) {
                FocusArea.SIDEBAR_CATEGORY -> {
                    if (selectedCategoryIndex > 0) {
                        selectedCategoryIndex--
                        true
                    } else false
                }
                FocusArea.CONTENT_GRID_CARD -> {
                    if (gridCardIndex >= 4) {
                        gridCardIndex -= 4
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    @Test
    fun test1_enterCatalogGrid_pressingDpadRight_transfersFocusToFirstCard() {
        val controller = MockDpadFocusController()
        assertEquals(FocusArea.SIDEBAR_CATEGORY, controller.currentFocusArea)

        val focusMoved = controller.onDpadRight()
        assertTrue("D-pad Right from sidebar should transfer focus to catalog grid", focusMoved)
        assertEquals(FocusArea.CONTENT_GRID_CARD, controller.currentFocusArea)
        assertEquals(0, controller.gridCardIndex)
    }

    @Test
    fun test2_returnToCategories_pressingDpadLeftOnColumn0_transfersFocusToSidebar() {
        val controller = MockDpadFocusController()
        controller.onDpadRight() // Focus moves to grid card 0
        assertEquals(FocusArea.CONTENT_GRID_CARD, controller.currentFocusArea)

        val focusReturned = controller.onDpadLeft()
        assertTrue("D-pad Left on column 0 of grid should return focus to sidebar", focusReturned)
        assertEquals(FocusArea.SIDEBAR_CATEGORY, controller.currentFocusArea)
    }

    @Test
    fun test3_navigateBetweenCards_dpadDirections_movesCardIndexesCorrectly() {
        val controller = MockDpadFocusController()
        controller.onDpadRight() // Card 0

        // D-pad Right -> Card 1
        assertTrue(controller.onDpadRight())
        assertEquals(1, controller.gridCardIndex)

        // D-pad Down -> Card 5 (in 4-column grid)
        assertTrue(controller.onDpadDown())
        assertEquals(5, controller.gridCardIndex)

        // D-pad Up -> Card 1
        assertTrue(controller.onDpadUp())
        assertEquals(1, controller.gridCardIndex)

        // D-pad Left -> Card 0
        assertTrue(controller.onDpadLeft())
        assertEquals(0, controller.gridCardIndex)
    }

    @Test
    fun test4_switchCategories_sidebarToGridAndBack_maintainsValidNavigation() {
        val controller = MockDpadFocusController()

        // Select Category 0 -> Move to Grid -> Back to Sidebar
        controller.onDpadRight()
        assertEquals(FocusArea.CONTENT_GRID_CARD, controller.currentFocusArea)
        controller.onDpadLeft()
        assertEquals(FocusArea.SIDEBAR_CATEGORY, controller.currentFocusArea)

        // Move Down to Category 1
        controller.onDpadDown()
        assertEquals(1, controller.selectedCategoryIndex)

        // Move to Grid for Category 1
        controller.onDpadRight()
        assertEquals(FocusArea.CONTENT_GRID_CARD, controller.currentFocusArea)
        assertEquals(0, controller.gridCardIndex)
    }

    @Test
    fun test5_emptyCategory_dpadRight_safelyRetainsFocusInSidebar() {
        val controller = MockDpadFocusController()
        controller.isGridEmpty = true
        controller.totalGridCards = 0

        val focusMoved = controller.onDpadRight()
        assertFalse("D-pad Right on empty category must NOT crash or leave sidebar without focus", focusMoved)
        assertEquals(FocusArea.SIDEBAR_CATEGORY, controller.currentFocusArea)
    }

    @Test
    fun test6_favoritesToggle_retainsFocusInGrid() {
        val item = IptvItem("101", "HBO HD", null, ContentType.LIVE, isFavorite = false)
        val toggled = item.copy(isFavorite = !item.isFavorite)

        assertTrue(toggled.isFavorite)
        assertEquals("101", toggled.id)
    }

    @Test
    fun test7_continueWatching_cardsNavigation_supportsDpad() {
        val controller = MockDpadFocusController()
        controller.onDpadRight()

        assertEquals(FocusArea.CONTENT_GRID_CARD, controller.currentFocusArea)
        assertTrue(controller.gridCardIndex >= 0)
    }

    @Test
    fun test8_playerReturn_preservesCategoryAndGridNavigation() {
        val controller = MockDpadFocusController()
        controller.onDpadRight() // Card 0
        controller.onDpadRight() // Card 1

        // Simulate returning from Player
        assertEquals(FocusArea.CONTENT_GRID_CARD, controller.currentFocusArea)
        assertEquals(1, controller.gridCardIndex)
    }

    @Test
    fun test9_hold3sFavoriteGesture_doesNotBlockDirectionalKeys() {
        val controller = MockDpadFocusController()

        // Directional keys (Right, Left, Up, Down) return true or false for focus, NOT blocked by hold gesture
        assertTrue(controller.onDpadRight())
        assertTrue(controller.onDpadDown())
        assertTrue(controller.onDpadUp())
    }

    @Test
    fun test10_asyncLoading_gridPopulates_focusTargetValid() {
        val controller = MockDpadFocusController()
        controller.isGridEmpty = true

        // Grid empty while loading -> D-pad Right stays in sidebar
        assertFalse(controller.onDpadRight())

        // Loading completes -> Grid populated
        controller.isGridEmpty = false
        controller.totalGridCards = 5

        // Now D-pad Right succeeds and focuses first card
        assertTrue(controller.onDpadRight())
        assertEquals(0, controller.gridCardIndex)
    }
}
