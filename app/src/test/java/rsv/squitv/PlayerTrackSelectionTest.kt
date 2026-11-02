@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package rsv.squitv

import android.app.Application
import androidx.media3.common.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.ui.content.player.PlayerTrackSelection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class PlayerTrackSelectionTest {
    private val audio = TrackGroup("audio", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC)
        .setLanguage("pt").build(), Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("en").build())
    private val text = TrackGroup("text", Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT)
        .setLanguage("pt").build())
    private val video = TrackGroup("video", Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build())
    private fun tracks(support: Int = C.FORMAT_HANDLED) = Tracks(listOf(
        Tracks.Group(video, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
        Tracks.Group(audio, false, intArrayOf(support, support), booleanArrayOf(true, false)),
        Tracks.Group(text, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))))

    @Test fun audioUsesIndexWithinItsTypeAndPreservesVideo() {
        val current = TrackSelectionParameters.DEFAULT.buildUpon()
            .setOverrideForType(TrackSelectionOverride(video, 0)).build()
        val selected = PlayerTrackSelection.select(tracks(), current, 0, 1, C.TRACK_TYPE_AUDIO)!!
        assertEquals(listOf(1), selected.overrides[audio]!!.trackIndices)
        assertTrue(selected.overrides.containsKey(video))
        assertEquals(listOf("en"), selected.preferredAudioLanguages)
    }

    @Test fun invalidIndicesAreIgnored() {
        for ((group, track) in listOf(-1 to 0, 8 to 0, 0 to -1, 0 to 3))
            assertNull(PlayerTrackSelection.select(tracks(), TrackSelectionParameters.DEFAULT,
                group, track, C.TRACK_TYPE_AUDIO))
    }

    @Test fun unsupportedTrackIsIgnored() {
        assertNull(PlayerTrackSelection.select(tracks(C.FORMAT_UNSUPPORTED_TYPE),
            TrackSelectionParameters.DEFAULT, 0, 0, C.TRACK_TYPE_AUDIO))
    }

    @Test fun selectingSubtitleEnablesIt() {
        val current = TrackSelectionParameters.DEFAULT.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        val selected = PlayerTrackSelection.select(tracks(), current, 0, 0, C.TRACK_TYPE_TEXT)!!
        assertFalse(C.TRACK_TYPE_TEXT in selected.disabledTrackTypes)
        assertEquals(listOf("pt"), selected.preferredTextLanguages)
        assertTrue(selected.overrides.containsKey(text))
    }

    @Test fun disablingSubtitleClearsItsOverrideAndLanguage() {
        val selected = PlayerTrackSelection.select(tracks(), TrackSelectionParameters.DEFAULT, 0, 0, C.TRACK_TYPE_TEXT)!!
        val cleared = PlayerTrackSelection.clear(selected, C.TRACK_TYPE_TEXT)
        assertTrue(C.TRACK_TYPE_TEXT in cleared.disabledTrackTypes)
        assertFalse(cleared.overrides.containsKey(text))
        assertTrue(cleared.preferredTextLanguages.isEmpty())
    }

    @Test fun automaticAudioClearsLanguageWithoutDisablingSound() {
        val selected = PlayerTrackSelection.select(tracks(), TrackSelectionParameters.DEFAULT, 0, 1, C.TRACK_TYPE_AUDIO)!!
        val cleared = PlayerTrackSelection.clear(selected, C.TRACK_TYPE_AUDIO)
        assertFalse(C.TRACK_TYPE_AUDIO in cleared.disabledTrackTypes)
        assertFalse(cleared.overrides.containsKey(audio))
        assertTrue(cleared.preferredAudioLanguages.isEmpty())
    }

    @Test fun automaticVideoKeepsAudioOverride() {
        val selected = PlayerTrackSelection.select(tracks(), TrackSelectionParameters.DEFAULT, 0, 1, C.TRACK_TYPE_AUDIO)!!
            .buildUpon().setOverrideForType(TrackSelectionOverride(video, 0)).build()
        val cleared = PlayerTrackSelection.clear(selected, C.TRACK_TYPE_VIDEO)
        assertFalse(cleared.overrides.containsKey(video))
        assertTrue(cleared.overrides.containsKey(audio))
    }
}
