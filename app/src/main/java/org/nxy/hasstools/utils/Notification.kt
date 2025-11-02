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

/**
 * 显示通知。
 *
 * @param context 应用上下文
 * @param id 通知 ID，默认为 1
 * @param title 通知标题，默认为 "通知"
 * @param text 通知内容
 * @param isOngoing 是否为常驻通知，默认为 false
 * @param importance 通知重要性级别，默认为 IMPORTANCE_NONE
 */
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

/**
 * 移除通知。
 *
 * @param context 应用上下文
 * @param id 要移除的通知 ID，默认为 1
 */
fun removeNotification(context: Context, id: Int = 1) {
    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.cancel(id)
}