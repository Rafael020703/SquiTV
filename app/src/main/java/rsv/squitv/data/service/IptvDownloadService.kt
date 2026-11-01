package rsv.squitv.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import rsv.squitv.IptvApplication
import rsv.squitv.R
import timber.log.Timber

@UnstableApi
class IptvDownloadService : DownloadService(
    1, // JOB_ID
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    "iptv_downloads",
    R.string.download_channel_name,
    R.string.download_channel_description
) {

    private lateinit var notificationHelper: DownloadNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = DownloadNotificationHelper(this, "iptv_downloads")
    }

    override fun getDownloadManager(): DownloadManager {
        return (application as IptvApplication).downloadManager
    }

    override fun getScheduler(): Scheduler? {
        return PlatformScheduler(this, 1)
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val activeDownloads = downloads.filter { 
            it.state != Download.STATE_COMPLETED && it.state != Download.STATE_FAILED 
        }
        
        val title = if (activeDownloads.size == 1) {
            val download = activeDownloads[0]
            try {
                if (download.request.data.isNotEmpty()) {
                    String(download.request.data, Charsets.UTF_8)
                } else {
                    download.request.id
                }
            } catch (e: Exception) {
                download.request.id
            }
        } else {
            "Baixando ${activeDownloads.size} itens"
        }

        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_notification,
            null,
            title,
            downloads,
            notMetRequirements
        )
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "iptv_downloads",
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
