package rsv.squitv.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

    fun showUpdateNotification(
        context: Context,
        updateInfo: AppUpdateInfo
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val updateChannelId = "app_updates"
        val updateChannelName = "Atualizações do Aplicativo"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(updateChannelId, updateChannelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações sobre novas versões do aplicativo"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra("navigate_to", "updates")
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.update_notification_title)
        val message = context.getString(R.string.update_notification_msg, updateInfo.versionName)

        val notification = NotificationCompat.Builder(context, updateChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1001, notification)
    }
}
