package rsv.squitv.core.debug

import rsv.squitv.BuildConfig

object DebugTrace {
    fun startTrace(
        name: String,
        category: DebugCategory = DebugCategory.PERFORMANCE,
        timeoutMs: Long = 10_000L,
        context: Map<String, String>? = null
    ): String {
        if (!BuildConfig.DEBUG) return ""
        val opId = DebugCorrelation.newOperationId(name)
        DebugLogger.startOperation(
            operationId = opId,
            name = name,
            category = category,
            timeoutMs = timeoutMs,
            context = context
        )
        return opId
    }

    fun endTrace(
        operationId: String,
        result: String = "SUCCESS",
        error: String? = null,
        context: Map<String, String>? = null
    ) {
        if (!BuildConfig.DEBUG || operationId.isBlank()) return
        DebugLogger.endOperation(
            operationId = operationId,
            result = result,
            error = error,
            context = context
        )
    }

    inline fun <T> trace(
        name: String,
        category: DebugCategory = DebugCategory.PERFORMANCE,
        timeoutMs: Long = 10_000L,
        context: Map<String, String>? = null,
        block: () -> T
    ): T {
        if (!BuildConfig.DEBUG) {
            return block()
        }
        val opId = startTrace(name, category, timeoutMs, context)
        try {
            val result = block()
            endTrace(opId, result = "SUCCESS")
            return result
        } catch (e: Exception) {
            endTrace(opId, result = "FAILED", error = "${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}
