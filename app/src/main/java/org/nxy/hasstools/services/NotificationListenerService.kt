package org.nxy.hasstools.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.nxy.hasstools.data.KillPowerAlertDataStore

class NotificationListenerService : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        println("NotificationListenerService created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // 不健全的通知不管
        if (sbn == null || sbn.notification == null || sbn.notification.extras == null) {
            return
        }

        // 不是系统管家服务的通知不管
        val packageName = sbn.packageName
        if (
            packageName != "com.hihonor.systemmanager" &&
            packageName != "com.huawei.systemmanager"
        ) {
            return
        }

        // 检查开没开
        val killPowerAlertViewModel = KillPowerAlertDataStore().readData()
        if (!killPowerAlertViewModel.enabled) {
            return
        }

        val notification = sbn.notification
        val extras = notification.extras

        if (
            extras.getString("android.title") == "发现高耗电应用" &&
            extras.getString("android.bigText")?.contains(Regex("^“?(?:Home Assistant|位置上报|多个应用)”?")) == true
        ) {
            // 把通知删了
            println("Cancel notification: ${sbn.key}")
            cancelNotification(sbn.key)
            return
        }

        // 处理通知发布事件
        println(
            "Notification posted: packageName=${sbn.packageName}, channelId=${sbn.notification.channelId}, android.title=${
                sbn.notification.extras.getString(
                    "android.title"
                )
            }, android.text=${sbn.notification.extras.getString("android.text")}, android.subText=${
                sbn.notification.extras.getString(
                    "android.subText"
                )
            }, android.bigText=${sbn.notification.extras.getString("android.bigText")}, android.summaryText=${
                sbn.notification.extras.getString(
                    "android.summaryText"
                )
            }, android.infoText=${sbn.notification.extras.getString("android.infoText")}"
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 处理通知移除事件
        // println("Notification removed: $sbn")
    }
}
