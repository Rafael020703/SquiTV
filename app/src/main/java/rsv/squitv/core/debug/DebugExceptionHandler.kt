package rsv.squitv.core.debug

import android.util.Log
import rsv.squitv.BuildConfig

class DebugExceptionHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            try {
                val threadName = "${thread.name} [${thread.id}]"
                val stackTrace = Log.getStackTraceString(throwable)

                DebugLogger.takeSnapshot("CRASH_UNCAUGHT_EXCEPTION")

                DebugLogger.log(
                    level = DebugLevel.FATAL,
                    category = DebugCategory.EXCEPTION,
                    event = "UNCAUGHT_EXCEPTION",
                    error = "${throwable.javaClass.simpleName}: ${throwable.message}",
                    stackTrace = stackTrace,
                    context = mapOf(
                        "thread" to threadName,
                        "exceptionClass" to throwable.javaClass.name
                    )
                )
            } catch (e: Exception) {
                Log.e("SquiTV_Debug", "Error logging uncaught exception: ${e.message}")
            }
        }

        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        fun install() {
            if (!BuildConfig.DEBUG) return
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler !is DebugExceptionHandler) {
                Thread.setDefaultUncaughtExceptionHandler(DebugExceptionHandler(currentHandler))
            }
        }
    }
}
