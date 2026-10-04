package rsv.squitv.worker

import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import rsv.squitv.MainActivity
import rsv.squitv.R
import androidx.media3.common.util.UnstableApi

@UnstableApi
class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val programTitle = inputData.getString(KEY_PROGRAM_TITLE) ?: return Result.failure()
        val channelName = inputData.getString(KEY_CHANNEL_NAME) ?: "TV"
        val streamId = inputData.getInt(KEY_STREAM_ID, -1)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return ListenableWorker.Result.success()
            }
        }

        showNotification(programTitle, channelName, streamId)
        
        return Result.success()
    }

    @RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    private fun showNotification(programTitle: String, channelName: String, streamId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel(notificationManager)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("stream_id", streamId)
            putExtra("action", "play")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            streamId, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.reminder_prefix, programTitle))
            .setContentText(applicationContext.getString(R.string.reminder_notification_msg, channelName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(streamId, notification)
        }
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.epg_label),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = applicationContext.getString(R.string.epg_subtitle)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "epg_reminders_channel"
        const val KEY_PROGRAM_TITLE = "program_title"
        const val KEY_CHANNEL_NAME = "channel_name"
        const val KEY_STREAM_ID = "stream_id"
    }
}
