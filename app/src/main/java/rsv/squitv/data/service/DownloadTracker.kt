package rsv.squitv.data.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import rsv.squitv.IptvApplication

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
@UnstableApi
class DownloadTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val TAG = "DownloadTracker"

    fun startDownload(mediaItem: MediaItem, displayName: String, type: String = "movie"): Boolean {
        val streamId = mediaItem.mediaId
        val mediaId = if (streamId.contains("_")) streamId else "${type}_$streamId"
        Log.d(TAG, "Starting download for: $displayName mediaId: $mediaId")
        
        val downloadRequest = DownloadRequest.Builder(mediaId, mediaItem.localConfiguration?.uri ?: Uri.EMPTY)
            .setData(displayName.toByteArray(Charsets.UTF_8))
            .build()
        
        return try {
            DownloadService.sendAddDownload(
                context,
                IptvDownloadService::class.java,
                downloadRequest,
                /* foreground= */ true
            )
            Log.d(TAG, "Download request sent to service successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download", e)
            false
        }
    }

    fun removeDownload(streamId: String, type: String = "movie") {
        val mediaId = if (streamId.contains("_")) streamId else "${type}_$streamId"
        Log.d(TAG, "Removing download: $mediaId")
        try {
            DownloadService.sendRemoveDownload(
                context,
                IptvDownloadService::class.java,
                mediaId,
                /* foreground= */ true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove download", e)
        }
    }
}
