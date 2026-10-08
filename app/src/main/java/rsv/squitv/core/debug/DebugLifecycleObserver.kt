package rsv.squitv.core.debug

import android.app.Activity
import android.app.Application
import android.os.Bundle
import rsv.squitv.BuildConfig

class DebugLifecycleObserver : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (!BuildConfig.DEBUG) return
        val name = activity.javaClass.simpleName
        DebugLogger.currentActivity.set(name)

        val isRecreation = savedInstanceState != null
        DebugLogger.log(
            level = DebugLevel.INFO,
            category = DebugCategory.LIFECYCLE,
            event = "ACTIVITY_CREATED",
            component = name,
            context = mapOf(
                "isRecreation" to isRecreation.toString(),
                "intent" to (activity.intent?.action ?: "none")
            )
        )
    }

    override fun onActivityStarted(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        val name = activity.javaClass.simpleName
        DebugLogger.currentActivity.set(name)
        DebugLogger.log(
            level = DebugLevel.TRACE,
            category = DebugCategory.LIFECYCLE,
            event = "ACTIVITY_STARTED",
            component = name
        )
    }

    override fun onActivityResumed(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        val name = activity.javaClass.simpleName
        DebugLogger.currentActivity.set(name)
        DebugLogger.log(
            level = DebugLevel.TRACE,
            category = DebugCategory.LIFECYCLE,
            event = "ACTIVITY_RESUMED",
            component = name
        )
    }

    override fun onActivityPaused(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        val name = activity.javaClass.simpleName
        DebugLogger.log(
            level = DebugLevel.TRACE,
            category = DebugCategory.LIFECYCLE,
            event = "ACTIVITY_PAUSED",
            component = name
        )
    }

    override fun onActivityStopped(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        val name = activity.javaClass.simpleName
        DebugLogger.log(
            level = DebugLevel.TRACE,
            category = DebugCategory.LIFECYCLE,
            event = "ACTIVITY_STOPPED",
            component = name
        )
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        if (!BuildConfig.DEBUG) return
        val name = activity.javaClass.simpleName
        DebugLogger.log(
            level = DebugLevel.TRACE,
            category = DebugCategory.LIFECYCLE,
            event = "ACTIVITY_SAVE_INSTANCE_STATE",
            component = name
        )
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        val name = activity.javaClass.simpleName
        val isFinishing = activity.isFinishing
        val isConfigChange = activity.isChangingConfigurations

        DebugLogger.log(
            level = DebugLevel.INFO,
            category = DebugCategory.LIFECYCLE,
            event = "ACTIVITY_DESTROYED",
            component = name,
            context = mapOf(
                "isFinishing" to isFinishing.toString(),
                "isChangingConfigurations" to isConfigChange.toString()
            )
        )
    }
}
