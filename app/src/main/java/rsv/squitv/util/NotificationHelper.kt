package rsv.squitv.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import timber.log.Timber
import androidx.core.app.NotificationCompat
import rsv.squitv.R
import rsv.squitv.domain.model.AppUpdateInfo

object NotificationHelper {
    private const val CHANNEL_ID = "content_updates"
    private const val CHANNEL_NAME = "Atualizações de Conteúdo"

    fun showContentUpdateNotification(
        context: Context, 
        newMovies: List<String> = emptyList(), 
        newSeries: List<String> = emptyList(),
        isDetailed: Boolean = true
    ) {
        if (!canNotify(context)) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val title = if (newMovies.size + newSeries.size == 1) "Nova Adição ao Catálogo" else "Novidades no Catálogo"
        
        val message = if (isDetailed) {
            val items = (newMovies + newSeries).take(5)
            val suffix = if (newMovies.size + newSeries.size > 5) "... e mais" else ""
            items.joinToString(", ") + suffix
        } else {
            buildString {
                if (newMovies.isNotEmpty()) append("${newMovies.size} novos filmes")
                if (newMovies.isNotEmpty() && newSeries.isNotEmpty()) append(" e ")
                if (newSeries.isNotEmpty()) append("${newSeries.size} novas séries")
                append(" adicionados!")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showPushNotification(context: Context, title: String, message: String, streamId: Int? = null, contentType: String? = null) {
        if (!canNotify(context)) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pushChannelId = "content_alerts"
        val pushChannelName = "Alertas de Estreias e Canais"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(pushChannelId, pushChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notificações sobre novos episódios e eventos ao vivo"
                enableLights(true)
                lightColor = android.graphics.Color.CYAN
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Deep link intent could be added here if MainViewModel supports navigating via intent
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, pushChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun showUpdateNotification(context: Context, updateInfo: AppUpdateInfo): Boolean {
        if (!canNotify(context)) return false
        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "app_updates"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(NotificationChannel(channelId,
                    context.getString(R.string.app_updates_title), NotificationManager.IMPORTANCE_DEFAULT))
                if (manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE) return false
            }
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                putExtra("navigate_to", "updates")
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } ?: return false
            val pending = PendingIntent.getActivity(context, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val message = context.getString(R.string.update_notification_msg, updateInfo.versionName)
            manager.notify(1001, NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .addAction(0, context.getString(R.string.updater_view), pending)
                .build())
            true
        } catch (e: SecurityException) {
            Timber.w(e, "Permissão de notificações indisponível")
            false
        }
    }
}
