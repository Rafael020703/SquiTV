package rsv.squitv.core.debug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

object DebugCorrelation {
    private val counter = AtomicLong(1)

    fun newSessionId(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = dateFormat.format(Date())
        val randomHex = UUID.randomUUID().toString().replace("-", "").take(4).uppercase(Locale.US)
        return "DEBUG_SESSION_${timestamp}_${randomHex}"
    }

    fun newAppLaunchId(): String {
        return "LAUNCH_${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}"
    }

    fun newActionId(actionName: String = "ACT"): String {
        val num = counter.getAndIncrement()
        val randomHex = UUID.randomUUID().toString().take(4).uppercase(Locale.US)
        return "${actionName.uppercase(Locale.US)}_${num}_${randomHex}"
    }

    fun newOperationId(opName: String = "OP"): String {
        val num = counter.getAndIncrement()
        val randomHex = UUID.randomUUID().toString().take(4).uppercase(Locale.US)
        return "${opName.uppercase(Locale.US)}_${num}_${randomHex}"
    }

    fun newChannelSwitchId(targetChannelId: Any): String {
        val num = counter.getAndIncrement()
        val randomHex = UUID.randomUUID().toString().take(4).uppercase(Locale.US)
        return "CS_${targetChannelId}_${num}_${randomHex}"
    }
}
