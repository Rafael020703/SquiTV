package rsv.squitv

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.core.navigation.AppController
import rsv.squitv.core.navigation.Route

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ExternalRouteTest {
    @Test fun queuedBeforeUi() {
        val controller = AppController()
        controller.requestExternalRoute(Route.Updates)
        assertEquals(Route.Updates, controller.externalRoute.value)
    }
    @Test fun consumedOnce() {
        val controller = AppController()
        controller.requestExternalRoute(Route.Updates)
        assertTrue(controller.consumeExternalRoute(Route.Updates))
        assertNull(controller.externalRoute.value)
        assertFalse(controller.consumeExternalRoute(Route.Updates))
    }
    @Test fun newerRequest() {
        val controller = AppController()
        controller.requestExternalRoute(Route.Updates)
        controller.requestExternalRoute(Route.LocalLogin)
        assertFalse(controller.consumeExternalRoute(Route.Updates))
        assertEquals(Route.LocalLogin, controller.externalRoute.value)
    }
}
