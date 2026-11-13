package rsv.squitv.domain.usecase

import rsv.squitv.core.data.repository.UserRepository
import javax.inject.Inject
import timber.log.Timber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Saves playback progress locally with bounded disk writes. */
class SyncWatchProgressUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    private var lastLocalSaveTime = 0L
    private var lastLocalSaveStreamId = -1
    private var lastLocalSaveType: String? = null
    private var lastLocalSaveSeriesId: Int? = null
    private var lastLocalSavePosition = -1L
    private var lastLocalSaveDuration = -1L
    private var lastLocalSaveCompleted = false
    private val saves = Mutex()
    private val MIN_LOCAL_SAVE_INTERVAL_MS = 10_000L // 10 seconds threshold for disk IO

    /**
     * Updates watch progress.
     * 
     * @param streamId The ID of the stream.
     * @param type The type of content (VOD, SERIES).
     * @param position Current playback position in ms.
     * @param duration Total duration in ms.
     * @param seriesId Optional series ID if it's an episode.
     * @param forceSync If true, bypasses throttling for local saves.
     */
    suspend operator fun invoke(
        streamId: Int,
        type: String,
        position: Long,
        duration: Long,
        seriesId: Int?,
        forceSync: Boolean = false,
        isEndedEvent: Boolean = false,
        cloudScope: UserRepository.CloudPlaybackScope? = null,
        title: String? = null
    ) = saves.withLock {
        val timestamp = System.currentTimeMillis()
        val elapsed = android.os.SystemClock.elapsedRealtime()
        
        // Centralized completion check via WatchProgressUtils
        val isCompleted = WatchProgressUtils.isVideoCompleted(position, duration, isEndedEvent)
        val sameItem = streamId == lastLocalSaveStreamId && type == lastLocalSaveType && seriesId == lastLocalSaveSeriesId
        if (!forceSync && sameItem && (isCompleted && lastLocalSaveCompleted ||
                !isCompleted && !lastLocalSaveCompleted && position == lastLocalSavePosition && duration == lastLocalSaveDuration)) return@withLock
        Timber.tag("SquiTV_E2E").i("[SYNC_WATCH_PROGRESS] streamId=$streamId type=$type position=${position}ms duration=${duration}ms isCompleted=$isCompleted isEndedEvent=$isEndedEvent forceSync=$forceSync")

        // Local save with disk I/O throttling during continuous playback
        val shouldSaveLocal = forceSync || isCompleted || isEndedEvent ||
                              !sameItem ||
                              (elapsed - lastLocalSaveTime >= MIN_LOCAL_SAVE_INTERVAL_MS)

        if (shouldSaveLocal) {
            try {
                if (isCompleted) {
                    userRepository.deleteWatchProgress(streamId)
                } else if (position >= 5000L) {
                    userRepository.saveWatchProgress(streamId, type, position, duration, seriesId, timestamp)
                } else return@withLock
                try {
                    if (cloudScope != null) userRepository.recordNewCloudProgress(
                        streamId, type, position, duration, seriesId, timestamp, isCompleted, cloudScope, title,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Timber.w("Não foi possível preparar o progresso para sincronização.")
                }
                lastLocalSaveTime = elapsed
                lastLocalSaveStreamId = streamId
                lastLocalSaveType = type
                lastLocalSaveSeriesId = seriesId
                lastLocalSavePosition = position
                lastLocalSaveDuration = duration
                lastLocalSaveCompleted = isCompleted
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to save local watch progress")
            }
        }

    }
}
