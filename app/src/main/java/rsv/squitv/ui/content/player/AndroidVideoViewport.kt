package rsv.squitv.ui.content.player

import android.content.Context
import android.graphics.Color
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import rsv.squitv.shared.player.VideoScaleMode

/** Keep zoom inside its allotted viewport, including the four bounded Multi-view slots. */
@UnstableApi
internal class AndroidVideoViewport(context: Context) : FrameLayout(context) {
    private val video = PlayerView(context).apply { useController = false; setBackgroundColor(Color.BLACK) }
    init {
        clipChildren = true; clipToPadding = true; setBackgroundColor(Color.BLACK)
        addView(video, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    fun bind(player: Player?, mode: VideoScaleMode) {
        if (video.player !== player) video.player = player
        video.resizeMode = mode.media3Value
        keepScreenOn = player != null
    }
    fun releaseSurface() { video.player = null; keepScreenOn = false }
}
