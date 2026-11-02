@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package rsv.squitv.ui.content.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks

/** Selection rules shared by the player actions and their regression tests. */
internal object PlayerTrackSelection {
    fun select(
        tracks: Tracks, current: TrackSelectionParameters,
        groupIndex: Int, trackIndex: Int, type: Int
    ): TrackSelectionParameters? {
        val group = tracks.groups.filter { it.type == type }.getOrNull(groupIndex) ?: return null
        if (trackIndex !in 0 until group.length || !group.isTrackSupported(trackIndex)) return null
        val language = group.getTrackFormat(trackIndex).language
        val builder = current.buildUpon().setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
        when (type) {
            C.TRACK_TYPE_AUDIO -> builder.setPreferredAudioLanguage(language)
            C.TRACK_TYPE_TEXT -> builder.setPreferredTextLanguage(language)
        }
        return builder.build()
    }

    fun clear(current: TrackSelectionParameters, type: Int): TrackSelectionParameters {
        val builder = current.buildUpon().clearOverridesOfType(type)
            .setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT)
        when (type) {
            C.TRACK_TYPE_AUDIO -> builder.setPreferredAudioLanguage(null)
            C.TRACK_TYPE_TEXT -> builder.setPreferredTextLanguage(null)
        }
        return builder.build()
    }
}
