package org.nxy.hasstools.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import okio.withLock
import org.nxy.hasstools.R
import org.nxy.hasstools.data.LocationDataStore
import org.nxy.hasstools.data.UserDataStore
import org.nxy.hasstools.objects.UserConfig
import org.nxy.hasstools.utils.HassClient
import org.nxy.hasstools.utils.LocationHandler
import org.nxy.hasstools.utils.checkGroupPermissions
import org.nxy.hasstools.utils.getCurrentLocation
import org.nxy.hasstools.utils.locationPermissionGroup
import org.nxy.hasstools.utils.showNotification
import java.util.concurrent.locks.ReentrantLock

const val LOCATION_WORKING_STATUS_ACTION = "WORK_STATUS"

object LocationWorkerStatus {
    const val STOPPED = "stopped"
    const val STARTING = "starting"
    const val RUNNING = "running"
    const val STOPPING = "stopping"
    const val UNKNOWN = "unknown"

    private var value = STOPPED

    fun get(): String {
        return value
    }

    fun set(context: Context, status: String) {
        value = status
        context.sendBroadcast(Intent(LOCATION_WORKING_STATUS_ACTION).apply {
            `package` = context.packageName
            putExtra("status", status)
        })
    }
}

class UserClient(val userId: String, val userName: String, val hassClient: HassClient) {
    var locationId: String? = null
    var locationName: String? = null
}

object LocationWorker {
    val lock = ReentrantLock()

    inline fun <T> withLock(action: () -> T): T = lock.withLock { return action() }

    var userClients: List<UserClient> = listOf()
}

object LocationAlarmScheduler {
    private lateinit var nextPlannedAlarmPendingIntent: PendingIntent

    fun scheduleExactAlarm(context: Context, action: String, elapsedMillis: Long): PendingIntent? {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        val intent = Intent(context, LocationAlarmReceiver::class.java)
        intent.action = action

        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedMillis, pendingIntent
            )

            return pendingIntent
        } catch (e: SecurityException) {
            e.printStackTrace()
            return null
        }
    }

    fun schedulePlannedExactAlarm(context: Context, elapsedMillis: Long): Boolean {
        val pendingIntent = scheduleExactAlarm(context, LocationAlarmReceiver.GET_LOCATION_PLANNED, elapsedMillis)
        if (pendingIntent != null) {
            nextPlannedAlarmPendingIntent = pendingIntent
            return true
        } else {
            return false
        }
    }

    fun cancelAlarm(context: Context, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent)
    }

    fun cancelPlannedAlarm(context: Context) {
        cancelAlarm(context, nextPlannedAlarmPendingIntent)
    }
}

fun startLocationWork(context: Context, userConfig: UserConfig): Boolean {
    LocationWorker.withLock {
        if (LocationWorkerStatus.get() != LocationWorkerStatus.STOPPED) {
            println("在非停止状态下启动工作")
            showNotification(context, 501, title = "启动失败", text = "工作已经在运行或正在启动，请稍后再试。")
            return true
        }

        // 权限检查
        if (!checkGroupPermissions(context, locationPermissionGroup)) {
            println("权限检查失败，跳过")
            return false
        }

        println("工作已启动")

        LocationWorkerStatus.set(context, LocationWorkerStatus.STARTING)

        LocationWorker.userClients = userConfig.items.filter { it.enabled }.map {
            UserClient(it.userId, it.userName, HassClient(baseUrl = it.url, webhookId = it.webhookId))
        }

        if (!LocationAlarmScheduler.schedulePlannedExactAlarm(context, SystemClock.elapsedRealtime())) {
            showNotification(context, 502, title = "启动失败", text = "定时失败，请检查应用权限后重试。")
            LocationWorkerStatus.set(context, LocationWorkerStatus.STOPPED)
            return false
        }

        // 启动前台服务
        startKeepAliveService(context)

        return true
    }
}

fun stopLocationWork(context: Context, reason: String? = null) {
    LocationWorker.withLock {
        if (LocationWorkerStatus.get() != LocationWorkerStatus.RUNNING) {
            Log.w("LocationWorker", "在非运行状态下停止工作")
            return
        }

        LocationWorkerStatus.set(context, LocationWorkerStatus.STOPPING)

        LocationAlarmScheduler.cancelPlannedAlarm(context)

        // 停止前台服务
        stopKeepAliveService(context)

        if (reason != null) {
            showNotification(context, 503, title = "异常退出", text = reason)
        }

        println("工作已停止")
    }
}

class LocationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        println("LocationBootReceiver received: $intent")

        // 检查广播类型
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                println("开机启动")

                val locationConfig = LocationDataStore().readData()

                // 检查是否启用了定位服务
                if (!locationConfig.enabled) {
                    println("定位服务已禁用，跳过自动启动")
                    return
                }

                val userConfig = UserDataStore().readData()

                // 进io线程
                CoroutineScope(IO).launch {
                    // 启动服务
                    startLocationWork(context, userConfig)
                }

                println("服务已启动")
            }
        }
    }
}

class LocationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        println("LocationAlarmReceiver received: $intent")

        when (intent.action) {
            GET_LOCATION_MANUALLY -> {
                println("手动定位")

                // 启动手动定位服务
                GetLocationManuallyForegroundService.run(context)
            }

            GET_LOCATION_PLANNED -> {
                println("计划定位")

                // 启动计划定位服务
                GetLocationPlannedForegroundService.run(context)
            }

            else -> {
                println("未知广播")
            }
        }
    }

    companion object {
        const val GET_LOCATION_MANUALLY = "GET_LOCATION_MANUALLY"

        const val GET_LOCATION_PLANNED = "GET_LOCATION_PLANNED"
    }
}

class GetLocationManuallyForegroundService : Service() {
    private val notificationId = 3

    private var _error = ""

    private fun getError(): String {
        return _error
    }

    private fun setError(error: String) {
        println("Manually定位错误：$error")
        this._error = error
    }

    private fun setFatalError(error: String) {
        setError(error)
        stopSelf()
    }

    private fun hasError(): Boolean {
        return _error.isNotEmpty()
    }

    override fun onCreate() {
        super.onCreate()
        println("GetLocationManuallyForegroundService created")

        // 创建前台通知
        val notification = createNotification()

        startForeground(notificationId, notification, FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        println("GetLocationManuallyForegroundService onStartCommand: $intent")

        val that = this

        CoroutineScope(IO).launch updateLocation@{
            // 获取位置配置
            val locationConfig = LocationDataStore().readDataSync()

            // 获取当前位置
            val newLocation = getCurrentLocation(that, locationConfig.fusedProvider, false)

            // 如果获取位置失败，停止服务
            if (newLocation == null) {
                setFatalError("定位失败。")
                return@updateLocation
            }

            println("手动确定位置：${newLocation.latitude}, ${newLocation.longitude}")

            LocationWorker.userClients.forEach { userClient ->
                val hassClient = userClient.hassClient

                val zones = LocationHandler.getZones(hassClient)

                val newLocationName = LocationHandler.getLocationName(userClient.locationName, newLocation, zones)

                // 离开保护
                if (!LocationHandler.isSticky(
                        application,
                        userClient.userId,
                        zones,
                        userClient.locationName,
                        newLocationName
                    )
                ) {
                    println("位置名更新：${userClient.locationName} -> $newLocationName")
                    userClient.locationId = zones.find { it.name == newLocationName || (it.id == "zone.home" && newLocationName == "home") }?.id
                    userClient.locationName = newLocationName
                } else {
                    println("离开保护生效：${userClient.locationName} -x-> $newLocationName")
                }

                if (!LocationHandler.postLocation(hassClient, newLocation, userClient.locationName)) {
                    setError("${userClient.userName} 的位置更新失败。")
                }
            }

            stopSelf()
        }

        return START_NOT_STICKY // 服务停止后不再重启
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        println("onDestroy")

        if (hasError()) {
            showNotification(this, 512, title = "手动定位错误", text = getError())
        }
    }

    // 创建前台通知
    private fun createNotification(): Notification {
        val channelId = "get_location_manually_service_channel"
        val channelName = "手动定位服务"
        val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_MIN)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(notificationChannel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在定位")
            .setContentText("服务正在运行，请稍候……")
            .setSmallIcon(R.drawable.ic_my_location)
            .setColor(ContextCompat.getColor(this, R.color.md_theme_primaryContainer))
            .build()
    }

    companion object {
        /**
         * 运行定位服务
         * @param context 上下文
         * @return 错误信息
         */
        fun run(context: Context) {
            val serviceIntent = Intent(context, GetLocationManuallyForegroundService::class.java)
            context.startService(serviceIntent)
        }
    }
}

class GetLocationPlannedForegroundService : Service() {
    private val notificationId = 2

    private var _error = ""

    private fun getError(): String {
        return _error
    }

    private fun setError(error: String) {
        println("Planned定位错误：$error")
        this._error += error
    }

    private fun setFatalError(error: String) {
        setError(error)
        stopSelf()
    }

    private fun hasError(): Boolean {
        return _error.isNotEmpty()
    }

    override fun onCreate() {
        super.onCreate()
        println("GetLocationPlannedForegroundService created")

        // 创建前台通知
        val notification = createNotification()

        startForeground(notificationId, notification, FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        println("GetLocationPlannedForegroundService onStartCommand: $intent")

        val that = this

        CoroutineScope(IO).launch updateLocation@{
            // 获取位置配置
            val locationConfig = LocationDataStore().readData()

            // 获取当前位置
            val newLocation = getCurrentLocation(that, locationConfig.fusedProvider, false)

            // 如果获取位置失败，停止服务
            if (newLocation == null) {
                setFatalError("定位失败。")
                return@updateLocation
            }

            if (LocationHandler.hasLocationChange(newLocation) || LocationHandler.getLastLocation() == null) {
                println(
                    "位置发生变化：${LocationHandler.getCurrLocation()?.latitude} -> ${newLocation.latitude}, ${LocationHandler.getCurrLocation()?.longitude} -> ${newLocation.longitude}"
                )

                // 更新位置
                LocationHandler.updateLocation(newLocation)

                LocationWorker.userClients.forEach { userClient ->
                    println("开始上报：${userClient.userName}")

                    val hassClient = userClient.hassClient

                    val zones = LocationHandler.getZones(hassClient)

                    val newLocationName = LocationHandler.getLocationName(userClient.locationName, newLocation, zones)

                    // 离开保护
                    if (!LocationHandler.isSticky(
                            application,
                            userClient.userId,
                            zones,
                            userClient.locationName,
                            newLocationName
                        )
                    ) {
                        println("位置名更新：${userClient.locationName} -> $newLocationName")
                        userClient.locationId = zones.find { it.name == newLocationName || (it.id == "zone.home" && newLocationName == "home") }?.id
                        userClient.locationName = newLocationName
                    } else {
                        println("离开保护生效：${userClient.locationName} -x->$newLocationName")
                    }

                    if (!LocationHandler.postLocation(hassClient, newLocation, userClient.locationName)) {
                        setError("${userClient.userName} 的位置更新失败。")
                    }
                }
            } else {
                println(
                    "位置未发生变化：${newLocation.latitude} == ${LocationHandler.getCurrLocation()?.latitude}, ${newLocation.longitude} == ${LocationHandler.getCurrLocation()?.longitude}"
                )

                LocationHandler.ignoreLocation()
            }

            stopSelf()
        }

        return START_NOT_STICKY // 服务停止后不再重启
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        println("onDestroy")

        val currElapsedTime = SystemClock.elapsedRealtime()

        if (hasError()) {
            showNotification(this, 511, title = "计划定位错误", text = getError())
        }

        // 设置下一次闹钟（手动定位不影响计划时间）
        if (LocationWorkerStatus.get() == LocationWorkerStatus.RUNNING) {
            val nextScheduledElapsedTime = if (hasError()) {
                currElapsedTime + 60 * 1000
            } else {
                LocationHandler.getNextScheduledElapsedTime()
            }

            // 设置下一次闹钟
            if (!LocationAlarmScheduler.schedulePlannedExactAlarm(applicationContext, nextScheduledElapsedTime)) {
                stopLocationWork(applicationContext, "定时失败，请检查应用权限后重试。")
            }
        }

        // 锁，然后停止服务
        runningLock.withLock {
            isRunning = false
        }
    }

    // 创建前台通知
    private fun createNotification(): Notification {
        val channelId = "get_location_planned_service_channel"
        val channelName = "计划定位服务"
        val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_MIN)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(notificationChannel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在定位")
            .setContentText("服务正在运行，请稍候……")
            .setSmallIcon(R.drawable.ic_my_location)
            .setColor(ContextCompat.getColor(this, R.color.md_theme_primaryContainer))
            .build()
    }

    companion object {
        private var isRunning = false

        private val runningLock = ReentrantLock()

        /**
         * 运行定位服务
         * @param context 上下文
         * @return 错误信息
         */
        fun run(context: Context): String? {
            // 锁，然后启动服务
            runningLock.withLock {
                if (isRunning) {
                    println("服务正在运行，无法启动")
                    return "定位服务正在运行，无法启动"
                }
                isRunning = true
            }

            val serviceIntent = Intent(context, GetLocationPlannedForegroundService::class.java)

            context.startService(serviceIntent)

            return null
        }
    }
}
