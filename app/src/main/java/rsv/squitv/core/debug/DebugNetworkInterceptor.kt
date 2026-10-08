package rsv.squitv.core.debug

import rsv.squitv.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class DebugNetworkInterceptor(
    private val isPlayerStream: Boolean = false
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!BuildConfig.DEBUG) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val rawUrl = request.url.toString()
        val sanitizedUrl = DebugSanitizer.sanitizeUrl(rawUrl)
        val path = request.url.encodedPath
        val host = request.url.host
        val method = request.method

        val category = if (isPlayerStream) DebugCategory.STREAM else DebugCategory.HTTP
        val opId = DebugTrace.startTrace(
            name = if (isPlayerStream) "STREAM_HTTP_REQUEST" else "API_HTTP_REQUEST",
            category = category,
            timeoutMs = if (isPlayerStream) 15_000L else 30_000L,
            context = mapOf(
                "method" to method,
                "host" to host,
                "path" to path,
                "url" to sanitizedUrl
            )
        )

        val startTime = System.currentTimeMillis()

        DebugLogger.log(
            level = DebugLevel.DEBUG,
            category = category,
            event = "NETWORK_REQUEST_START",
            operationId = opId,
            context = mapOf(
                "method" to method,
                "host" to host,
                "path" to path,
                "url" to sanitizedUrl
            )
        )

        val response: Response
        try {
            response = chain.proceed(request)
            val durationMs = System.currentTimeMillis() - startTime
            val statusCode = response.code
            val contentLength = response.body.contentLength()

            val level = if (response.isSuccessful) DebugLevel.DEBUG else DebugLevel.WARN

            DebugLogger.log(
                level = level,
                category = category,
                event = "NETWORK_REQUEST_END",
                operationId = opId,
                durationMs = durationMs,
                context = mapOf(
                    "statusCode" to statusCode.toString(),
                    "contentLength" to contentLength.toString(),
                    "method" to method,
                    "host" to host,
                    "path" to path,
                    "url" to sanitizedUrl
                )
            )

            DebugTrace.endTrace(
                operationId = opId,
                result = if (response.isSuccessful) "HTTP_$statusCode" else "HTTP_ERROR_$statusCode",
                context = mapOf("statusCode" to statusCode.toString())
            )

            return response
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime
            val errorMsg = "${e.javaClass.simpleName}: ${e.message}"

            DebugLogger.log(
                level = DebugLevel.ERROR,
                category = category,
                event = "NETWORK_REQUEST_ERROR",
                operationId = opId,
                durationMs = durationMs,
                error = errorMsg,
                context = mapOf(
                    "method" to method,
                    "host" to host,
                    "path" to path,
                    "url" to sanitizedUrl
                )
            )

            DebugTrace.endTrace(
                operationId = opId,
                result = "NETWORK_FAILED",
                error = errorMsg
            )

            throw e
        }
    }
}
