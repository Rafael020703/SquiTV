package rsv.squitv.core.debug

import kotlinx.serialization.Serializable

enum class DebugLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}

enum class DebugCategory {
    APP,
    LIFECYCLE,
    UI,
    NAVIGATION,
    INPUT,
    FOCUS,
    DPAD,
    CHANNEL,
    CHANNEL_SWITCH,
    PLAYER,
    PLAYER_STATE,
    PLAYER_ERROR,
    MEDIA,
    NETWORK,
    HTTP,
    API,
    AUTH,
    SESSION,
    STREAM,
    CACHE,
    DATABASE,
    PREFERENCES,
    COROUTINE,
    THREAD,
    SERVICE,
    TIMER,
    PERFORMANCE,
    MEMORY,
    EXCEPTION,
    UPDATE,
    DEBUG_SYSTEM,
    ANOMALY
}

@Serializable
data class DebugEvent(
    val timestamp: String,
    val relativeMs: Long,
    val sessionId: String,
    val appLaunchId: String,
    val actionId: String? = null,
    val operationId: String? = null,
    val channelSwitchId: String? = null,
    val playerInstanceId: Int? = null,
    val level: String,
    val category: String,
    val event: String,
    val thread: String,
    val component: String? = null,
    val className: String? = null,
    val methodName: String? = null,
    val context: Map<String, String>? = null,
    val channelId: String? = null,
    val oldState: String? = null,
    val newState: String? = null,
    val durationMs: Long? = null,
    val result: String? = null,
    val error: String? = null,
    val stackTrace: String? = null
)

@Serializable
data class DebugSnapshot(
    val timestamp: String,
    val sessionId: String,
    val appLaunchId: String,
    val currentActivity: String? = null,
    val currentRoute: String? = null,
    val selectedChannelId: String? = null,
    val activeChannelSwitchId: String? = null,
    val activePlayerState: String? = null,
    val playerInstanceId: Int? = null,
    val isPlaying: Boolean? = null,
    val playbackPositionMs: Long? = null,
    val bufferedPositionMs: Long? = null,
    val activeOperationsCount: Int = 0,
    val activeOperationsList: List<String> = emptyList(),
    val activeMediaStreamsCount: Int = 0,
    val memoryUsedMb: Long = 0L,
    val maxMemoryMb: Long = 0L,
    val freeMemoryMb: Long = 0L,
    val reason: String = "MANUAL_SNAPSHOT"
)

@Serializable
data class DebugMetadata(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val buildTimestamp: String,
    val deviceModel: String,
    val deviceManufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val sessionId: String,
    val appLaunchId: String
)
