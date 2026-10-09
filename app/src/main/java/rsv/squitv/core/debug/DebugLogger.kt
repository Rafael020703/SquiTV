package rsv.squitv.core.debug

import android.content.Context
import android.os.Build
import android.util.Log
import rsv.squitv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object DebugLogger {
    private const val TAG = "SquiTV_Debug"
    private const val MAX_RING_BUFFER_SIZE = 500
    private const val MAX_LOG_FILES = 5
    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L // 10MB

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Volatile
    private var isInitialized = false

    val sessionId: String by lazy { DebugCorrelation.newSessionId() }
    val appLaunchId: String by lazy { DebugCorrelation.newAppLaunchId() }
    private val sessionStartTimeMs = System.currentTimeMillis()

    // State Tracking
    val currentActivity = AtomicReference<String?>(null)
    val currentRoute = AtomicReference<String?>(null)
    val selectedChannelId = AtomicReference<String?>(null)
    val activeChannelSwitchId = AtomicReference<String?>(null)
    val activePlayerInstanceId = AtomicInteger(0)
    val activePlayerState = AtomicReference<String?>("IDLE")
    val activeActionId = AtomicReference<String?>(null)

    data class ActiveOperation(
        val operationId: String,
        val name: String,
        val category: DebugCategory,
        val startTimeMs: Long,
        val timeoutMs: Long,
        val channelSwitchId: String? = null,
        val context: Map<String, String>? = null
    )

    private val activeOperations = ConcurrentHashMap<String, ActiveOperation>()
    private val ringBuffer = ConcurrentLinkedQueue<DebugEvent>()

    private var logFile: File? = null
    private var fileWriter: FileWriter? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (!BuildConfig.DEBUG || isInitialized) return

        try {
            val logDir = File(context.filesDir, "debug_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            cleanOldLogFiles(logDir)

            val fileName = "debug_session_${sessionId}.jsonl"
            logFile = File(logDir, fileName)
            fileWriter = FileWriter(logFile!!, true)

            isInitialized = true

            val metadata = DebugMetadata(
                applicationId = BuildConfig.APPLICATION_ID,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                buildType = BuildConfig.BUILD_TYPE,
                buildTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date()),
                deviceModel = Build.MODEL ?: "Unknown",
                deviceManufacturer = Build.MANUFACTURER ?: "Unknown",
                androidVersion = Build.VERSION.RELEASE ?: "Unknown",
                sdkInt = Build.VERSION.SDK_INT,
                sessionId = sessionId,
                appLaunchId = appLaunchId
            )

            log(
                level = DebugLevel.INFO,
                category = DebugCategory.APP,
                event = "APP_START",
                context = mapOf(
                    "metadata" to json.encodeToString(metadata)
                )
            )

            startWatchdogLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DebugLogger: ${e.message}", e)
        }
    }

    private fun cleanOldLogFiles(logDir: File) {
        try {
            val files = logDir.listFiles { _, name -> name.startsWith("debug_session_") && name.endsWith(".jsonl") }
                ?: return
            if (files.size >= MAX_LOG_FILES) {
                files.sortBy { it.lastModified() }
                val toDeleteCount = files.size - MAX_LOG_FILES + 1
                for (i in 0 until toDeleteCount) {
                    files[i].delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old log files: ${e.message}")
        }
    }

    private fun startWatchdogLoop() {
        scope.launch {
            while (isActive) {
                delay(2000L)
                checkOperationTimeouts()
            }
        }
    }

    private fun checkOperationTimeouts() {
        val now = System.currentTimeMillis()
        activeOperations.forEach { (opId, op) ->
            val elapsed = now - op.startTimeMs
            if (elapsed > op.timeoutMs) {
                activeOperations.remove(opId)
                log(
                    level = DebugLevel.WARN,
                    category = DebugCategory.TIMER,
                    event = "OPERATION_TIMEOUT",
                    operationId = opId,
                    channelSwitchId = op.channelSwitchId,
                    durationMs = elapsed,
                    context = mapOf(
                        "operationName" to op.name,
                        "opCategory" to op.category.name,
                        "timeoutMs" to op.timeoutMs.toString(),
                        "elapsedMs" to elapsed.toString()
                    ) + (op.context ?: emptyMap())
                )
            }
        }
    }

    fun startOperation(
        operationId: String,
        name: String,
        category: DebugCategory = DebugCategory.TIMER,
        timeoutMs: Long = 10_000L,
        channelSwitchId: String? = activeChannelSwitchId.get(),
        context: Map<String, String>? = null
    ) {
        if (!BuildConfig.DEBUG) return
        val now = System.currentTimeMillis()
        val op = ActiveOperation(
            operationId = operationId,
            name = name,
            category = category,
            startTimeMs = now,
            timeoutMs = timeoutMs,
            channelSwitchId = channelSwitchId,
            context = context
        )
        activeOperations[operationId] = op

        log(
            level = DebugLevel.TRACE,
            category = category,
            event = "${name}_START",
            operationId = operationId,
            channelSwitchId = channelSwitchId,
            context = context
        )
    }

    fun endOperation(
        operationId: String,
        result: String = "SUCCESS",
        error: String? = null,
        context: Map<String, String>? = null
    ) {
        if (!BuildConfig.DEBUG) return
        val op = activeOperations.remove(operationId)
        val now = System.currentTimeMillis()
        val elapsed = if (op != null) now - op.startTimeMs else null

        val name = op?.name ?: "OPERATION"
        val category = op?.category ?: DebugCategory.TIMER

        log(
            level = if (error != null) DebugLevel.ERROR else DebugLevel.TRACE,
            category = category,
            event = "${name}_END",
            operationId = operationId,
            channelSwitchId = op?.channelSwitchId ?: activeChannelSwitchId.get(),
            durationMs = elapsed,
            result = result,
            error = error,
            context = (op?.context ?: emptyMap()) + (context ?: emptyMap())
        )
    }

    fun log(
        level: DebugLevel,
        category: DebugCategory,
        event: String,
        actionId: String? = activeActionId.get(),
        operationId: String? = null,
        channelSwitchId: String? = activeChannelSwitchId.get(),
        playerInstanceId: Int? = activePlayerInstanceId.get().takeIf { it > 0 },
        component: String? = null,
        className: String? = null,
        methodName: String? = null,
        context: Map<String, String>? = null,
        channelId: String? = selectedChannelId.get(),
        oldState: String? = null,
        newState: String? = null,
        durationMs: Long? = null,
        result: String? = null,
        error: String? = null,
        stackTrace: String? = null
    ) {
        if (!BuildConfig.DEBUG) return

        val nowMs = System.currentTimeMillis()
        val relativeMs = nowMs - sessionStartTimeMs
        val timestampIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date(nowMs))
        val threadName = "${Thread.currentThread().name} [${Thread.currentThread().id}]"

        val sanitizedContext = context?.mapValues { DebugSanitizer.sanitizeText(it.value) }
        val sanitizedError = DebugSanitizer.sanitizeText(error)

        val debugEvent = DebugEvent(
            timestamp = timestampIso,
            relativeMs = relativeMs,
            sessionId = sessionId,
            appLaunchId = appLaunchId,
            actionId = actionId,
            operationId = operationId,
            channelSwitchId = channelSwitchId,
            playerInstanceId = playerInstanceId,
            level = level.name,
            category = category.name,
            event = event,
            thread = threadName,
            component = component ?: currentActivity.get() ?: currentRoute.get(),
            className = className,
            methodName = methodName,
            context = sanitizedContext,
            channelId = channelId,
            oldState = oldState,
            newState = newState,
            durationMs = durationMs,
            result = result,
            error = sanitizedError.ifBlank { null },
            stackTrace = stackTrace
        )

        // 1. Output to Logcat
        formatAndOutputLogcat(debugEvent)

        // 2. Add to Ring Buffer
        ringBuffer.add(debugEvent)
        while (ringBuffer.size > MAX_RING_BUFFER_SIZE) {
            ringBuffer.poll()
        }

        // 3. Write to File
        scope.launch {
            writeToFile(debugEvent)
        }
    }

    private fun formatAndOutputLogcat(e: DebugEvent) {
        val sb = StringBuilder()
        sb.append("[${e.category}]")
        sb.append("[${e.timestamp}]")
        sb.append("[session=${e.sessionId.takeLast(8)}]")
        if (e.channelSwitchId != null) sb.append("[cs=${e.channelSwitchId}]")
        if (e.operationId != null) sb.append("[op=${e.operationId}]")
        if (e.playerInstanceId != null) sb.append("[player=${e.playerInstanceId}]")
        sb.append("[thread=${e.thread}] ")
        sb.append("EVENT=${e.event}")

        if (e.channelId != null) sb.append(" channel=${e.channelId}")
        if (e.oldState != null || e.newState != null) sb.append(" state=${e.oldState}->${e.newState}")
        if (e.durationMs != null) sb.append(" duration=${e.durationMs}ms")
        if (e.result != null) sb.append(" result=${e.result}")
        if (e.error != null) sb.append(" error=${e.error}")
        if (e.context != null && e.context.isNotEmpty()) {
            sb.append(" ctx=").append(e.context.toString())
        }

        val formattedMessage = sb.toString()
        try {
            when (e.level) {
                "TRACE" -> Log.v(TAG, formattedMessage)
                "DEBUG" -> Log.d(TAG, formattedMessage)
                "INFO" -> Log.i(TAG, formattedMessage)
                "WARN" -> Log.w(TAG, formattedMessage)
                "ERROR" -> Log.e(TAG, formattedMessage)
                "FATAL" -> Log.e(TAG, "FATAL: $formattedMessage")
            }
            if (e.stackTrace != null) {
                Log.e(TAG, "StackTrace: ${e.stackTrace}")
            }
        } catch (_: Throwable) {
            // Android Log not mocked in local JVM unit tests
        }
    }

    @Synchronized
    private fun writeToFile(e: DebugEvent) {
        try {
            val writer = fileWriter ?: return
            val jsonLine = json.encodeToString(e) + "\n"
            writer.write(jsonLine)
            writer.flush()

            if (logFile != null && logFile!!.length() > MAX_FILE_SIZE_BYTES) {
                writer.close()
                fileWriter = null
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error writing debug log to file: ${ex.message}")
        }
    }

    fun takeSnapshot(reason: String = "MANUAL_SNAPSHOT"): DebugSnapshot {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usedMemory = totalMemory - freeMemory

        val snapshot = DebugSnapshot(
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date()),
            sessionId = sessionId,
            appLaunchId = appLaunchId,
            currentActivity = currentActivity.get(),
            currentRoute = currentRoute.get(),
            selectedChannelId = selectedChannelId.get(),
            activeChannelSwitchId = activeChannelSwitchId.get(),
            activePlayerState = activePlayerState.get(),
            playerInstanceId = activePlayerInstanceId.get().takeIf { it > 0 },
            activeOperationsCount = activeOperations.size,
            activeOperationsList = activeOperations.values.map { "${it.name}(${it.operationId})" },
            memoryUsedMb = usedMemory / (1024 * 1024),
            maxMemoryMb = maxMemory / (1024 * 1024),
            freeMemoryMb = freeMemory / (1024 * 1024),
            reason = reason
        )

        log(
            level = DebugLevel.INFO,
            category = DebugCategory.PERFORMANCE,
            event = "SNAPSHOT_TAKEN",
            context = mapOf(
                "reason" to reason,
                "memoryUsedMb" to snapshot.memoryUsedMb.toString(),
                "maxMemoryMb" to snapshot.maxMemoryMb.toString(),
                "activeOpsCount" to snapshot.activeOperationsCount.toString(),
                "activeOps" to snapshot.activeOperationsList.joinToString(",")
            )
        )

        return snapshot
    }

    fun getRecentEvents(): List<DebugEvent> {
        return ringBuffer.toList()
    }

    fun getLogFile(): File? {
        return logFile
    }

    fun clearBuffer() {
        ringBuffer.clear()
    }
}
