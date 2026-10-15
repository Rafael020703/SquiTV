package rsv.squitv.domain.usecase

/**
 * Centralized domain utility for video watch progress and playback completion logic.
 */
object WatchProgressUtils {

    /**
     * Determines if a video has reached actual playback completion.
     * 
     * @param position Current playback position in ms.
     * @param duration Total video duration in ms.
     * @param isEndedEvent True if Media3/ExoPlayer emitted STATE_ENDED.
     */
    fun isVideoCompleted(position: Long, duration: Long, isEndedEvent: Boolean = false): Boolean {
        // PRIMARY CRITERION: Media3/ExoPlayer emitted STATE_ENDED naturally or reached end
        if (isEndedEvent) return true
        if (duration <= 0L) return false

        // Mark completed if position reached or exceeded the duration threshold (<= 2000ms remaining)
        val remainingMs = duration - position
        return remainingMs in 0L..2_000L
    }

    /**
     * Determines if a watch progress entry is valid for display in "Continuar Assistindo".
     */
    fun isPendingWatchProgress(position: Long, duration: Long): Boolean {
        if (position < 5000L) return false // Less than 5s watched is ignored
        return !isVideoCompleted(position, duration)
    }
}
