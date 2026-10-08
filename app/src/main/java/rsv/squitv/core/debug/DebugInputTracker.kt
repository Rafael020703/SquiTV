package rsv.squitv.core.debug

import android.view.KeyEvent
import rsv.squitv.BuildConfig

object DebugInputTracker {

    fun trackKeyEvent(event: KeyEvent): Boolean {
        if (!BuildConfig.DEBUG) return false

        val keyCodeName = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "DPAD_UP"
            KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD_DOWN"
            KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD_LEFT"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
            KeyEvent.KEYCODE_DPAD_CENTER -> "DPAD_CENTER"
            KeyEvent.KEYCODE_ENTER -> "ENTER"
            KeyEvent.KEYCODE_BACK -> "BACK"
            KeyEvent.KEYCODE_MENU -> "MENU"
            KeyEvent.KEYCODE_CHANNEL_UP -> "CHANNEL_UP"
            KeyEvent.KEYCODE_CHANNEL_DOWN -> "CHANNEL_DOWN"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "PLAY"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "PAUSE"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "PLAY_PAUSE"
            KeyEvent.KEYCODE_MEDIA_STOP -> "STOP"
            else -> "KEY_${event.keyCode}"
        }

        val actionName = when (event.action) {
            KeyEvent.ACTION_DOWN -> "KEY_DOWN"
            KeyEvent.ACTION_UP -> "KEY_UP"
            else -> "ACTION_${event.action}"
        }

        val actionId = DebugCorrelation.newActionId(keyCodeName)
        DebugLogger.activeActionId.set(actionId)

        DebugLogger.log(
            level = DebugLevel.TRACE,
            category = if (keyCodeName.startsWith("DPAD") || keyCodeName == "ENTER" || keyCodeName == "BACK") DebugCategory.DPAD else DebugCategory.INPUT,
            event = "KEY_EVENT",
            actionId = actionId,
            context = mapOf(
                "key" to keyCodeName,
                "action" to actionName,
                "repeatCount" to event.repeatCount.toString()
            )
        )
        return false // Return false so we NEVER consume or interfere with normal key handling!
    }

    fun trackFocusChange(viewIdOrName: String, hasFocus: Boolean, screenName: String? = null) {
        if (!BuildConfig.DEBUG) return
        DebugLogger.log(
            level = DebugLevel.TRACE,
            category = DebugCategory.FOCUS,
            event = if (hasFocus) "FOCUS_GAIN" else "FOCUS_LOSS",
            component = screenName ?: DebugLogger.currentRoute.get(),
            context = mapOf(
                "view" to viewIdOrName,
                "hasFocus" to hasFocus.toString()
            )
        )
    }

    fun trackClick(elementName: String, screenName: String? = null, channelId: String? = null) {
        if (!BuildConfig.DEBUG) return
        val actionId = DebugCorrelation.newActionId("CLICK")
        DebugLogger.activeActionId.set(actionId)

        DebugLogger.log(
            level = DebugLevel.INFO,
            category = DebugCategory.UI,
            event = "UI_CLICK",
            actionId = actionId,
            channelId = channelId ?: DebugLogger.selectedChannelId.get(),
            component = screenName ?: DebugLogger.currentRoute.get(),
            context = mapOf("element" to elementName)
        )
    }
}
