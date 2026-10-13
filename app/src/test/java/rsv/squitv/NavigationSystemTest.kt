package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.core.navigation.Route

class NavigationSystemTest {

    private class MockNavStack {
        val stack = mutableListOf<Route>()
        var showExitDialog = false

        fun navigate(route: Route, popUpToRoute: Route? = null, inclusive: Boolean = false) {
            showExitDialog = false
            if (popUpToRoute != null) {
                val idx = stack.indexOfLast { it.javaClass == popUpToRoute.javaClass }
                if (idx != -1) {
                    val popUntil = if (inclusive) idx else idx + 1
                    while (stack.size > popUntil) {
                        stack.removeAt(stack.size - 1)
                    }
                }
            }
            stack.add(route)
        }

        fun clearAndNavigateToDashboard() {
            stack.clear()
            stack.add(Route.Dashboard)
            showExitDialog = false
        }

        fun handleBack(): Boolean {
            if (showExitDialog) {
                showExitDialog = false
                return true
            }

            if (stack.size > 1) {
                showExitDialog = false
                stack.removeAt(stack.size - 1)
                return true
            } else {
                showExitDialog = true
                return false
            }
        }
    }

    @Test
    fun scenario1_dashboardBack_showsExitDialog() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)

        val handled = nav.handleBack()
        assertFalse("Popping root Dashboard should return false", handled)
        assertTrue("Exit dialog should be displayed when pressing Back on root Dashboard", nav.showExitDialog)
    }

    @Test
    fun scenario2_dashboardToMoviesBack_returnsToDashboardWithoutExitDialog() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)
        nav.navigate(Route.Movies())

        val handled = nav.handleBack()
        assertTrue("Popping MoviesScreen back to Dashboard should return true", handled)
        assertFalse("Exit dialog must NOT be displayed when returning from Movies to Dashboard", nav.showExitDialog)
        assertEquals(Route.Dashboard, nav.stack.last())
    }

    @Test
    fun scenario3_dashboardToSeriesBack_returnsToDashboardWithoutExitDialog() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)
        nav.navigate(Route.Series())

        val handled = nav.handleBack()
        assertTrue("Popping SeriesScreen back to Dashboard should return true", handled)
        assertFalse("Exit dialog must NOT be displayed when returning from Series to Dashboard", nav.showExitDialog)
        assertEquals(Route.Dashboard, nav.stack.last())
    }

    @Test
    fun scenario4_dashboardToSettingsBack_returnsToDashboardWithoutExitDialog() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)
        nav.navigate(Route.Settings)

        val handled = nav.handleBack()
        assertTrue("Popping SettingsScreen back to Dashboard should return true", handled)
        assertFalse("Exit dialog must NOT be displayed when returning from Settings to Dashboard", nav.showExitDialog)
        assertEquals(Route.Dashboard, nav.stack.last())
    }

    @Test
    fun scenario5_dashboardToMoviesToVodDetailBack_returnsToMovies() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)
        nav.navigate(Route.Movies())
        nav.navigate(Route.VodDetail(vodId = 101, vodName = "Inception"))

        val handled = nav.handleBack()
        assertTrue(handled)
        assertFalse(nav.showExitDialog)
        assertEquals(Route.Movies(), nav.stack.last())
    }

    @Test
    fun scenario6_vodDetailDoubleBack_returnsToMoviesThenDashboard() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)
        nav.navigate(Route.Movies())
        nav.navigate(Route.VodDetail(vodId = 101, vodName = "Inception"))

        // First Back: VodDetail -> Movies
        assertTrue(nav.handleBack())
        assertFalse(nav.showExitDialog)
        assertEquals(Route.Movies(), nav.stack.last())

        // Second Back: Movies -> Dashboard
        assertTrue(nav.handleBack())
        assertFalse(nav.showExitDialog)
        assertEquals(Route.Dashboard, nav.stack.last())

        // Third Back: Dashboard -> Exit Dialog
        assertFalse(nav.handleBack())
        assertTrue(nav.showExitDialog)
    }

    @Test
    fun scenario7_consecutiveSectionSwitching_maintainsCleanStack() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)

        // Select Movies from Dashboard
        nav.navigate(Route.Movies())
        assertEquals(2, nav.stack.size)

        // Select Series from Movies with popUpTo Dashboard
        nav.navigate(Route.Series(), popUpToRoute = Route.Dashboard, inclusive = false)
        assertEquals(2, nav.stack.size)
        assertEquals(Route.Dashboard, nav.stack[0])
        assertEquals(Route.Series(), nav.stack[1])

        // Select LiveChannels from Series with popUpTo Dashboard
        nav.navigate(Route.LiveChannels(), popUpToRoute = Route.Dashboard, inclusive = false)
        assertEquals(2, nav.stack.size)
        assertEquals(Route.Dashboard, nav.stack[0])
        assertEquals(Route.LiveChannels(), nav.stack[1])

        // Pressing Back from LiveChannels returns to Dashboard
        assertTrue(nav.handleBack())
        assertFalse(nav.showExitDialog)
        assertEquals(Route.Dashboard, nav.stack.last())
    }

    @Test
    fun scenario8_repeatedOpeningOfSameRoute_handledCleanly() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)
        nav.navigate(Route.Movies(categoryId = "1"))
        nav.navigate(Route.Movies(categoryId = "2"), popUpToRoute = Route.Dashboard, inclusive = false)

        assertEquals(2, nav.stack.size)
        assertEquals(Route.Movies(categoryId = "2"), nav.stack.last())
    }

    @Test
    fun scenario9_dialogOpenDuringBackPress_dismissesDialogFirst() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)

        // Press Back on Dashboard -> Exit dialog appears
        nav.handleBack()
        assertTrue(nav.showExitDialog)

        // Press Back again -> Dismisses exit dialog, stays on Dashboard
        val handled = nav.handleBack()
        assertTrue(handled)
        assertFalse(nav.showExitDialog)
        assertEquals(Route.Dashboard, nav.stack.last())
    }

    @Test
    fun scenario10_navigationAction_resetsExitDialogState() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)

        // Trigger exit dialog on Dashboard
        nav.handleBack()
        assertTrue(nav.showExitDialog)

        // User navigates to Settings while dialog was active
        nav.navigate(Route.Settings)
        assertFalse("Navigating to a new route must clear showExitDialog", nav.showExitDialog)

        // Press Back on Settings -> Returns to Dashboard without exit dialog
        assertTrue(nav.handleBack())
        assertFalse(nav.showExitDialog)
        assertEquals(Route.Dashboard, nav.stack.last())
    }

    @Test
    fun scenario11_loginAndSyncCompletion_clearsPreAuthStack() {
        val nav = MockNavStack()
        nav.navigate(Route.Initial)
        nav.navigate(Route.Login)
        nav.navigate(Route.Sync)

        // MainNavigation executes popUpTo(0) { inclusive = true } on transition to Ready
        nav.clearAndNavigateToDashboard()

        // Entire pre-auth stack (Initial, Login, Sync) is replaced by Dashboard
        assertEquals(1, nav.stack.size)
        assertEquals(Route.Dashboard, nav.stack.first())
    }

    @Test
    fun scenario12_afterReturningToDashboard_pressingBackShowsExitDialog() {
        val nav = MockNavStack()
        nav.navigate(Route.Dashboard)
        nav.navigate(Route.Movies())

        // Back from Movies -> Dashboard
        assertTrue(nav.handleBack())
        assertFalse(nav.showExitDialog)
        assertEquals(Route.Dashboard, nav.stack.last())

        // Press Back again on Dashboard -> Exit dialog appears
        assertFalse(nav.handleBack())
        assertTrue(nav.showExitDialog)
    }
}
