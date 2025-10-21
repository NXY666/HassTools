package org.nxy.hasstools.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.nxy.hasstools.R
import org.nxy.hasstools.data.LocationDataStore
import org.nxy.hasstools.data.StepPushDataStore
import org.nxy.hasstools.data.UserDataStore
import org.nxy.hasstools.data.WifiGeofenceDataStore
import org.nxy.hasstools.objects.WifiGeofence
import org.nxy.hasstools.startStepWork
import org.nxy.hasstools.utils.LocationHandler

private object KeepAliveWorker {
    lateinit var serviceIntent: Intent
}

fun startKeepAliveService(context: Context) {
    KeepAliveWorker.serviceIntent = Intent(context, KeepAliveForegroundService::class.java)
    context.startForegroundService(KeepAliveWorker.serviceIntent)
}

fun stopKeepAliveService(context: Context) {
    context.stopService(KeepAliveWorker.serviceIntent)
}

class KeepAliveForegroundService : Service() {
    private val notificationId = 1

    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            println("notificationActionReceiver received: $intent")

            when (intent.action) {
                "LOCATE_ONCE" -> {
                    GetLocationManuallyForegroundService.run(applicationContext)
                }

                "STOP_SERVICE" -> {
                    stopSelf()
                }
            }
        }
    }

    private var connectivityAlarmPendingIntent: PendingIntent? = null

    private val scanResultsCallback = object : WifiManager.ScanResultsCallback() {
        private var availableGeofences: List<Triple<WifiGeofence, Set<String>, Set<String>>>? = null

        fun loadGeofences(): Boolean {
            val geofenceConfig = WifiGeofenceDataStore().readData()
            availableGeofences = if (geofenceConfig.enabled) {
                geofenceConfig.items.mapNotNull { geofence ->
                    if (!geofence.enabled) return@mapNotNull null
                    if (!geofence.functions.fastEnter) return@mapNotNull null

                    val ssidSet = geofence.ssidList.filter { it.ssid.isNotBlank() }.map { it.ssid }.toSet()
                    val bssidSet = geofence.bssidList.filter { it.bssid.isNotBlank() }.map { it.bssid }.toSet()
                    if (ssidSet.isEmpty() && bssidSet.isEmpty()) {
                        null
                    } else {
                        Triple(geofence, ssidSet, bssidSet)
                    }
                }.ifEmpty { null }
            } else {
                null
            }
            return availableGeofences != null
        }

        fun clearGeofences() {
            availableGeofences = null
        }

        override fun onScanResultsAvailable() {
            if (ActivityCompat.checkSelfPermission(
                    this@KeepAliveForegroundService,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val wifiManager = getSystemService(WifiManager::class.java)
            if (wifiManager == null) {
                println("WifiManager 不可用，无法处理扫描结果")
                return
            }

            val results = wifiManager.scanResults

            val geofences = availableGeofences ?: return

            val users = LocationWorker.userClients
            if (users.isEmpty()) return

            // 对每个 user 执行完整流程
            users.forEach { userClient ->
                val userId = userClient.userId
                val lastZoneId = userClient.locationId

                // 1. 对开启“快速进入”的地理围栏逐一比对（限定在该用户生效范围内）
                var matchedGeofence: WifiGeofence? = null
                for ((geofence, ssidSet, bssidSet) in geofences) {
                    // 若该条目限定用户且当前用户不在作用范围，则跳过
                    if (geofence.effectUserIds.isNotEmpty() && !geofence.effectUserIds.contains(userId)) continue

                    val anyMatch = results.any { result ->
                        val ssid = result.wifiSsid?.toString()?.removeSurrounding("\"") ?: ""
                        val bssid = result.BSSID ?: ""
                        ssidSet.contains(ssid) || bssidSet.contains(bssid)
                    }

                    println(
                        "地理围栏匹配：user=$userId zone=${geofence.zoneId} ssid=${
                            ssidSet.joinToString(
                                ","
                            )
                        } bssid=${
                            bssidSet.joinToString(
                                ","
                            )
                        } hasMatch=$anyMatch"
                    )

                    if (!anyMatch) continue

                    // 2. 与上次结果比对：若相同，则略过，继续比对下一条
                    if (geofence.zoneId == lastZoneId) {
                        println("地理围栏匹配：与上次相同，跳过上报")
                        continue
                    }

                    println("地理围栏匹配：${geofence.zoneId} != $lastZoneId，准备上报")

                    // 3. 若不同：结束比对，基于该 zone 位置上报
                    matchedGeofence = geofence
                    break
                }

                if (matchedGeofence == null) {
                    println("地理围栏匹配：没有命中任何新条目，跳过上报")
                    return@forEach
                }

                CoroutineScope(IO).launch updateLocation@{
                    try {
                        // 用 webhook API 获取 zones
                        val zones = try {
                            userClient.hassClient.getZones()
                        } catch (_: Exception) {
                            emptyList()
                        }

                        println("地理围栏匹配：zones=${zones.map { it.id }}")
                        val zone = zones.find { it.id == matchedGeofence.zoneId } ?: return@updateLocation
                        println(
                            "地理围栏匹配：zone=${zone.id} name=${zone.name} lat=${zone.latitude} lon=${zone.longitude}"
                        )

                        val location = Location("wifi_geofence").apply {
                            latitude = zone.latitude
                            longitude = zone.longitude
                            accuracy = zone.radius.toFloat()
                        }

                        LocationHandler.updateLocation(location)

                        val locationId = zone.id
                        val locationName = if (locationId == "zone.home") "home" else zone.name

                        userClient.locationId = locationId
                        userClient.locationName = locationName

                        LocationHandler.postLocation(userClient.hassClient, location, locationName)
                        println(
                            "地理围栏上报成功：user=$userId id=${locationId} name=$locationName lat=${location.latitude} lon=${location.longitude} acc=${location.accuracy}"
                        )
                    } catch (e: Exception) {
                        println("地理围栏上报失败：$e")
                    }
                }
            }
        }
    }

    private val networkCallback = object : NetworkCallback() {
        private var isFixed: Boolean? = false

        private val networks = mutableSetOf<Network>()

        @Synchronized
        fun checkFixed() {
            // 在network里面翻，有任何一个非计费wifi，则为true；反之false
            // network为空说明没有网络连接，则为null
            val isLastFixed = isFixed
            val isCurrFixed = if (networks.isNotEmpty()) {
                networks.map {
                    val connectivityManager = getSystemService(ConnectivityManager::class.java)
                    val activeNetwork = connectivityManager.getNetworkCapabilities(it)
                    activeNetwork?.let { net ->
                        val isWifi = net.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        val isMetered = !net.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                        isWifi && !isMetered
                    } ?: false
                }.any { it }
            } else {
                null
            }

            if (isLastFixed == isCurrFixed) {
                // 状态没有变化
                return
            }

            when (isCurrFixed) {
                true -> {
                    // 连接了固定wifi网络，马上定位
                    setAlarm(1 * 1000)
                }

                false -> {
                    // 连接了非固定wifi网络或移动网络，60秒后定位（等人走远一点）
                    setAlarm(60 * 1000)
                }

                null -> {
                    cancelAlarm()
                }
            }

            isFixed = isCurrFixed
            println("checkFixed：$isFixed")
        }

        override fun onAvailable(network: Network) {
            println("网络已连接")

            if (!networks.contains(network)) {
                networks.add(network)
                checkFixed()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)

            networks.remove(network)
            checkFixed()
        }

        private fun setAlarm(delay: Long) {
            cancelAlarm()

            connectivityAlarmPendingIntent = LocationAlarmScheduler.scheduleExactAlarm(
                applicationContext,
                LocationAlarmReceiver.GET_LOCATION_MANUALLY,
                SystemClock.elapsedRealtime() + delay
            )
            println("设置网络闹钟：$delay")
        }

        private fun cancelAlarm() {
            connectivityAlarmPendingIntent?.let {
                LocationAlarmScheduler.cancelAlarm(applicationContext, it)
            }
            connectivityAlarmPendingIntent = null
        }
    }

    override fun onCreate() {
        super.onCreate()

        println("KeepAliveForegroundService created")

        // 创建前台通知
        val notification = createNotification()

        startForeground(notificationId, notification)

        val notificationActionFilter = IntentFilter().apply {
            addAction("LOCATE_ONCE")
            addAction("STOP_SERVICE")
        }
        registerReceiver(
            notificationActionReceiver,
            notificationActionFilter,
            RECEIVER_NOT_EXPORTED
        )

        val locationConfig = LocationDataStore().readData()

        if (locationConfig.networkTriggerEnabled) {
            // 创建监听wifi和流量的监听器
            val connectivityManager = getSystemService(ConnectivityManager::class.java)
            connectivityManager?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build(),
                networkCallback
            ) ?: println("ConnectivityManager 不可用，无法注册网络回调")
        }

        if (scanResultsCallback.loadGeofences()) {
            val wifiManager = getSystemService(WifiManager::class.java)
            wifiManager?.let {
                val executor = ContextCompat.getMainExecutor(this)
                it.registerScanResultsCallback(executor, scanResultsCallback)
            } ?: run {
                scanResultsCallback.clearGeofences()
                println("WifiManager 不可用，无法注册 Wi-Fi 扫描回调")
            }
        } else {
            println("没有开启快速进入的地理围栏，未注册 Wi-Fi 扫描回调")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("KeepAliveForegroundService onStartCommand: $intent")

        val userConfig = UserDataStore().readData()

        // intent为空时，说明是服务重启
        if (intent == null) {
            print("KeepAliveForegroundService 重启")

            startLocationWork(applicationContext, userConfig)

            val stepPushConfig = StepPushDataStore().readData()

            if (stepPushConfig.enabled) {
                startStepWork(applicationContext)
            }
        }

        // 广播启动
        LocationWorkerStatus.set(this, LocationWorkerStatus.RUNNING)

        if (!userConfig.hasEnabledUser()) {
            stopLocationWork(applicationContext, "没有已启用的用户。")

            return START_NOT_STICKY // 服务停止后不再重启
        }

        return START_STICKY // 服务停止后重启
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(notificationActionReceiver)

        // 停止网络变化监听器
        try {
            val connectivityManager = getSystemService(ConnectivityManager::class.java)
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }

        // 停止网络扫描结果监听器
        try {
            val wifiManager = getSystemService(WifiManager::class.java)
            wifiManager?.unregisterScanResultsCallback(scanResultsCallback)
        } catch (_: Exception) {
        }
        scanResultsCallback.clearGeofences()

        // 广播停止
        LocationWorkerStatus.set(this, LocationWorkerStatus.STOPPED)
    }

    // 创建前台通知
    private fun createNotification(
        text: String = "", subtext: String = ""
    ): Notification {
        val channelId = "keep_alive_service_channel"
        val channelName = "保活服务"
        val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_MIN)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(notificationChannel)

        return NotificationCompat.Builder(this, channelId).setSubText(subtext)
            .setContentText(text)
            .addAction(
                R.drawable.ic_launcher_foreground, "定位", PendingIntent.getBroadcast(
                    this, 0, Intent("LOCATE_ONCE").apply {
                        `package` = packageName
                    }, PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                R.drawable.ic_launcher_foreground, "停止", PendingIntent.getBroadcast(
                    this, 0, Intent("STOP_SERVICE").apply {
                        `package` = packageName
                    }, PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setSmallIcon(R.drawable.ic_location_on)
            .setColor(ContextCompat.getColor(this, R.color.md_theme_primaryContainer))
            .build()
    }
}
