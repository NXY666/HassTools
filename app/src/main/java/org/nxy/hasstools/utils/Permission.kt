package org.nxy.hasstools.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import org.nxy.hasstools.services.NotificationListenerService

private fun checkBatteryOptimization(context: Context): Boolean {
    val packageName = context.packageName
    val pm = context.getSystemService(PowerManager::class.java)
    return pm.isIgnoringBatteryOptimizations(packageName)
}

@SuppressLint("BatteryLife")
private fun requestBatteryOptimization(context: Context) {
    val packageName = context.packageName
    val intent = Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:$packageName".toUri()
    }
    context.startActivity(intent)
}

fun checkExactAlarmPermission(context: Context): Boolean {
    val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    return alarmManager.canScheduleExactAlarms()
}

fun requestExactAlarmPermission(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
    context.startActivity(intent)
}

private fun checkNotificationListenerPermission(activity: Activity): Boolean {
    // 检查是否已获得通知访问权限
    val enabledListeners = Settings.Secure.getString(
        activity.contentResolver,
        "enabled_notification_listeners"
    )

    // 如果已获得权限，返回 true
    return enabledListeners.contains(activity.packageName)
}

private fun requestNotificationListenerPermission(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
    intent.putExtra(
        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
        ComponentName(
            context.packageName,
            NotificationListenerService::class.java.name
        ).flattenToString()
    )
    context.startActivity(intent)
}

class PermissionItem(
    val permissionLabel: String,
    val permissionDesc: String = "",
    val permissionCheckFunction: (Context) -> Boolean,
    val permissionRequestFunction: (Context) -> Unit,
    val permissionRevokedFunction: (Context) -> Unit = { context ->
        Toast.makeText(context, "此权限不支持自动撤销授权", Toast.LENGTH_SHORT).show()
    },
) {
    constructor(
        permissionLabel: String,
        permissionDesc: String = "",
        permissionName: String
    ) : this(
        permissionLabel = permissionLabel,
        permissionDesc = permissionDesc,
        permissionCheckFunction = { context ->
            ActivityCompat.checkSelfPermission(
                context,
                permissionName
            ) == PackageManager.PERMISSION_GRANTED
        },
        permissionRequestFunction = { context ->
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(permissionName),
                0
            )
        },
        permissionRevokedFunction = { context -> context.revokeSelfPermissionOnKill(permissionName) }
    )
}

val locationPermissionGroup = listOf(
    PermissionItem(
        permissionLabel = "精确位置",
        permissionName = Manifest.permission.ACCESS_FINE_LOCATION
    ),
    PermissionItem(
        permissionLabel = "后台位置",
        permissionDesc = "授权时，请在弹出的设置页面中选择“始终允许”。",
        permissionName = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ),
    PermissionItem(
        permissionLabel = "发送通知",
        permissionDesc = "注册前台服务可以提升进程优先级，降低应用被系统优化的可能性。",
        permissionName = Manifest.permission.POST_NOTIFICATIONS
    ),
    PermissionItem(
        permissionLabel = "电池优化",
        permissionDesc = "允许应用在后台运行。",
        permissionCheckFunction = {
            checkBatteryOptimization(context = it)
        },
        permissionRequestFunction = {
            requestBatteryOptimization(context = it)
        }
    ),
    PermissionItem(
        permissionLabel = "精准闹钟",
        permissionDesc = "用于创建精确的定时任务。",
        permissionCheckFunction = {
            checkExactAlarmPermission(context = it)
        },
        permissionRequestFunction = {
            requestExactAlarmPermission(context = it)
        }
    )
)

val stepPushPermissionGroup = listOf(
    PermissionItem(
        permissionLabel = "运动健康",
        permissionDesc = "用于获取步数传感器的实时数据。",
        permissionName = Manifest.permission.ACTIVITY_RECOGNITION
    ),
    PermissionItem(
        permissionLabel = "步数（写入）",
        permissionDesc = "用于将步数数据推送到 Health Connect。",
        permissionName = HealthPermission.getWritePermission(StepsRecord::class)
    ),
    PermissionItem(
        permissionLabel = "电池优化",
        permissionDesc = "允许应用在后台运行。",
        permissionCheckFunction = {
            checkBatteryOptimization(context = it)
        },
        permissionRequestFunction = {
            requestBatteryOptimization(context = it)
        }
    ),
    PermissionItem(
        permissionLabel = "精准闹钟",
        permissionDesc = "用于创建精确的定时任务。",
        permissionCheckFunction = {
            checkExactAlarmPermission(context = it)
        },
        permissionRequestFunction = {
            requestExactAlarmPermission(context = it)
        }
    )
)

val wifiGeofencePermissionGroup = listOf(
    PermissionItem(
        permissionLabel = "精确位置",
        permissionDesc = "扫描附近的 Wi-Fi 设备以进行辅助定位。",
        permissionName = Manifest.permission.ACCESS_FINE_LOCATION
    )
)

val killPowerAlertPermissionGroup = listOf(
    PermissionItem(
        permissionLabel = "通知使用权",
        permissionDesc = "用于监听并自动移除与 Home Assistant 应用和位置上报相关的高耗电通知。",
        permissionCheckFunction = {
            checkNotificationListenerPermission(it as Activity)
        },
        permissionRequestFunction = {
            requestNotificationListenerPermission(it)
        }
    )
)

fun checkGroupPermissions(context: Context, permissionGroup: List<PermissionItem>): Boolean {
    return permissionGroup.all { it.permissionCheckFunction(context) }
}
