package rsv.squitv.core.navigation

import android.util.Log
import rsv.squitv.core.debug.DebugCategory
import rsv.squitv.core.debug.DebugLevel
import rsv.squitv.core.debug.DebugLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppController @Inject constructor() {
    private val _actions = MutableSharedFlow<AppAction>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val actions = _actions.asSharedFlow()

    fun handleAction(action: AppAction) {
        _actions.tryEmit(action)
    }

    fun navigate(
        route: Route,
        popUpToRoute: Route? = null,
        inclusive: Boolean = false,
        launchSingleTop: Boolean = false
    ) {
        val oldRoute = DebugLogger.currentRoute.get()
        val newRouteName = route.javaClass.simpleName
        DebugLogger.currentRoute.set(newRouteName)

        DebugLogger.log(
            level = DebugLevel.INFO,
            category = DebugCategory.NAVIGATION,
            event = "NAVIGATION_START",
            oldState = oldRoute,
            newState = newRouteName,
            context = mapOf(
                "route" to route.toString(),
                "popUpTo" to (popUpToRoute?.toString() ?: "none"),
                "inclusive" to inclusive.toString()
            )
        )

        val caller = Thread.currentThread().stackTrace.getOrNull(3)
        Log.d("BACK_NAV_DEBUG", "NAV_REQUEST | route=$route | popUpTo=$popUpToRoute | inclusive=$inclusive | caller=$caller")
        handleAction(AppAction.Navigate(route, popUpToRoute, inclusive, launchSingleTop))
    }

    fun goBack(eventId: String = "manual") {
        DebugLogger.log(
            level = DebugLevel.INFO,
            category = DebugCategory.NAVIGATION,
            event = "NAVIGATION_BACK",
            context = mapOf("eventId" to eventId, "fromRoute" to (DebugLogger.currentRoute.get() ?: "unknown"))
        )
        Log.i("BACK_NAV_DEBUG", "GO_BACK_REQUEST | source=AppController | eventId=$eventId")
        handleAction(AppAction.Back(eventId))
    }
    fun exitApp() {
        Log.e("BACK_NAV_DEBUG", "EXIT_APP_REQUEST")
        handleAction(AppAction.ExitApp)
    }
}
