package rsv.squitv.domain.usecase

import rsv.squitv.core.data.repository.UserRepository
import rsv.squitv.data.repository.FirebaseRepository
import rsv.squitv.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import timber.log.Timber
import kotlin.math.abs

/**
 * UseCase for syncing watch progress both locally (Room) and remotely (Firebase).
 * Encapsulates throttling logic to avoid excessive network calls.
 */
class SyncWatchProgressUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val firebaseRepository: FirebaseRepository,
    private val settingsRepository: SettingsRepository
) {
    private var lastFirebaseSyncTime = 0L
    private var lastFirebaseSyncPosition = -1L
    private var lastLocalSaveTime = 0L
    private var lastLocalSaveStreamId = -1
    private val MIN_SYNC_INTERVAL_MS = 60_000L // 1 minute threshold
    private val MIN_LOCAL_SAVE_INTERVAL_MS = 10_000L // 10 seconds threshold for disk IO

    /**
     * Updates watch progress.
     * 
     * @param streamId The ID of the stream.
     * @param type The type of content (VOD, SERIES).
     * @param position Current playback position in ms.
     * @param duration Total duration in ms.
     * @param seriesId Optional series ID if it's an episode.
     * @param forceSync If true, bypasses throttling for Firebase sync and local saves.
     */
    suspend operator fun invoke(
        streamId: Int,
        type: String,
        position: Long,
        duration: Long,
        seriesId: Int?,
        forceSync: Boolean = false,
        isEndedEvent: Boolean = false
    ) {
        val timestamp = System.currentTimeMillis()
        
        // Centralized completion check via WatchProgressUtils
        val isCompleted = WatchProgressUtils.isVideoCompleted(position, duration, isEndedEvent)
        Timber.tag("SquiTV_E2E").i("[SYNC_WATCH_PROGRESS] streamId=$streamId type=$type position=${position}ms duration=${duration}ms isCompleted=$isCompleted isEndedEvent=$isEndedEvent forceSync=$forceSync")

        // Local save with disk I/O throttling during continuous playback
        val shouldSaveLocal = forceSync || isCompleted || isEndedEvent ||
                              streamId != lastLocalSaveStreamId ||
                              (timestamp - lastLocalSaveTime >= MIN_LOCAL_SAVE_INTERVAL_MS)

        if (shouldSaveLocal) {
            lastLocalSaveTime = timestamp
            lastLocalSaveStreamId = streamId
            try {
                if (isCompleted) {
                    userRepository.deleteWatchProgress(streamId)
                } else if (position >= 5000L) {
                    userRepository.saveWatchProgress(streamId, type, position, duration, seriesId, timestamp)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save local watch progress")
            }
        }

        // Remote sync with throttling logic (Time threshold OR 5% progress jump)
        val shouldSyncFirebase = forceSync || 
                                 (timestamp - lastFirebaseSyncTime > MIN_SYNC_INTERVAL_MS) ||
                                 (abs(position - lastFirebaseSyncPosition) > duration / 20)

        if (shouldSyncFirebase && duration > 0) {
            lastFirebaseSyncTime = timestamp
            lastFirebaseSyncPosition = position
            
            try {
                val settings = settingsRepository.settingsFlow.first()
                val profileId = settings.activeProfileId
                val data = mapOf(
                    "position" to position,
                    "duration" to duration,
                    "type" to type,
                    "seriesId" to (seriesId ?: 0),
                    "timestamp" to timestamp
                )
                firebaseRepository.syncWatchProgress(profileId, streamId.toString(), data)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync watch progress to Firebase")
            }
        }
    }
}
