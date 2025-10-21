package org.nxy.hasstools.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.nxy.hasstools.R

fun showNotification(
    context: Context,
    id: Int = 1,
    title: String? = null,
    text: String,
    isOngoing: Boolean = false,
    importance: Int = NotificationManager.IMPORTANCE_NONE
) {
    val channelId = "channelId"

    val notificationManager = NotificationManagerCompat.from(context)

    val channel = NotificationChannel(
        channelId,
        "普通通知",
        importance
    )

    notificationManager.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, channelId)
        .setContentTitle(title ?: "通知")
        .setContentText(text)
        .setOngoing(isOngoing)
        .setSilent(true)
        .setSmallIcon(R.drawable.ic_info)
        .setColor(ContextCompat.getColor(context, R.color.md_theme_primaryContainer))
        .build()

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    notificationManager.notify(id, notification)
}

fun removeNotification(context: Context, id: Int = 1) {
    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.cancel(id)
}